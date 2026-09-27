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
     * Returns a version of this signature in which the output and input types don't have any
     * names.
     */
    public CallSignature noFieldNames();

    /**
     * Returns a signature in which evaluated inputs become {@link Binding Bindings},
     * unevaluated inputs become {@link Block Blocks}, and the output type is a {@code Block}.
     * The inputs of the returned signature are themselves eagerly evaluated.
     */
    // FIXME: update comments
    public CallSignature forMacro();
}
