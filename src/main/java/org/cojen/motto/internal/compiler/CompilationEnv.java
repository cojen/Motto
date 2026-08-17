/*
 *  Copyright 2026 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.motto.internal.compiler;

import java.io.File;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.cojen.motto.internal.model.BaseClassTypeItem;
import org.cojen.motto.internal.model.BasePath;
import org.cojen.motto.internal.model.NewClass;

import org.cojen.motto.internal.parser.CompilationUnit;
import org.cojen.motto.internal.parser.Element;
import org.cojen.motto.internal.parser.ImportDirective;
import org.cojen.motto.internal.parser.Token;

import static org.cojen.motto.internal.model.Modifiers.*;

/**
 * A CompilationEnv is associated with a single source file.
 *
 * @author Brian S. O'Neill
 */
public final class CompilationEnv {
    private final Compiler mCompiler;
    private final File mSourceFile;

    private volatile int mNumErrors;

    // The following fields are set by resolveImports.

    private BasePath mPackagePath;

    // Maps names to NominalClassItem for class import, or MemberImport for field/method import.
    private Map<String, Object> mExplicitImports;

    // Maps package paths to the same Path for package import, or NominalClassItem for class
    // import.
    private Map<BasePath, Object> mWildcardImports;

    private record MemberImport(BaseClassTypeItem clazz) {}

    // The following fields are set by findImportedClass.

    private Map<String, BaseClassTypeItem> mFoundImports, mFoundImportsByMember;

    /**
     * @param sourceFile optional
     */
    public CompilationEnv(Compiler compiler, File sourceFile) {
        mCompiler = Objects.requireNonNull(compiler);
        mSourceFile = sourceFile;
    }

    public Compiler compiler() {
        return mCompiler;
    }

    public File sourceFile() {
        return mSourceFile;
    }

    /**
     * Returns the number of errors reported against this CompilationEnv instance.
     */
    public int numErrors() {
        return mNumErrors;
    }

    public void error(Element element, String message) {
        error(new CompileError(element, message));
    }

    public synchronized void error(CompileError error) {
        mNumErrors++;
        mCompiler.error(mSourceFile, error);
    }

    public synchronized void uncaught(Throwable ex) {
        mNumErrors++;
        mCompiler.uncaught(mSourceFile, ex);
    }

    /**
     * Register a prepared NewClass which is being compiled. All classes being compiled must be
     * prepared and registered before imports are resolved.
     *
     * @return false if a matching NewClass already exists
     */
    public boolean tryRegister(NewClass clazz) {
        return mCompiler.tryRegister(clazz);
    }

    /**
     * This method be called before imports can be searched. It verifies that all imports
     * exist and reports errors as necessary.
     */
    @SuppressWarnings("unchecked")
    void resolveImports(CompilationUnit cu) {
        mPackagePath = cu.packagePath();

        var explicitImports = new HashMap<String, Object>();
        var wildcardImports = new LinkedHashMap<BasePath, Object>();

        boolean hasNulls = false;

        for (ImportDirective imp : cu.imports) {
            Map imports;
            Object key;

            if (imp.wildcard == null) {
                imports = explicitImports;
                key = imp.name.getLast().text;
            } else {
                imports = wildcardImports;
                key = imp.path();
            }

            if (imports.containsKey(key)) {
                error(imp, "duplicate import");
                continue;
            }

            Object result = tryResolveImport(imp, imports == wildcardImports, false);

            if (result == null) {
                // Try again to report any errors. This isn't done the first time because
                // spurious errors might be reported. There's a slight chance that trying again
                // will actually work, if the classpath is being concurrently modified. Errors
                // might still be reported, however.
                result = tryResolveImport(imp, imports == wildcardImports, true);
                hasNulls |= result == null;
            }

            imports.put(key, result);
        }

        if (hasNulls) {
            removeNullValues(explicitImports);
            removeNullValues(wildcardImports);
        }

        mExplicitImports = explicitImports;
        mWildcardImports = wildcardImports;
    }

    private static void removeNullValues(Map<?, Object> map) {
        Iterator<Object> it = map.values().iterator();
        while (it.hasNext()) {
            Object value = it.next();
            if (value == null) {
                it.remove();
            }
        }
    }

