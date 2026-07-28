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

package org.cojen.motto.model;

import org.cojen.motto.internal.model.BaseCallSignature;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed interface CallSignature permits BaseCallSignature {
    public Type outputType();

    public String name();

    public TupleType inputType();

    /**
     * Returns true if all inputs are eagerly evaluated, which is true for a normal call.
     */
    public boolean isInputEvaluated();

    public int numClauses();

    public Clause clause(int index);

    /**
     * Returns a version of this signature in which the output and input types don't have any
     * names.
     */
    public CallSignature noFieldNames();

    /**
     * Returns a signature in which evaluated inputs become {@link Binding Bindings},
     * unevaluated inputs become {@link Block Blocks}, and the output type is a {@code Block}.
     * The inputs of the returned signature are themselves eagerly evaluated.
     */
    public CallSignature forMacro();

    /**
     * Returns a signature which has no clauses and the input type is eagerly evaluated. A
     * flattened signature can be used to define a Java method.
     *
     * <p>It should be noted that the flattened representation is lossy. Repetition details are
     * lost, and clause interleaving order is also lost.
     *
     * <p>The following transformations are made:
     * <ul>
     * <li>Parameter types which represent unevaluated code blocks are converted to function
     *     types.
     * <li>Clauses are converted to parameters, and they appear after the regular parameters.
     * <li>Clauses which are defined once are represented by tuples of named parameters.
     * <li>Clauses which have repetition are represented by tuple arrays.
     * <li>Clauses which are defined more than once (by name) are represented by tuples of
     *     tuples or tuples of tuple arrays. The number of fields in the outer tuple matches
     *     the number of times the clause is defined, and the outer tuple fields are unnamed.
     * </ul>
     */
    public CallSignature flatten();

    public static sealed interface Clause permits BaseCallSignature.TheClause {
        public boolean isRequired();

        public boolean hasRepetition();

        public String name();

        public TupleType inputType();

        /**
         * Returns true if all inputs are eagerly evaluated.
         */
        public boolean isInputEvaluated();

        /**
         * Returns a version of this clause in which the input type doesn't have any names.
         */
        public Clause noFieldNames();
    }
}
