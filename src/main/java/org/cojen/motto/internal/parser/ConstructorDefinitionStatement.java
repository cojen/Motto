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

import org.cojen.motto.internal.model.BaseCallableItem;
import org.cojen.motto.internal.model.BaseTupleType;
import org.cojen.motto.internal.model.NewClass;

import static org.cojen.motto.internal.model.Modifiers.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class ConstructorDefinitionStatement extends FunctionDefinitionStatement {
    // These are assigned when addToClass is called.
    private NewClass mClass;
    private BaseCallableItem mItem;

    /**
     * @param modifiers required; might be empty
     * @param name required name of constructor
     * @param clauses required; might be empty
     * @param params items should only be DeclarationStatements (to be verifier later)
     * @param code required, unless the definition is broken
     */
    ConstructorDefinitionStatement(List<Token.Identifier> modifiers, Token.Identifier name,
                                   List<Clause> clauses, TupleStatement code,
                                   TupleVarType paramType)
    {
        super(modifiers, name, clauses, code, null, paramType);
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token start() {
        return modifiers.isEmpty() ? name : modifiers.getFirst();
    }

    @Override
    public Token end() {
        return code.end();
    }

    @Override
    public BaseCallableItem addToClass(CompilationEnv env, NewClass clazz) {
        if (mClass != null) {
            return mItem;
        }

        mClass = clazz;

        if (code == null) {
            // Definition is broken, so skip it. An error should have been reported already.
            return null;
        }

        int modifierBits = Element.resolveModifiers
            (env, PUBLIC | PROTECTED | INTERNAL | SYNCHRONIZED, modifiers);

        BaseTupleType inputType = paramType.tryResolve(env, clazz, clazz);

        if (inputType == null) {
            // An error should have been reported already.
            return null;
        }

        boolean evaluated = paramType.isEvaluated();

        // FIXME: Clauses.

        if ((mItem = clazz.tryAddConstructor(modifierBits, inputType, evaluated)) == null) {
            env.error(this, "duplicate constructor definition");
        }

        return mItem;
    }
}
