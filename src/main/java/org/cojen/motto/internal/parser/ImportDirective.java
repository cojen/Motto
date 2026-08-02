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

import java.util.List;

import org.cojen.motto.internal.model.BasePath;

/**
 * Example: `import a.B`
 *
 * @author Brian S. O'Neill
 */
public final class ImportDirective implements Element {
    public final List<Token.Identifier> modifiers;
    public final List<Token.Identifier> name;
    public final Token wildcard;

    private BasePath mPath;

    /**
     * @param modifiers required; might be empty
     * @param name required
     * @param wildcard optional
     */
    ImportDirective(List<Token.Identifier> modifiers, List<Token.Identifier> name, Token wildcard) {
        this.modifiers = modifiers;
        this.name = name;
        this.wildcard = wildcard;
    }

    @Override
    public Token start() {
        return name.getFirst();
    }

    @Override
    public Token end() {
        return wildcard == null ? name.getLast() : wildcard;
    }

    /**
     * Returns the name as a Path.
     */
    public BasePath path() {
        BasePath path = mPath;
        if (path == null) {
            mPath = path = BasePath.from(name);
        }
        return path;
    }
}
