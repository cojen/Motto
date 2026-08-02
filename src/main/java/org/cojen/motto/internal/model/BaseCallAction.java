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

import org.cojen.motto.model.CallAction;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed abstract class BaseCallAction extends FlowAction implements CallAction {
    private final BaseCallableItem mCallable;
    private final BaseBinding mOutput;
    private final BaseBinding[] mInputs;
    private final BaseSegmentArgument[] mSegments;

    BaseCallAction(int position, BaseCallableItem callable,
                   BaseBinding output, BaseBinding... inputs)
    {
        this(position, callable, output, inputs, (BaseSegmentArgument[]) null);
    }

    BaseCallAction(int position, BaseCallableItem callable,
                   BaseBinding output, BaseBinding[] inputs, BaseSegmentArgument... segments)
    {
        super(position);
        mCallable = Objects.requireNonNull(callable);
        mOutput = Objects.requireNonNull(output);
        mInputs = Objects.requireNonNull(inputs);
        mSegments = segments;
    }

    @Override
    public final BaseCallableItem callable() {
        return mCallable;
    }

    @Override
    public final BaseBinding output() {
        return mOutput;
    }

    @Override
    public final int numInputs() {
        return mInputs.length;
    }

    @Override
    public final BaseBinding input(int index) {
        return mInputs[index];
    }

    public final int numSegments() {
        return mSegments == null ? 0 : mSegments.length;
    }

    public final BaseSegmentArgument segment(int index) {
        if (mSegments == null) {
            throw new IndexOutOfBoundsException();
        }
        return mSegments[index];
    }

    @Override
    final void trackBlockLocalBindings(Map<BaseBinding.Anonymous, Boolean> map) {
        for (BaseBinding input : mInputs) {
            input.trackBlockLocalSource(map);
        }

        mOutput.trackBlockLocalTarget(map);
    }

    public static final class Direct extends BaseCallAction implements CallAction.Direct {
        public Direct(int position, BaseCallableItem callable,
                      BaseBinding output, BaseBinding... inputs)
        {
            super(position, callable, output, inputs);
        }

        public Direct(int position, BaseCallableItem callable,
                      BaseBinding output, BaseBinding[] inputs, BaseSegmentArgument... segments)
        {
            super(position, callable, output, inputs, segments);
        }

        @Override
        public <R> R accept(ActionVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class New extends BaseCallAction implements CallAction.New {
        /**
         * @param callable constructor to call; its output will be dropped; the first input
         * must be a "this" instance
         * @param inputs constructor inputs, excluding the "this" instance
         */
        public New(int position, BaseCallableItem callable,
                   BaseBinding output, BaseBinding... inputs)
        {
            super(position, callable, output, inputs);
        }

        @Override
        public <R> R accept(ActionVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Virtual extends BaseCallAction implements CallAction.Virtual {
        /**
         * @param callable method to call; this first input is a "this" instance
         * @param inputs method inputs, starting with the "this" instance
         */
        public Virtual(int position, BaseCallableItem callable,
                       BaseBinding output, BaseBinding... inputs)
        {
            super(position, callable, output, inputs);
        }

        @Override
        public <R> R accept(ActionVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }
}