    /**
     * For an explicit (non-wildcard) import, returns BaseClassTypeItem or MemberImport. For a
     * wildcard import, returns Path or BaseClassTypeItem.
     */
    private Object tryResolveImport(ImportDirective imp, boolean wildcard, boolean reportErrors) {
        // Search ordering rules: The longest package name list wins, followed by the longest
        // class name list. If an import would also match against something else, like a
        // shorter package name list, then the source code might need to use a shorter import
        // in order to access items with conflicting paths.

        final BasePath fullPath = imp.path();
        BasePath packagePath = fullPath;

        if (wildcard && mCompiler.packageExists(packagePath)) {
            // FIXME: Remember to do module access checks against the package. If not
            // accessible, then reject it and move on. Report an error if requested.
            return packagePath;
        }

        int classIndex = fullPath.size();
        final int lastIndex = classIndex - 1;
        final String name = fullPath.get(lastIndex);
        final int numErrors = mNumErrors;

        permute: do {
            packagePath = packagePath.trimLast();
            classIndex--;

            if (mCompiler.packageExists(packagePath)) {
                // FIXME: Remember to do module access checks against the package. If not
                // accessible, then reject it and move on. Report an error if requested.
            }

            BaseClassTypeItem clazz = mCompiler.findClass(packagePath, fullPath.get(classIndex));

            if (clazz == null) {
                continue permute;
            }

            if (!isAccessible(clazz)) {
                reportInaccessible(imp, clazz, reportErrors);
                continue permute;
            }

            if (classIndex == lastIndex) {
                return clazz;
            }

            for (int ix = classIndex + 1; ix < lastIndex; ix++) {
                clazz = clazz.findInnerClass(fullPath.get(ix));
                if (clazz == null) {
                    continue permute;
                }
                if (!isAccessible(clazz)) {
                    reportInaccessible(imp, clazz, reportErrors);
                    continue permute;
                }
            }

            BaseClassTypeItem inner = clazz.findInnerClass(name);

            if (inner != null) {
                if (!isAccessible(inner)) {
                    reportInaccessible(imp, inner, reportErrors);
                } else {
                    if (inner.isStatic()) {
                        return inner;
                    }
                    if (reportErrors) {
                        error(imp, "cannot import a non-static inner class");
                    }
                }
            }

            if (wildcard) {
                continue permute;
            }

            int modifierBits = clazz.findFieldForImport(name);

            if (modifierBits != -1) {
                if (!isAccessible(clazz, modifierBits)) {
                    reportInaccessible(imp, "field", reportErrors);
                } else {
                    return new MemberImport(clazz);
                }
            }

            modifierBits = clazz.findMethodForImport(name);

            if (modifierBits != -1) {
                if (!isAccessible(clazz, modifierBits)) {
                    reportInaccessible(imp, "method", reportErrors);
                } else {
                    return new MemberImport(clazz);
                }
            }
        } while (!packagePath.isEmpty());

        if (reportErrors && numErrors == mNumErrors) {
            error(imp, "unable to resolve import");
        }

        return null;
    }

    /**
     * Returns true if the given class is accessible from the package associated with this
     * CompilationEnv.
     */
    public boolean isAccessible(BaseClassTypeItem clazz) {
        return isAccessible(clazz, clazz.modifierBits());
    }

    private boolean isAccessible(BaseClassTypeItem clazz, int modifierBits) {
        if ((modifierBits & PUBLIC) != 0) {
            return true;
        }
        if ((modifierBits & (PROTECTED | INTERNAL)) != 0) {
            return Objects.equals(mPackagePath, clazz.packagePath());
        }
        return clazz.env() == this;
    }

    private void reportInaccessible(ImportDirective imp, BaseClassTypeItem clazz,
                                    boolean reportErrors)
    {
        String type = (clazz.modifierBits() & INTERFACE) != 0 ? "interface" : "class";
        reportInaccessible(imp, type, reportErrors);
    }

    private void reportInaccessible(ImportDirective imp, String type, boolean reportErrors) {
        if (reportErrors) {
            error(imp, type + " is inaccessible");
        }
    }

    /**
     * Tries to find an outer class by its fully qualified name. Searches the class path,
     * module path, and any registered NewClass instances which are currently being compiled.
     *
     * <p>If the class isn't accessible from the package associated with this CompilationEnv,
     * null is returned.
     *
     * @return null if not found or not accessible
     */
    public BaseClassTypeItem findClass(BasePath fullName) {
        BaseClassTypeItem clazz = mCompiler.findClass(fullName);
        return clazz == null ? null : (isAccessible(clazz) ? clazz : null);
    }

    /**
     * Tries to find an outer class by its package and name. Searches the class path, module
     * path, and any registered NewClass instances which are currently being compiled.
     *
     * <p>If the class isn't accessible from the package associated with this CompilationEnv,
     * null is returned.
     *
     * @param packagePath required, but can be empty
     * @param className required (must not be an inner class name)
     * @return null if not found or not accessible
     */
    public BaseClassTypeItem findClass(BasePath packagePath, String className) {
        BaseClassTypeItem clazz = mCompiler.findClass(packagePath, className);
        return clazz == null ? null : (isAccessible(clazz) ? clazz : null);
    }

