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

import java.io.File;

import java.util.List;

import org.cojen.motto.internal.model.BasePath;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class CompilationUnit implements Element {
    public final List<Token.Identifier> packageName;
    public final List<ImportDirective> imports;
    public final List<DefinitionStatement> definitions;

    private BasePath mPackagePath;

    /**
     * @param packageName required; can be empty
     * @param imports required; can be empty
     * @param definitions required; can be empty
     */
    CompilationUnit(List<Token.Identifier> packageName,
                    List<ImportDirective> imports,
                    List<DefinitionStatement> definitions)
    {
        this.packageName = packageName;
        this.imports = imports;
        this.definitions = definitions;
    }

    @Override
    public Token start() {
        if (!packageName.isEmpty()) {
            return packageName.getFirst();
        }
        if (!imports.isEmpty()) {
            return imports.getFirst().start();
        }
        if (!definitions.isEmpty()) {
            return definitions.getFirst().start();
        }
        return null;
    }

    @Override
    public Token end() {
        if (!definitions.isEmpty()) {
            return definitions.getLast().end();
        }
        if (!imports.isEmpty()) {
            return imports.getLast().end();
        }
        if (!packageName.isEmpty()) {
            return packageName.getLast();
        }
        return null;
    }

    public BasePath packagePath() {
        BasePath path = mPackagePath;
        if (path == null) {
            mPackagePath = path = BasePath.from(packageName);
        }
        return path;
    }
}

