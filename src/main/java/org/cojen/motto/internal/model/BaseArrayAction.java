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

import org.cojen.motto.model.ArrayAction;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class BaseArrayAction extends FlowAction implements ArrayAction {
    BaseArrayAction(int position) {
        super(position);
    }

    public static final class New extends BaseArrayAction implements ArrayAction.New {
        private final BaseArrayType mType;
        private final BaseBinding mOutput;
        private final BaseBinding[] mDimensions;

        public New(int position, BaseArrayType type, BaseBinding output, BaseBinding... dims) {
            super(position);
            mType = Objects.requireNonNull(type);
            mOutput = Objects.requireNonNull(output);
            mDimensions = Objects.requireNonNull(dims);
        }

        @Override
        public <R> R accept(ActionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public BaseArrayType type() {
            return mType;
        }

        @Override
        public BaseBinding output() {
            return mOutput;
        }

        @Override
        public int numDimensions() {
            return mDimensions.length;
        }

        @Override
        public BaseBinding dimension(int index) {
            return mDimensions[index];
        }

        @Override
        void trackBlockLocalBindings(Map<BaseBinding.Anonymous, Boolean> map) {
            for (BaseBinding dim : mDimensions) {
                dim.trackBlockLocalSource(map);
            }

            mOutput.trackBlockLocalTarget(map);
        }
    }

    public static final class Get extends BaseArrayAction implements ArrayAction.Get {
        private final BaseBinding mArray;
        private final BaseBinding mOutput;
        private final BaseBinding mIndex;

        public Get(int position, BaseBinding array, BaseBinding output, BaseBinding index) {
            super(position);
            mArray = Objects.requireNonNull(array);
            mOutput = Objects.requireNonNull(output);
            mIndex = Objects.requireNonNull(index);
        }

        @Override
        public <R> R accept(ActionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public BaseBinding array() {
            return mArray;
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
            mArray.trackBlockLocalSource(map);
            mIndex.trackBlockLocalSource(map);
            mOutput.trackBlockLocalTarget(map);
        }
    }

    public static final class Set extends BaseArrayAction implements ArrayAction.Set {
        private final BaseBinding mArray;
        private final BaseBinding mIndex;
        private final BaseBinding mValue;

        public Set(int position, BaseBinding array, BaseBinding index, BaseBinding value) {
            super(position);
            mArray = Objects.requireNonNull(array);
            mIndex = Objects.requireNonNull(index);
            mValue = Objects.requireNonNull(value);
        }

        @Override
        public <R> R accept(ActionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public BaseBinding array() {
            return mArray;
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
            mArray.trackBlockLocalSource(map);
            mIndex.trackBlockLocalSource(map);
            mValue.trackBlockLocalSource(map);
        }
    }
}
