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

import org.cojen.motto.model.Action;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class BaseAction implements Action
    permits FlowAction, BaseTerminalAction, BaseYieldAction
{
    private final int mPosition;

    /**
     * @param position encodes the source code line and column corresponding to this action;
     * pass 0 if not applicable
     */
    BaseAction(int position) {
        mPosition = position;
    }

    public abstract <R> R accept(ActionVisitor<R> visitor);

    @Override
    public int line() {
        return decodeLine(mPosition);
    }

    @Override
    public int column() {
        return decodeColumn(mPosition);
    }

    int position() {
        return mPosition;
    }

    /**
     * For each binding used by this action, calls BaseBinding#trackBlockLocalSource or
     * BaseBinding#trackBlockLocalTarget against the given map. True values indicate that the
     * binding value is dependent upon a prior block.
     *
     * <p>If the action has source and target bindings, all the source bindings should be
     * tracked first, ensuring that the true value has precedence. The target could the same as
     * the source, and so naturally, it must be accessed as a source first.
     *
     * @param map a map associated with one block
     * @see BaseBinding
     */
    abstract void trackBlockLocalBindings(Map<BaseBinding.Anonymous, Boolean> map);

    /**
     * @param line source code start line, one-based; is 0 if not applicable
     * @param column source code start column, zero-based; is -1 if not applicable
     */
    public static int encodePosition(int line, int column) {
        // Favor the line number, dropping the column number if necessary.

        // -1 means not applicable, but encode as 0.
        column++;

        if (line >= (1 << 6)) {
            if (line < (1 << 12)) {
                // (12-bit line, 18-bit column)
                return (1 << 30) | (line << 18 | columnLimit(column, (1 << 18) - 1));
            }
            if (line < (1 << 18)) {
                // (18-bit line, 12-bit column)
                return (2 << 30) | (line << 12 | columnLimit(column, (1 << 12) - 1));
            }
            if (line < (1 << 24)) {
                // (24-bit line, 6-bit column)
                return (3 << 30) | (line <<  6 | columnLimit(column, (1 <<  6) - 1));
            }
            // Line number is too big, so make it not applicable.
            line = 0;
        }

        // (6-bit line, 24-bit column)
        return (0 << 30) | (line << 24 | Math.min(column, (1 << 24) - 1));
    }

    private static int columnLimit(int value, int limit) {
        return value <= limit ? value : 0; // 0 will be decoded as -1, or not applicable
    }

    public static int decodeLine(int position) {
        return switch (position >>> 30) {
            default -> (position >> 24) & ((1 <<  6) - 1);
            case 1  -> (position >> 18) & ((1 << 12) - 1);
            case 2  -> (position >> 12) & ((1 << 18) - 1);
            case 3  -> (position >>  6) & ((1 << 24) - 1);
        };
    }

    public static int decodeColumn(int position) {
        return switch (position >>> 30) {
            default -> position & ((1 << 24) - 1);
            case 1  -> position & ((1 << 18) - 1);
            case 2  -> position & ((1 << 12) - 1);
            case 3  -> position & ((1 <<  6) - 1);
        } - 1;
    }
}
