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

import org.cojen.motto.internal.model.BaseBinding;
import org.cojen.motto.internal.model.BaseBlock;
import org.cojen.motto.internal.model.BaseCallableItem;
import org.cojen.motto.internal.model.BaseItem;

import org.cojen.motto.internal.parser.AsStatement;
import org.cojen.motto.internal.parser.ClassDefinitionStatement;
import org.cojen.motto.internal.parser.ConstructorDefinitionStatement;
import org.cojen.motto.internal.parser.CoordinateLoadStatement;
import org.cojen.motto.internal.parser.DeclarationStatement;
import org.cojen.motto.internal.parser.Element;
import org.cojen.motto.internal.parser.EmptyStatement;
import org.cojen.motto.internal.parser.FieldLoadStatement;
import org.cojen.motto.internal.parser.FunctionDefinitionStatement;
import org.cojen.motto.internal.parser.InfixStatement;
import org.cojen.motto.internal.parser.IsStatement;
import org.cojen.motto.internal.parser.JumpStatement;
import org.cojen.motto.internal.parser.LabeledStatement;
import org.cojen.motto.internal.parser.LambdaStatement;
import org.cojen.motto.internal.parser.LiteralStatement;
import org.cojen.motto.internal.parser.LoadStatement;
import org.cojen.motto.internal.parser.MethodCallStatement;
import org.cojen.motto.internal.parser.MethodDefinitionStatement;
import org.cojen.motto.internal.parser.NewArrayStatement;
import org.cojen.motto.internal.parser.NewClassDefinitionStatement;
import org.cojen.motto.internal.parser.NewStatement;
import org.cojen.motto.internal.parser.ParseVisitor;
import org.cojen.motto.internal.parser.PostfixStatement;
import org.cojen.motto.internal.parser.PrefixStatement;
import org.cojen.motto.internal.parser.ReturnStatement;
import org.cojen.motto.internal.parser.SequenceStatement;
import org.cojen.motto.internal.parser.Statement;
import org.cojen.motto.internal.parser.StoreStatement;
import org.cojen.motto.internal.parser.ThrowStatement;
import org.cojen.motto.internal.parser.TupleStatement;
import org.cojen.motto.internal.parser.YieldStatement;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class ModelGenerator implements ParseVisitor<BaseBinding, BaseBinding> {
    private final CompilationEnv mEnv;

    private ModelScope mScope;

    ModelGenerator(CompilationEnv env) {
        mEnv = env;
    }

    CompilationEnv env() {
        return mEnv;
    }

    private void enterScope(ModelScope newScope) {
        mScope = newScope;
    }

    private void exitScope() {
        mScope = mScope.finish();
    }

    /**
     * Should be called when initially visiting a statement, to check if it would be
     * unreachable. If not called, adding an action can throw a TerminatedBlockException.
     *
     * <p>It's not strictly necessary to call this method, but it can reduce the number of
     * errors generated.
     *
     * @see ModelScope#checkReachability
     */
    private boolean checkUnreachable(Element element) {
        int count = mScope.checkReachability();
        if (count > 0) {
            if (count == 1) { // only need to report the error once
                mEnv.error(element, "unreachable");
            }
            return true;
        }
        return false;
    }

    // Visitor methods: Null is returned if an error was reported. Void is returned if the
    // statement returns void, and a non-void target binding is returned otherwise. If a null
    // target binding is passed in, then the visit method must provide a target binding on its
    // own if necessary. Otherwise, it should use the target binding already provided.

    @Override
    public BaseBinding visit(AsStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(ClassDefinitionStatement st, BaseBinding target) {
        // The clazz field should have been assigned when createNewClass was called.
        enterScope(new ModelScope(this, mScope, st.clazz));

        for (Statement item : st.code.items) {
            switch (item) {
                case FunctionDefinitionStatement fds -> {
                    fds.accept(this, null);
                }
                case DeclarationStatement ds -> {
                    // This might be redundant, but it checks for additional name conflicts.
                    mScope.addDeclaration(ds);

                    /* FIXME
                    if (ds.source != null) {
                        // FIXME: Code must be added to the constructor(s) or static initializer.
                        // If a simple final constant, then initialize the JVM field directly.
                        throw null;
                    }
                    */
                }
                default -> {
                    mEnv.error(item, "invalid class member");
                }
            }
        }

        exitScope();

        return BaseBinding.Void.THE;
    }

    @Override
    public BaseBinding visit(ConstructorDefinitionStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(CoordinateLoadStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(DeclarationStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(EmptyStatement st, BaseBinding target) {
        // No actions are added, so don't bother checking reachability.
        return BaseBinding.Void.THE;
    }

    @Override
    public BaseBinding visit(FieldLoadStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(InfixStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(IsStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(JumpStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(LabeledStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(LambdaStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(LiteralStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(LoadStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(MethodCallStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(MethodDefinitionStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(NewArrayStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(NewClassDefinitionStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(NewStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(PostfixStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(PrefixStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(ReturnStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(SequenceStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(StoreStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(ThrowStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(TupleStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }

    @Override
    public BaseBinding visit(YieldStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // FIXME
        throw null;
    }
}
