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
import org.cojen.motto.internal.model.BaseCallSignature;
import org.cojen.motto.internal.model.BaseTupleType;
import org.cojen.motto.internal.model.BaseType;
import org.cojen.motto.internal.model.BaseVoidType;
import org.cojen.motto.internal.model.NewClass;

import static org.cojen.motto.internal.model.BaseCallSignature.BaseSegment;

import static org.cojen.motto.internal.model.Modifiers.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class MethodDefinitionStatement extends FunctionDefinitionStatement {
    public final List<DefinitionSegment> segments;

    private int mModifierBits;

    // These are assigned when addToClass is called.
    private NewClass mClass;
    private BaseCallableItem mItem;

    /**
     * @param modifiers required; might be empty
     * @param name required base name of method
     * @param clauses required; might be empty
     * @param code optional
     * @param returnType can be null for void
     * @param segments required; can be empty
     */
    MethodDefinitionStatement(List<Token.Identifier> modifiers, Token.Identifier name,
                              List<Clause> clauses, TupleStatement code, VarType returnType,
                              TupleVarType paramType, List<DefinitionSegment> segments)
    {
        super(modifiers, name, clauses, code, returnType, paramType);
        this.segments = segments;
        mModifierBits = -1; // unresolved
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token start() {
        return modifiers.isEmpty() ? returnType.start() : modifiers.getFirst();
    }

    @Override
    public Token end() {
        if (code != null) {
            return code.end();
        }
        if (!segments.isEmpty()) {
            return segments.getLast().end();
        }
        if (!clauses.isEmpty()) {
            return clauses.getLast().end();
        }
        return paramType.end();
    }

    @Override
    public void prepareClass(CompilationEnv env, NewClass clazz) {
        clazz.prepareMethodForImport(modifierBits(env), name.text);
    }

    @Override
    public BaseCallableItem addToClass(CompilationEnv env, NewClass clazz) {
        if (mClass != null) {
            return mItem;
        }

        mClass = clazz;

        BaseType outputType;
        if (returnType == null) {
            outputType = BaseVoidType.THE;
        } else {
            outputType = returnType.tryResolve(env, clazz);
            if (outputType == null) {
                // An error should have been reported already.
                return null;
            }
        }

        int modifierBits = modifierBits(env);

        NewClass insertThis = (modifierBits & STATIC) == 0 ? clazz : null;

        BaseTupleType inputType = paramType.tryResolve(env, clazz, insertThis);

        if (inputType == null) {
            // An error should have been reported already.
            return null;
        }

        BaseSegment[] segments = null;

        if (!this.segments.isEmpty()) {
            segments = new BaseSegment[this.segments.size()];

            int i = 0;
            for (DefinitionSegment dseg : this.segments) {
                String name = dseg.name == null ? "" : dseg.name.text;

                BaseTupleType segParamType = dseg.paramType.tryResolve(env, clazz, null);

                if (segParamType == null) {
                    // An error should have been reported already.
                    return null;
                }

                var seg = BaseSegment.from
                    (dseg.repetition, name, segParamType, dseg.paramType.isEvaluated());

                segments[i++] = seg;
            }

            if (i != segments.length) {
                throw new AssertionError();
            }
        }

        if ((modifierBits & (MACRO | STATIC)) == MACRO) {
            // FIXME: drop this restriction
            env.error(this, "macro method must be static");
            return null;
        }

        BaseCallSignature sig = BaseCallSignature.from
            (outputType, name.text, inputType, paramType.isEvaluated(), segments);

        // FIXME: Clauses.

        if ((mItem = clazz.tryAddMethod(modifierBits, sig)) == null) {
            env.error(this, "duplicate method definition");
        }

        return mItem;
    }

    private int modifierBits(CompilationEnv env) {
        int modifierBits = mModifierBits;

        if (modifierBits == -1) {
            mModifierBits = modifierBits = Element.resolveModifiers
                (env, PUBLIC | PROTECTED | INTERNAL | STATIC | FINAL
                 | SYNCHRONIZED | NATIVE | ABSTRACT | MACRO,
                 modifiers);
        }

        return modifierBits;
    }
}
