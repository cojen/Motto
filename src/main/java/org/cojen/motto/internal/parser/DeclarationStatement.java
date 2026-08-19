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

import org.cojen.motto.internal.compiler.CompilationEnv;

import org.cojen.motto.internal.model.BaseFieldItem;
import org.cojen.motto.internal.model.BaseType;
import org.cojen.motto.internal.model.NewClass;

import static org.cojen.motto.internal.model.Modifiers.*;

/**
 * Example: `int a` or `int a = 0`
 *
 * @author Brian S. O'Neill
 */
public final class DeclarationStatement implements Statement, NamedVarType {
    public final List<Token.Identifier> modifiers;
    public final VarType type;
    public final Token.Identifier name;
    public final Statement source;

    private int mModifierBits;

    // These are assigned when addToClass is called.
    private NewClass mClass;
    private BaseFieldItem mItem;

    /**
     * @param modifiers required; might be empty
     * @param type required
     * @param name name of the variable or field being declared
     * @param source optional source for immediate assignment
     */
    DeclarationStatement(List<Token.Identifier> modifiers,
                         VarType type, Token.Identifier name, Statement source)
    {
        this.modifiers = modifiers;
        this.type = type;
        this.name = name;
        this.source = source;
        mModifierBits = -1; // unresolved
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token start() {
        if (!modifiers.isEmpty()) {
            return modifiers.getFirst();
        }
        return type.start();
    }

    @Override
    public Token end() {
        if (source != null) {
            return source.end();
        }
        return name;
    }

    @Override
    public NamedVarType asVarType(Parser p) {
        if (source != null) {
            p.error(this, "default value not allowed");
        }
        return this;
    }

    @Override // NamedVarType
    public List<Token.Identifier> modifiers() {
        return modifiers;
    }

    @Override // NamedVarType
    public VarType type() {
        return type;
    }

    @Override // NamedVarType
    public Token.Identifier name() {
        return name;
    }

    @Override
    public void prepareClass(CompilationEnv env, NewClass clazz) {
        // Assume the caller knows that this declaration is a field and not a local variable.
        clazz.prepareFieldForImport(modifierBits(env), name.text);
    }

    @Override
    public BaseFieldItem addToClass(CompilationEnv env, NewClass clazz) {
        if (mClass != null) {
            return mItem;
        }

        mClass = clazz;

        BaseType type = this.type.tryResolve(env, clazz);

        if (type == null) {
            // An error should have been reported.
            return null;
        }

        int modifierBits = modifierBits(env);

        if ((mItem = clazz.tryAddField(modifierBits, type, name.text)) == null) {
            env.error(this, "duplicate declaration");
        }

        return mItem;
    }

    private int modifierBits(CompilationEnv env) {
        int modifierBits = mModifierBits;

        if (modifierBits == -1) {
            mModifierBits = modifierBits = Element.resolveModifiers
                (env, PUBLIC | PROTECTED | INTERNAL | STATIC | FINAL | VOLATILE | TRANSIENT,
                 modifiers);
        }

        return modifierBits;
    }
}
