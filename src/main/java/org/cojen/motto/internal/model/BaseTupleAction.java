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

import java.util.Map;
import java.util.Objects;

import org.cojen.motto.model.Binding;
import org.cojen.motto.model.TupleAction;
import org.cojen.motto.model.TupleType;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class BaseTupleAction extends FlowAction implements TupleAction {
    BaseTupleAction(int position) {
        super(position);
    }

    public static final class New extends BaseTupleAction implements TupleAction.New {
        private final BaseTupleType mType;
        private final BaseBinding mOutput;
        private final BaseBinding[] mInputs;

        New(int position, BaseTupleType type, BaseBinding output, BaseBinding... inputs) {
            super(position);
            mType = Objects.requireNonNull(type);
            mOutput = Objects.requireNonNull(output);
            mInputs = Objects.requireNonNull(inputs);
        }

        @Override
        public <R> R accept(ActionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public BaseTupleType type() {
            return mType;
        }

        @Override
        public BaseBinding output() {
            return mOutput;
        }

        @Override
        public int numInputs() {
            return mInputs.length;
        }

        @Override
        public BaseBinding input(int index) {
            return mInputs[index];
        }

        @Override
        void trackBlockLocalBindings(Map<BaseBinding.Anonymous, Boolean> map) {
            for (BaseBinding input : mInputs) {
                input.trackBlockLocalSource(map);
            }

            mOutput.trackBlockLocalTarget(map);
        }
    }

    /**
     * Defines an action which reads from a tuple field at a variable index. Use
     * BaseBinding.TupleField if the index is constant.
     */
    public static final class Get extends BaseTupleAction implements TupleAction.Get {
        private final BaseBinding mTuple;
        private final BaseBinding mOutput;
        private final BaseBinding mIndex;

        Get(int position, BaseBinding tuple, BaseBinding output, BaseBinding index) {
            super(position);
            mTuple = Objects.requireNonNull(tuple);
            mOutput = Objects.requireNonNull(output);
            mIndex = Objects.requireNonNull(index);
        }

        @Override
        public <R> R accept(ActionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public BaseBinding tuple() {
            return mTuple;
        }

        @Override
        public BaseBinding output() {
            return mOutput;
        }

        @Override
        public BaseBinding index() {
            return mIndex;
        }

        @Override
        void trackBlockLocalBindings(Map<BaseBinding.Anonymous, Boolean> map) {
            mTuple.trackBlockLocalSource(map);
            mIndex.trackBlockLocalSource(map);
            mOutput.trackBlockLocalTarget(map);
        }
    }

    /**
     * Defines an action which writes to a tuple field at a variable index. Use
     * BaseBinding.TupleField if the index is constant.
     */
    public static final class Set extends BaseTupleAction implements TupleAction.Set {
        private final BaseBinding mTuple;
        private final BaseBinding mIndex;
        private final BaseBinding mValue;

        Set(int position, BaseBinding tuple, BaseBinding index, BaseBinding value) {
            super(position);
            mTuple = Objects.requireNonNull(tuple);
            mIndex = Objects.requireNonNull(index);
            mValue = Objects.requireNonNull(value);
        }

        @Override
        public <R> R accept(ActionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public BaseBinding tuple() {
            return mTuple;
        }

        @Override
        public BaseBinding index() {
            return mIndex;
        }

        @Override
        public BaseBinding value() {
            return mValue;
        }

        @Override
        void trackBlockLocalBindings(Map<BaseBinding.Anonymous, Boolean> map) {
            mValue.trackBlockLocalSource(map);
            mIndex.trackBlockLocalSource(map);
            mTuple.trackBlockLocalTarget(map);
        }
    }
}
