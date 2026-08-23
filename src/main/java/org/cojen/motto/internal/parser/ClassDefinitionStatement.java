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

package org.cojen.motto.internal.parser;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.cojen.motto.internal.compiler.CompilationEnv;

import org.cojen.motto.internal.model.BaseClassTypeItem;
import org.cojen.motto.internal.model.BasePath;
import org.cojen.motto.internal.model.LoadedClass;
import org.cojen.motto.internal.model.NewClass;

import static org.cojen.motto.internal.model.Modifiers.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class ClassDefinitionStatement extends DefinitionStatement {
    public final Token.Identifier type;

    /** Is assigned when createNewClass is called, unless it's a duplicate. */
    public NewClass clazz;

    /**
     * @param modifiers required; might be empty
     * @param type required (class or interface)
     * @param name required
     * @param clauses required; might be empty
     * @param code required; can pass null if the definition is broken
     */
    ClassDefinitionStatement(List<Token.Identifier> modifiers, Token.Identifier type,
                             Token.Identifier name, List<Clause> clauses, CodeScopeStatement code)
    {
        super(modifiers, name, clauses, code);
        this.type = type;
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token start() {
        return modifiers.isEmpty() ? type : modifiers.getFirst();
    }

    @Override
    public Token end() {
        if (code != null) {
            return code.end();
        }
        if (!clauses.isEmpty()) {
            return clauses.getLast().end();
        }
        return name;
    }

    @Override
    public void prepareClass(CompilationEnv env, NewClass clazz) {
        // FIXME: Must only add all path-accessible inner classes. Path-accessible means that
        // all path elements refer to classes/interfaces, and access isn't private. Inner
        // classes defined in other kinds of scopes aren't accessible.
        env.error(this, "inner classes not supported yet");
    }

    /**
     * Creates a top-level NewClass, prepares it, and registers it such that it can be seen by
     * other classes. If there are any errors, they're passed to CompilationEnv.
     *
     * @return null if defining a duplicate class (an error woild be reported too)
     */
    public NewClass prepareNewClass(CompilationEnv env, BasePath packagePath) {
        int modifierBits = Element.resolveModifiers
            (env, PUBLIC | PROTECTED | INTERNAL | STATIC | FINAL | ABSTRACT, modifiers);

        switch (type.text) {
            case "class" -> {
                modifierBits |= CLASS;
            }
            case "interface" -> {
                modifierBits |= INTERFACE;
            }
        }

        var clazz = new NewClass
            (env, modifierBits, packagePath, BasePath.from(name.text), env.sourceFile());

        for (Statement st : code.items) {
            st.prepareClass(env, clazz);
        }

        if (env.tryRegister(clazz)) {
            this.clazz = clazz;
        } else {
            env.error(this, "duplicate class definition");
        }

        return clazz;
    }

    /**
     * After all imports have been resolved, call this method to:
     *
     * - Resolve the supertypes, but cycle detection isn't performed yet. The supertypes might
     *   not have reached the available phase yet.
     *
     * - Resolve field types. FieldItems will now exist.
     *
     * - Resolve method return types and parameter types. CallableItems for methods
     *   will now exist, but they won't have any code.
     *
     * - Resolve constructor parameter types. CallableItems for constructors will now
     *   exist, but they won't have any code.
     *
     * - Resolve all path-accessible inner classes. Inner classes which aren't defined in a
     *   method or constructor will now exist.
     *
     * - Mark the NewClassItem as available.
     *
     * - Perform inheritance cycle detection.
     */
    public void resolveClass(CompilationEnv env) {
        if (clazz == null) {
            // Assuming that prepareNewClass was called, the definition was rejected.
            return;
        }

        try {
            BaseClassTypeItem superClass = null;
            Set<BaseClassTypeItem> interfaces = null;

            for (Clause clause : clauses) {
                String kind = clause.kind.text;
                if ("extends".equals(kind)) {
                    for (List<Token.Identifier> item : clause.items) {
                        BaseClassTypeItem clauseClass =
                            SimpleVarType.trySelectClass(env, clazz, item);

                        if (clauseClass != null) {
                            if ((clauseClass.modifierBits() & CLASS) == 0) {
                                env.error(item, "cannot extend a non-class");
                            } else if ((clauseClass.modifierBits() & FINAL) != 0) {
                                env.error(item, "cannot extend a final class");
                            }
                            if (superClass == null) {
                                superClass = clauseClass;
                            } else {
                                env.error(item, "cannot extend more than one class");
                            }
                        }
                    }
                } else if ("implements".equals(kind)) {
                    for (List<Token.Identifier> item : clause.items) {
                        BaseClassTypeItem clauseClass =
                            SimpleVarType.trySelectClass(env, clazz, item);

                        if (clauseClass != null) {
                            if ((clauseClass.modifierBits() & INTERFACE) == 0) {
                                env.error(item, "cannot implement a non-interface");
                            } else {
                                if (interfaces == null) {
                                    interfaces = LinkedHashSet
                                        .newLinkedHashSet(clause.items.size());
                                }
                                interfaces.add(clauseClass);
                            }
                        }
                    }
                } else {
                    env.error(clause, "unsupported clause");
                }
            }

            if (superClass == null) {
                superClass = LoadedClass.classFrom(Object.class);
            }

            clazz.setSuperTypes(superClass, interfaces);

            for (Statement st : code.items) {
                st.addToClass(env, clazz);
            }
        } finally {
            clazz.available();
        }

        clazz.checkForInheritanceCycle();
    }
}
