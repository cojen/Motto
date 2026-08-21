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

import java.util.List;

import org.cojen.motto.internal.model.BaseBinding;
import org.cojen.motto.internal.model.BaseBlock;
import org.cojen.motto.internal.model.BaseCallableItem;
import org.cojen.motto.internal.model.BaseItem;
import org.cojen.motto.internal.model.BaseType;
import org.cojen.motto.internal.model.BaseVoidType;

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
import org.cojen.motto.internal.parser.Token;
import org.cojen.motto.internal.parser.TupleStatement;
import org.cojen.motto.internal.parser.YieldStatement;

import static org.cojen.motto.internal.parser.Token.*;

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

    private void error(Element element, String message) {
        mEnv.error(element, message);
    }

    private void error(List<? extends Element> list, String message) {
        mEnv.error(list, message);
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
                error(element, "unreachable");
            }
            return true;
        }
        return false;
    }

    // Visit methods: Null is returned if an error was reported. Void is returned if the
    // statement returns void, and a non-void target binding is returned otherwise. If a null
    // target binding is passed in, then the visit method must provide a target binding on its
    // own if necessary. Otherwise, it should use the target binding already provided.

    /**
     * Note: Caller must call exitScope.
     */
    private BaseBinding visitCode(TupleStatement code, BaseCallableItem item) {
        var newScope = new ModelScope(this, mScope, item);

        List<Statement> items = code.items;

        // Add all the symbols first, allowing them to be accessed in any order.
        for (Statement st : items) {
            // FIXME: Local variables and methods. Check modifiers.

            if (st instanceof LabeledStatement ls) {
                while (true) {
                    if (!newScope.addLabel(ls)) {
                        error(ls.label, "duplicate label");
                    }
                    if (ls.source instanceof LabeledStatement source) {
                        ls = source;
                    } else {
                        break;
                    }
                }
            }
        }

        /* FIXME: always true
        if (item instanceof BaseCallableItem callable) {
            // Need to allocate local variable indexes for the output and inputs.

            BaseCallSignature signature;

            if (!callable.isMacro()) {
                signature = callable.signature();
            } else {
                signature = callable.macroSignature();
            }

            signature = signature.flatten();

            BaseTupleType inputType = signature.inputType();
            int numInputs = inputType.numElements();

            // Define the output local variable.
            newScope.localVariable(signature.outputType(), null);

            for (int i=0; i<numInputs; i++) {
                newScope.localVariable(inputType.elementType(i), inputType.elementName(i));
            }
        }
        */

        enterScope(newScope);

        BaseBinding lastBinding = BaseBinding.Void.THE;
        int size = items.size();

        if (size > 0) {
            if (size == 1) {
                // FIXME: auto yield action?
                lastBinding = items.getFirst().accept(this, null);
            } else {
                for (Statement st : items) {
                    BaseBinding binding = st.accept(this, null);
                }
                lastBinding = mScope.activeBlockResult();
            }
        }

        LabeledStatement ls = mScope.checkLabelReachability();

        if (ls != null) {
            error(ls, "unreachable");
        } else if (mScope.isReachable()) {
            // The scope must end with a return statement.
            if (item.isMacro() || item.signature().outputType() != BaseVoidType.THE) {
                error(code.end(), "missing return statement");
            }
        }

        return lastBinding;
    }

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
                    error(item, "invalid class member");
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

        int opType = st.operator.type();

        if (opType == T_AND || opType == T_OR) {
            // FIXME
            throw null;
        }

        BaseBinding leftBinding = st.left.accept(this, null);

        if (leftBinding == null) {
            // Error state.
            return null;
        }

        BaseBinding rightBinding = st.right.accept(this, null);

        if (rightBinding == null) {
            // Error state.
            return null;
        }

        // FIXME: find a common type; widen if necessary

        BaseBlock block = mScope.activeBlock(st);

        switch (opType) {
            default -> {
                error(st.operator, "unsupported infix operator");
                return null;
            }

            case T_EQ -> {return block.eq(target, leftBinding, rightBinding);}
            case T_NE -> {return block.ne(target, leftBinding, rightBinding);}
            case T_GE -> {return block.ge(target, leftBinding, rightBinding);}
            case T_LT -> {return block.lt(target, leftBinding, rightBinding);}
            case T_LE -> {return block.le(target, leftBinding, rightBinding);}
            case T_GT -> {return block.gt(target, leftBinding, rightBinding);}

            case T_LAND  -> {return block.and(target, leftBinding, rightBinding);}
            case T_LOR   -> {return block.or(target, leftBinding, rightBinding);}
            case T_LXOR  -> {return block.xor(target, leftBinding, rightBinding);}
            case T_PLUS  -> {return block.add(target, leftBinding, rightBinding);}
            case T_MINUS -> {return block.sub(target, leftBinding, rightBinding);}
            case T_MUL   -> {return block.mul(target, leftBinding, rightBinding);}
            case T_DIV   -> {return block.div(target, leftBinding, rightBinding);}
            case T_REM   -> {return block.rem(target, leftBinding, rightBinding);}
            case T_SHL   -> {return block.shl(target, leftBinding, rightBinding);}
            case T_SHR   -> {return block.shr(target, leftBinding, rightBinding);}
            case T_USHR  -> {return block.ushr(target, leftBinding, rightBinding);}
        }
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

        Token literal = st.literal;
        BaseType type = literal.literalType();
        Object value = literal.literalValue();

        if (type == null) {
            // Not expected.
            error(literal, "unsupported literal type");
            return null;
        }

        BaseBinding constant = BaseBinding.Constant.from(type, value);

        if (target == null) {
            return constant;
        }

        // FIXME: Automatic conversions when possible. Sometimes String to char.

        mScope.activeBlock(st).copy(target, constant);

        return target;
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

        // If the returned method is null, then an error should have been reported already.
        BaseCallableItem method = mScope.addMethod(st);

        if (method != null) {
            if (st.code == null) {
                // FIXME: must be abstract or be in an interface
            } else {
                visitCode(st.code, method);
                exitScope();
            }
        }

        return BaseBinding.Void.THE;
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

        BaseBlock block = mScope.activeBlock(st);

        BaseBinding result;

        if (st.source == null) {
            result = BaseBinding.Void.THE;
        } else {
            BaseBinding retVar = mScope.returnVar(st);
            result = st.source.accept(this, retVar);
            if (result != null) {
                // Note: The copy does nothing if the source result is the return var.
                block.copy(retVar, result);
            }
        }

        block.jump(mScope.returnBlock(st));

        return BaseBinding.Void.THE;
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
