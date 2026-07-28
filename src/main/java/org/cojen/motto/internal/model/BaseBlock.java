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

import java.util.Iterator;

import org.cojen.motto.model.Action;
import org.cojen.motto.model.ArrayType;
import org.cojen.motto.model.Binding;
import org.cojen.motto.model.Block;
import org.cojen.motto.model.CallableItem;
import org.cojen.motto.model.ClauseArgument;
import org.cojen.motto.model.Type;
import org.cojen.motto.model.TupleType;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class BaseBlock implements Block {
    @Override
    public Iterator<Action> iterator() {
        // FIXME
        throw null;
    }

    @Override
    public boolean isTerminated() {
        // FIXME
        throw null;
    }

    @Override
    public boolean isFullyTerminated() {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding var(Type type) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding var(Type type, String name) {
        // FIXME
        throw null;
    }

    @Override
    public void yield(Object result) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding result() {
        // FIXME
        throw null;
    }

    @Override
    public void copy(Binding target, Object source) {
        // FIXME
        throw null;
    }

    @Override
    public void cast(Binding target, Object source) {
        // FIXME
        throw null;
    }

    @Override
    public void convert(Binding target, Object source) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding callDirect(Binding target, CallableItem callable, Object... inputs) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding callDirect(Binding target, CallableItem callable, Object[] inputs,
                                  ClauseArgument... clauses) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding callNew(Binding target, CallableItem callable, Object... inputs) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding callVirtual(Binding target, CallableItem callable, Object... inputs) {
        // FIXME
        throw null;
    }

    @Override
    public void jump(Block destination) {
        // FIXME
        throw null;
    }

    @Override
    public void branch(Binding condition, Block whenTrue, Block whenFalse) {
        // FIXME
        throw null;
    }

    @Override
    public void throw_(Object exception) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding catch_(Type exceptionType, String varName, Block handler) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding arrayNew(Binding target, ArrayType type, Object... dims) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding arrayGet(Binding target, Binding array, Object index) {
        // FIXME
        throw null;
    }

    @Override
    public void arraySet(Binding array, Object index, Object value) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding tupleNew(Binding target, TupleType type, Object... inputs) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding tupleGet(Binding target, Binding tuple, Binding index) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding tupleGet(Binding target, Binding tuple, int index) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding tupleGet(Binding target, Binding tuple, String label) {
        // FIXME
        throw null;
    }

    @Override
    public void tupleSet(Binding tuple, Binding index, Object value) {
        // FIXME
        throw null;
    }

    @Override
    public void tupleSet(Binding tuple, int index, Object value) {
        // FIXME
        throw null;
    }

    @Override
    public void tupleSet(Binding tuple, String index, Object value) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding add(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding sub(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding mul(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding div(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding rem(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding shl(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding shr(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding ushr(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding and(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding or(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding xor(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding eq(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding ne(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding lt(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding ge(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding gt(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding le(Binding target, Object input1, Object input2) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding neg(Binding target, Object input) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding com(Binding target, Object input) {
        // FIXME
        throw null;
    }

    @Override
    public BaseBinding not(Binding target, Object input) {
        // FIXME
        throw null;
    }
}