    /**
     * Tries to find an inner class by its package and class name. Searches the class path,
     * module path, and any registered NewClass instances which are currently being compiled.
     *
     * <p>If the class isn't accessible from the package associated with this CompilationEnv,
     * null is returned.
     *
     * @param outer required
     * @param className loadable inner class name, not including the package name (typically
     * has '$' characters)
     * @param name simple inner class name
     * @return null if not found or not accessible
     */
    public BaseClassTypeItem findClass(BaseClassTypeItem outer, String className, String name) {
        BaseClassTypeItem clazz = mCompiler.findClass(outer, className, name);
        return clazz == null ? null : (isAccessible(clazz) ? clazz : null);
    }

    /**
     * Follows the given path until a BaseClassTypeItem is found by its fully qualified name.
     * Call fullPathSize() to obtain the number of path elements consumed, which might not be
     * the full path size which was given. If the class isn't accessible from the package
     * associated with this CompilationEnv, null is returned.
     *
     * @return null if nothing matched
     */
    public BaseClassTypeItem matchClassItem(BasePath path) {
        BaseClassTypeItem item = mCompiler.matchClassItem(path);
        return item == null ? null : (isAccessible(item) ? item : null);
    }

    /**
     * Tries to find an imported class by the given simple name. If found, the class is a
     * regular class or a static inner class. The class is guaranteed to be accessible from the
     * package associated with this CompilationEnv.
     *
     * @param name simple class name
     */
    public BaseClassTypeItem findImportedClass(Token.Identifier name) {
        return findImportedClass(name, false);
    }

    /**
     * Tries to find an imported class by the given field or method name. If found, the class
     * is a regular class or a static inner class which contains an accessible field or method
     * which matches the given name. The class is guaranteed to be accessible from the package
     * associated with this CompilationEnv.
     *
     * <p>The caller must perform an additional find against the class to obtain the actual
     * field or method. If found, the actual field or method might not be accessible, and so
     * the caller must check this.
     *
     * @param name field or method name
     */
    public BaseClassTypeItem findImportedClassByMember(Token.Identifier name) {
        return findImportedClass(name, true);
    }

    private BaseClassTypeItem findImportedClass(Token.Identifier name, boolean byMember) {
        Map<String, BaseClassTypeItem> cache = byMember ? mFoundImportsByMember : mFoundImports;

        String text = name.text;
        BaseClassTypeItem clazz;

        if (cache == null || ((clazz = cache.get(text)) == null && !cache.containsKey(text))) {
            clazz = doFindImportedClass(name, byMember);

            if (cache == null) {
                cache = new HashMap<>();
                if (byMember) {
                    mFoundImportsByMember = cache;
                } else {
                    mFoundImports = cache;
                }
            }

            cache.put(text, clazz);
        }

        return clazz;
    }

    private BaseClassTypeItem doFindImportedClass(Token.Identifier name, boolean byMember) {
        // Note: Explicit imports always win over wildcard imports, regardless of the order in
        // which they appear in the source file. Wildcard imports are stored in a LinkedHashMap
        // to improve error reporting.

        String text = name.text;
        Object result = mExplicitImports.get(text);

        if (byMember) {
            if (result instanceof MemberImport mi) {
                return mi.clazz();
            }
        } else {
            if (result instanceof BaseClassTypeItem clazz) {
                return clazz;
            }
        }

        Object matchedImport = null;
        BaseClassTypeItem matchedClass = null;
        StringBuilder error = null;

        for (Object imp : mWildcardImports.values()) {
            BaseClassTypeItem clazz;

            if (imp instanceof BasePath packagePath) {
                clazz = mCompiler.findClass(packagePath, text);
                if (clazz == null) {
                    continue;
                }
            } else {
                clazz = (BaseClassTypeItem) imp;
                if (byMember) {
                    if (clazz.findFieldForImport(text) == -1 &&
                        clazz.findMethodForImport(text) == -1)
                    {
                        continue;
                    }
                    throw null;
                } else if (!clazz.namePath().getLast().equals(text)) {
                    continue;
                }
            }

            if (matchedClass != null) {
                if (error == null) {
                    error = new StringBuilder();
                    error.append("ambiguous wildcard import: ");
                    appendImport(error, matchedImport);
                    error.append(".*");
                }
                error.append(", ");
                appendImport(error, imp);
                error.append(".*");
                continue;
            }

            matchedImport = imp;
            matchedClass = clazz;
        }

        if (matchedClass != null) {
            if (error != null) {
                error(name, error.toString());
            }
            return matchedClass;
        }

        if (!byMember) {
            // Try looking for the class in the local package.
            matchedClass = mCompiler.findClass(mPackagePath, text);
            if (matchedClass == null) {
                // Check implicit wildcard imports as a last resort.
                return mCompiler.findClass(BasePath.JAVA_LANG, text);
            }
        }

        return matchedClass;
    }

    private static void appendImport(StringBuilder b, Object imp) {
        if (imp instanceof BasePath path) {
            path.appendTo(b, '.');
        } else if (imp instanceof BaseClassTypeItem clazz) {
            clazz.appendDisplayNameTo(b);
        } else {
            b.append(imp); // not expected
        }
    }
}
