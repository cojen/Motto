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

package org.cojen.motto.internal.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import java.util.function.IntFunction;

/**
 * Debug printer.
 *
 * @author Brian S. O'Neill
 */
final class Printer implements ActionVisitor<Object> {
    private final HashMap<BaseBlock, Integer> mBlockIdMap;

    private final HashMap<BaseBinding.Anonymous, String> mVarNames;

    private final HashSet<BaseBlock> mVisited;
    private ArrayList<BaseBlock> mVisitLater;

    private int mLineNum;

    Printer() {
        mBlockIdMap = new HashMap<>();
        mVarNames = new HashMap<>();
        mVisited = new HashSet<>();
        mVisitLater = new ArrayList<>();
    }

    void visit(BaseBlock block) {
        doVisit(block);

        while (true) {
            ArrayList<BaseBlock> visitList = mVisitLater;

            if (visitList.isEmpty()) {
                break;
            }

            mVisitLater = new ArrayList<>();

            for (BaseBlock b : visitList) {
                doVisit(b);
            }
        }
    }

    private void doVisit(BaseBlock block) {
        if (!mVisited.add(block)) {
            return;
        }

        println("block " + blockId(block));

        Iterator<BaseAction> it = block.baseIterator();
        BaseAction action = null;

        while (it.hasNext()) {
            action = it.next();

            int lineNum = action.line();

            if (lineNum != 0 && lineNum != mLineNum) {
                println("    .line " + lineNum);
                mLineNum = lineNum;
            }

            action.accept(block, this);
        }

        if (!(action instanceof BaseTerminalAction)) {
            BaseBinding result = block.context().resultBinding();
            if (result.type().isVoid()) {
                println("    return");
            } else {
                println("    return " + toString(result));
            }
        }
    }

    @Override
    public Object visit(BaseBlock block, BaseArrayAction.New action) {
        var b = new StringBuilder().append("    ");

        if (!action.output().type().isVoid()) {
            b.append(toString(action.output()) + " = ");
        }

        b.append("new ");

        BaseType elementType = action.type().arrayElementType();

        while (elementType.isArray()) {
            elementType = elementType.arrayElementType();
        }

        b.append(elementType.displayName());

        for (int i=0; i<action.numDimensions(); i++) {
            b.append('[').append(toString(action.dimension(i))).append(']');

        }

        println(b.toString());

        return null;
    }

    @Override
    public Object visit(BaseBlock block, BaseArrayAction.Get action) {
        var b = new StringBuilder().append("    ");

        b.append(toString(action.output()) + " = ");

        b.append(toString(action.array()));

        b.append('[');
        b.append(toString(action.index()));
        b.append(']');

        println(b.toString());

        return null;
    }

    @Override
    public Object visit(BaseBlock block, BaseArrayAction.Set action) {
        var b = new StringBuilder().append("    ");

        b.append(toString(action.array()));

        b.append('[');
        b.append(toString(action.index()));
        b.append(']');

        b.append(" = " + toString(action.value()));

        println(b.toString());

        return null;
    }

    @Override
    public Object visit(BaseBlock block, BaseBranchAction action) {
        println("    if (" + toString(action.condition()) + ") goto " +
                toString(action.whenTrue()) + " else goto " + toString(action.whenFalse()));
        return null;
    }

    @Override
    public Object visit(BaseBlock block, BaseCallAction.Direct action) {
        var b = new StringBuilder().append("    ");

        if (!action.output().type().isVoid()) {
            b.append(toString(action.output()) + " = ");
        }

        b.append(action.callable().signature().name());

        if (action.callable().isMacro()) {
            b.append(' ');
        }

        b.append('(');
        b.append(toString(action.numInputs(), i -> toString(action.input(i))));
        b.append(')');

        println(b.toString());

        return null;
    }

    @Override
    public Object visit(BaseBlock block, BaseCallAction.New action) {
        var b = new StringBuilder().append("    ");

        if (!action.output().type().isVoid()) {
            b.append(toString(action.output()) + " = ");
        }

        b.append("new ").append(action.callable().nearestType().displayName());

        b.append('(');
        b.append(toString(action.numInputs(), i -> toString(action.input(i))));
        b.append(')');

        println(b.toString());

        return null;
    }

