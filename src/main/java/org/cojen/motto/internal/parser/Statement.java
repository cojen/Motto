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

package org.cojen.motto.internal.parser;

import java.util.List;

import org.cojen.motto.internal.compiler.CompilationEnv;

import org.cojen.motto.internal.model.BaseItem;
import org.cojen.motto.internal.model.NewClass;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed interface Statement extends Element permits
    AsStatement, CodeScopeStatement, CoordinateLoadStatement, DeclarationStatement,
    DefinitionStatement, EmptyStatement, FieldLoadStatement, InfixStatement, IsStatement,
    JumpStatement, LabeledStatement, LambdaStatement, LiteralStatement, NewArrayStatement,
    NewStatement, PathStatement, PostfixStatement, PrefixStatement, ReturnStatement,
    SequenceStatement, StoreStatement, ThrowStatement, TupleStatement, YieldStatement
{
    public <R, P> R accept(ParseVisitor<R, P> v, P param);

    /**
     * @see LabeledStatement
     */
    public default Statement delabel() {
        return this;
    }

    /**
     * Delabel and report an error if a label is defined.
     *
     * @see LabeledStatement
     */
    public default Statement noLabel(CompilationEnv env) {
        return this;
    }

    /**
     * @param p for reporting errors
     */
    public default VarType asVarType(Parser p) {
        p.error(this, "illegal type");
        // Return a bogus type.
        return new TupleVarType(start(), List.of(), end());
    }

    /**
     * In order for a new class to move to the prepared state, all members which can be
     * imported by other classes must be available. Statements which declare or define
     * importable members must implement this method. Note that the statement doesn't know what
     * scope it's in, and so it's the caller's responsibility to examine the scope.
     */
    public default void prepareClass(CompilationEnv env, NewClass clazz) {
        // Nothing to do in most cases.
    }

    /**
     * Is called by ClassDefinitionStatement.resolveClass to add declarations and definitions
     * to a NewClass. If this method was already called, calling it again returns the item
     * which was possibly added earlier. This method is a companion to the prepareClass method.
     *
     * @return null if an error was reported or if this statement doesn't add anything to a class
     */
    public default BaseItem addToClass(CompilationEnv env, NewClass clazz) {
        // Nothing to do in most cases.
        return null;
    }
}