    @Override
    public Object visit(BaseBlock block, BaseCallAction.Virtual action) {
        var b = new StringBuilder().append("    ");

        if (!action.output().type().isVoid()) {
            b.append(toString(action.output()) + " = ");
        }

        b.append(toString(action.input(0))).append('.');

        b.append(action.callable().signature().name());

        if (action.callable().isMacro()) {
            b.append(' ');
        }

        b.append('(');
        b.append(toString(action.numInputs() - 1, i -> toString(action.input(i + 1))));
        b.append(')');

        println(b.toString());

        return null;
    }

    @Override
    public Object visit(BaseBlock block, BaseCastAction action) {
        // FIXME: visit BaseCastAction
        throw null;
    }

    @Override
    public Object visit(BaseBlock block, BaseConvertAction action) {
        // FIXME: visit BaseConvertAction
        throw null;
    }

    @Override
    public Object visit(BaseBlock block, BaseCopyAction action) {
        println("    " + toString(action.target()) + " = " + toString(action.source()));
        return null;
    }

    @Override
    public Object visit(BaseBlock block, BaseJumpAction action) {
        println("    goto " + toString(action.destination()));
        return null;
    }

    @Override
    public Object visit(BaseBlock block, BaseThrowAction action) {
        println("    throw " + toString(action.exception()));
        return null;
    }

    @Override
    public Object visit(BaseBlock block, BaseTupleAction.New action) {
        var b = new StringBuilder().append("    ");

        if (!action.output().type().isVoid()) {
            b.append(toString(action.output()) + " = ");
        }

        b.append('(');
        b.append(toString(action.numInputs(), i -> toString(action.input(i))));
        b.append(')');

        println(b.toString());

        return null;
    }

    @Override
    public Object visit(BaseBlock block, BaseTupleAction.Get action) {
        // FIXME: visit BaseTupleAction.Get
        throw null;
    }

    @Override
    public Object visit(BaseBlock block, BaseTupleAction.Set action) {
        // FIXME: visit BaseTupleAction.Set
        throw null;
    }

    @Override
    public Object visit(BaseBlock block, UnreachableAction action) {
        println("    <unreachable>");
        return null;
    }

    private int blockId(BaseBlock block) {
        return mBlockIdMap.computeIfAbsent(block, k -> mBlockIdMap.size() + 1);
    }

    private int toString(BaseBlock block) {
        if (!mVisited.contains(block)) {
            mVisitLater.add(block);
        }
        return blockId(block);
    }

    private String toString(BaseBinding binding) {
        return switch (binding) {
            case BaseBinding.Void _ -> "void";

            case BaseBinding.Null _ -> "null";

            case BaseBinding.Constant b -> {
                Object value = b.value();
                if (value instanceof String s) {
                    yield '"' + s + '"';
                }
                yield String.valueOf(value);
            }

            case BaseBinding.Branch b -> toString(b.value());

            case BaseBinding.StaticField b -> {
                ClassFieldItem field = b.field();
                yield field.nearestType().displayName() + '.' + field.name();
            }

            case BaseBinding.InstanceField b -> toString(b.instance()) + '.' + b.field().name();

            case BaseBinding.ArrayLength b -> toString(b.instance()) + ".length";

            case BaseBinding.ClassLiteral b -> b.sourceType().displayName() + ".class";

            case BaseBinding.TupleField b -> toString(b.tuple()) + '[' + b.index() + ']';

            case BaseBinding.Parameter b -> {
                String name = b.name();
                if (name != null) {
                    if (name.isEmpty()) {
                        yield "``";
                    }
                    if (name.isBlank()) {
                        yield '`' + name + '`';
                    }
                    yield name;
                }
                yield "param_" + b.index();
            }

            case BaseBinding.Named b -> b.name();

            case BaseBinding.Anonymous b -> {
                yield mVarNames.computeIfAbsent(b, k -> ("var_" + (mVarNames.size() + 1)));
            }

            case BaseBinding.Captured b -> {
                // FIXME: BaseBinding.Captured
                throw null;
            }

            case BaseBinding.CodeBinding b -> "{block " + toString(b.entry()) + '}';

            default -> binding.toString();
        };
    }

    private String toString(int numInputs, IntFunction<String> inputAccessor) {
        var b = new StringBuilder();

        for (int i=0; i<numInputs; i++) {
            if (i != 0) {
                b.append(", ");
            }
            b.append(inputAccessor.apply(i));
        }

        return b.toString();
    }

    private void println(Object obj) {
        System.out.println(obj);
    }
}
