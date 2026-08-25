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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.cojen.motto.model.CallableItem;

import org.cojen.motto.internal.model.BaseArrayType;
import org.cojen.motto.internal.model.BaseBinding;
import org.cojen.motto.internal.model.BaseBlock;
import org.cojen.motto.internal.model.BaseBooleanType;
import org.cojen.motto.internal.model.BaseCallSignature;
import org.cojen.motto.internal.model.BaseCallableItem;
import org.cojen.motto.internal.model.BaseClassTypeItem;
import org.cojen.motto.internal.model.BaseFieldItem;
import org.cojen.motto.internal.model.BaseIntType;
import org.cojen.motto.internal.model.BaseItem;
import org.cojen.motto.internal.model.BaseNullType;
import org.cojen.motto.internal.model.BaseObjectType;
import org.cojen.motto.internal.model.BasePath;
import org.cojen.motto.internal.model.BaseSegmentArgument;
import org.cojen.motto.internal.model.BaseTupleType;
import org.cojen.motto.internal.model.BaseType;
import org.cojen.motto.internal.model.BaseUnspecifiedType;
import org.cojen.motto.internal.model.BaseVoidType;
import org.cojen.motto.internal.model.LoadedClass;

import org.cojen.motto.internal.parser.AsStatement;
import org.cojen.motto.internal.parser.ClassDefinitionStatement;
import org.cojen.motto.internal.parser.CodeScopeStatement;
import org.cojen.motto.internal.parser.ConstructorDefinitionStatement;
import org.cojen.motto.internal.parser.Coordinate;
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
import org.cojen.motto.internal.parser.PathStatement;
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

    private static String typeString(BaseType type) {
        // FIXME: typeString
        return String.valueOf(type);
        //return ToStringVisitor.toString(type);
    }

    /**
     * Examines the first path element to determine if it matches a local variable or a special
     * keyword.
     */
    private BaseBinding tryMatchFirstSymbol(ListIterator<Token.Identifier> pathIt) {
        Token.Identifier token = pathIt.next();
        String name = token.text;

        for (ModelScope scope = mScope; scope != null; scope = scope.parent()) {
            BaseItem item = scope.item();

            if (item instanceof BaseClassTypeItem) {
                break;
            }

            BaseBinding.Local local = scope.tryFindLocalVariable(name);

            if (local != null) {
                return local;
            }
        }

        if (!token.quoted) {
            // Check against a keyword.

            switch (name) {
                case "null" -> {
                    return BaseBinding.Null.THE;
                }
                case "false" -> {
                    return BaseBinding.Constant.from(BaseBooleanType.THE, false);
                }
                case "true" -> {
                    return BaseBinding.Constant.from(BaseBooleanType.THE, true);
                }
            }
        }

        // Back up.
        pathIt.previous();

        return null;
    }

    private BaseBinding.Parameter tryAccessThis() {
        for (ModelScope scope = mScope; scope != null; scope = scope.parent()) {
            BaseItem item = scope.item();

            if (item instanceof BaseClassTypeItem) {
                break;
            }

            if (item instanceof BaseCallableItem callable) {
                if (callable.isStatic()) {
                    break;
                }
                BaseType thisType = callable.signature().inputType().fieldType(0);
                return BaseBinding.Parameter.from(thisType, "this", 0);
            }
        }

        return null;
    }

    /**
     * Examines the path to determine if it starts as a static path. If so, a ClassTypeItem is
     * returned, and the path iterator is positioned at the next element to follow, which is
     * typically a static member. If the iterator has no next elements, then the path has
     * completely specified a class and nothing else. The caller must handle this specially.
     *
     * <p>When null is returned, the iterator state isn't defined. It should be discarded.
     *
     * @param pathIt must have at least one element
     */
    private BaseClassTypeItem tryResolveClass(PathStatement st,
                                              ListIterator<Token.Identifier> pathIt)
    {
        Token.Identifier nameToken = pathIt.next();
        BaseItem item = mScope.item();
        String name = nameToken.text;

        BaseClassTypeItem clazz = findClassForStaticField(item, name);

        if (clazz != null) {
            // Back up to the first member.
            pathIt.previous();
            return clazz;
        }

        clazz = findLocalClass(item, name);

        if (clazz == null) {
            clazz = mEnv.matchClassItem(BasePath.from(st.path));
            if (clazz != null) {
                int pathPos = clazz.fullPathSize();
                while (--pathPos > 0) {
                    // Jump past the class name elements.
                    pathIt.next();
                }
            } else {
                clazz = mEnv.findImportedClass(nameToken);
                if (clazz == null) {
                    clazz = mEnv.findImportedClassByMember(nameToken);
                    if (clazz != null) {
                        // Back up to the first member.
                        pathIt.previous();
                    }
                    return clazz;
                }
            }
        }

        // Try to find a static inner class.

        while (pathIt.hasNext()) {
            nameToken = pathIt.next();

            Set<BaseClassTypeItem> set = clazz.findInnerClass
                (nameToken.text, c -> c.isStatic() && c.isAccessibleVia(mScope.item()));

            if (set.isEmpty()) {
                // Back up.
                pathIt.previous();
                break;
            }

            if (set.size() != 1) {
                // FIXME: list them all
                error(nameToken, "inner class is ambiguous");
                return null;
            }

            clazz = set.iterator().next();
        }

        return clazz;
    }

    /**
     * Looks for accessible static fields in the given item, then inherited static fields,
     * and then any enclosing outer classes.
     */
    private BaseClassTypeItem findClassForStaticField(BaseItem item, String name) {
        BaseClassTypeItem clazz = item.nearestClass();

        while (clazz != null) {
            // The findField method also returns inherited fields.
            Set<BaseFieldItem> set = clazz.findField
                (name, f -> f.isStatic() && f.isAccessibleVia(mScope.item()));

            if (!set.isEmpty()) {
                return clazz;
            }

            clazz = clazz.outerType();
        }

        return null;
    }

    /**
     * Checks if the given name matches a local class, searching outer classes if necessary.
     */
    private BaseClassTypeItem findLocalClass(BaseItem item, String name) {
        BaseClassTypeItem clazz = item.nearestClass();

        while (clazz != null) {
            if (clazz.namePath().getLast().equals(name)) {
                return clazz;
            }
            clazz = clazz.outerType();
        }

        return null;
    }

    /**
     * @return null if not found, and an error was reported
     */
    private BaseFieldItem findStaticField(BaseClassTypeItem clazz, Token.Identifier nameToken) {
        Set<BaseFieldItem> set = clazz.findField
            (nameToken.text, f -> f.isStatic() && f.isAccessibleVia(mScope.item()));

        if (set.isEmpty()) {
            error(nameToken, "static field not found");
            return null;
        }

        if (set.size() != 1) {
            // FIXME: list them all
            error(nameToken, "static field is ambiguous");
            return null;
        }

        return set.iterator().next();
    }

    /**
     * Follows an instance path, returning the original binding or a composite binding.
     *
     * @param st if given a MethodCallStatement, the last item  isn't followed
     * @param instanceBinding the first binding
     * @param autoThis true if tryAccessThis was used (only affects error reporting)
     * @return null if an error was reported.
     */
    private BaseBinding followInstancePath(Statement st, BaseBinding instanceBinding,
                                           boolean autoThis, ListIterator<Token.Identifier> pathIt)
    {
        if (!pathIt.hasNext()) {
            return instanceBinding;
        }

        while (true) {
            Token.Identifier nameToken = pathIt.next();
            String name = nameToken.text;
            boolean last = !pathIt.hasNext();

            if (last && st instanceof MethodCallStatement) {
                // Back up.
                pathIt.previous();
                return instanceBinding;
            }

            BaseType instanceType = instanceBinding.type();

            switch (instanceType) {
                case BaseTupleType t -> {
                    int index = t.fieldIndex(name);

                    if (index < 0) {
                        error(nameToken, "tuple field not found");
                        return null;
                    }

                    instanceBinding = BaseBinding.TupleField.from(instanceBinding, index);
                }

                case BaseArrayType t -> {
                    // FIXME: Support a pseudo final length field.
                    error(nameToken, "array field not found");
                    return null;
                }

                case BaseClassTypeItem t -> {
                    Set<BaseFieldItem> fieldSet = t.findField(name, mScope.item());

                    if (fieldSet.isEmpty()) {
                        String message;
                        if (autoThis) {
                            message = "cannot find symbol";
                        } else {
                            message = "instance field not found";
                        }

                        error(nameToken, message);
                        return null;
                    }

                    if (fieldSet.size() != 1) {
                        // FIXME: list them all
                        error(nameToken, "field is ambiguous");
                        return null;
                    }

                    BaseFieldItem fieldItem = fieldSet.iterator().next();

                    instanceBinding = BaseBinding.Instance.from(instanceBinding, fieldItem);
                }

                default -> {
                    error(nameToken, "instance field not found");
                    return null;
                }
            }

            if (last) {
                return instanceBinding;
            }
        }
    }

    /**
     * @param item pass non-null to make a static call
     * @param instance pass non-null to make an instance call
     * @param direct pass true to always make a direct method call, not a virtual method call
     * @return null if no method was found or if an error was reported
     * @throws IllegalArgumentException if both a member and an instance were provided
     */
    private BaseBinding tryMakeMethodCall(MethodCallStatement st, BaseBinding target,
                                          BaseItem item, BaseBinding instance,
                                          Token.Identifier nameToken,
                                          BaseType[] inputTypes, BaseBinding[] inputBindings,
                                          boolean direct)
    {
        final boolean staticCall = item != null;

        if (staticCall) {
            if (instance != null) {
                throw new IllegalArgumentException();
            }
        } else {
            switch (instance.type()) {
                case BaseClassTypeItem clazz -> {
                    item = clazz;
                }

                case BaseNullType n -> {
                    item = LoadedClass.classFrom(Object.class);
                }

                default -> {
                    // FIXME: If a non-void primitive type, try boxing. Also check if a tuple.
                    error(st, "not invoking an object instance");
                    return null;
                }
            }
        }

        Map<BaseCallSignature, Set<CallableItem>> methods;

        BaseCallSignature.BaseSegment[] segSignatures = null;
        BaseSegmentArgument[] segArguments = null;

        findMethods: {
            List<Statement> segments = st.segments;

            if (!segments.isEmpty()) {
                /* FIXME
                segSignatures = new BaseCallSignature.BaseSegment[segments.size()];
                segArguments = new BaseSegmentArgument[segSignatures.length];

                for (int i=0; i<segSignatures.length; i++) {
                    CallSegment seg = st.segments.get(i);

                    if (!(seg.statement instanceof TupleStatement tuple)) {
                        // This likely indicates a compiler bug.
                        error(seg.statement, "segment parameters must be a tuple");
                        return null;
                    }

                    String name = seg.name == null ? "" : seg.name.text;
                    boolean evaluated = tuple.first.type() == Token.T_LPAREN;

                    List<Statement> items = tuple.items;
                    var segBindings = new BaseBinding[items.size()];
                    var segInputTypes = new BaseType[segBindings.length];

                    boolean hasError = false;

                    for (int j=0; j<segBindings.length; j++) {
                        BaseBinding segInput = items.get(j).accept(this, null);
                        if (segInput == null) {
                            hasError = true;
                        } else {
                            segBindings[j] = segInput;
                            segInputTypes[j] = segInput.type();
                        }
                    }

                    if (hasError) {
                        // Error state.
                        return null;
                    }

                    BaseTupleType segInputType = BaseTupleType.from(segInputTypes);

                    // Can pass -1 for repetition value; it's ignored by findMethod.
                    segSignatures[i] = BaseCallSignature
                        .BaseSegment.from(-1, name, segInputType, evaluated);

                    segArguments[i] = new BaseSegmentArgument(name, segBindings);
                }
                */
                throw null;
            }

            String name = nameToken.text;
            BaseTupleType inputType = BaseTupleType.from(inputTypes);
            boolean evaluated = st.params.open.type() == Token.T_LPAREN;

            var sig = BaseCallSignature.from
                (BaseUnspecifiedType.THE, name, inputType, evaluated, segSignatures);

            BaseType type = item.nearestType();

            do {
                methods = type.findMethod
                    (sig, m -> m.isStatic() == staticCall && m.isAccessibleVia(mScope.item()));

                if (!methods.isEmpty()) {
                    break findMethods;
                }

                if (type instanceof BaseClassTypeItem clazz) {
                    type = clazz.outerType();
                } else {
                    break;
                }
            } while (type != null);

            return null;
        }

        if (methods.size() > 1) {
            // FIXME: list them all
            error(nameToken, "method call is ambiguous");
            return null;
        }

        Set<CallableItem> set = methods.values().iterator().next();

        if (set.size() > 1) {
            // FIXME: list them all
            error(nameToken, "method call is ambiguous");
            return null;
        }

        var callable = (BaseCallableItem) set.iterator().next();

        if (!staticCall) {
            // Need a binding for the instance.
            var newBindings = new BaseBinding[1 + inputBindings.length];
            newBindings[0] = instance;
            System.arraycopy(inputBindings, 0, newBindings, 1, inputBindings.length);
            inputBindings = newBindings;
        }

        // FIXME: Convert the parameters if necessary (note: "this" param might have been
        // prepended). Also check if any arguments which are T_NULL_ALLOWED are mapped to
        // parameters which are T_NULL_DISALLOWED.

        BaseBlock block = mScope.activeBlock(st);

        if (direct | staticCall | callable.isPrivate() | segArguments != null) {
            return block.callDirect(target, callable, (Object[]) inputBindings, segArguments);
        } else {
            return block.callVirtual(target, callable, (Object[]) inputBindings);
        }
    }

    /**
     * @param callable can pass null if code isn't directly referenced by a callable (the code
     * is enclosed within a plain scope)
     */
    private void visitCode(CodeScopeStatement code, BaseCallableItem callable) {
        var newScope = new ModelScope(this, mScope, callable);

        if (callable != null) {
            // Parameters must be added before named local variables.
            newScope.addParameters(callable);
        }

        List<Statement> items = code.items;

        // Add all the symbols first, allowing them to be accessed in any order. The exception
        // is for declarations with an unspecified type. They cannot be accessed until the
        // DeclarationStatement assigns it a type and value.

        for (Statement item : items) {
            switch (item) {
                case LabeledStatement ls -> {
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

                case DeclarationStatement ds -> {
                    newScope.addDeclaration(ds, null);
                }

                // FIXME: MethodDefinitionStatement too

                default -> {
                }
            }
        }

        int size = items.size();

        enterScope(newScope);

        try {
            for (Statement st : items) {
                st.accept(this, null);
            }

            LabeledStatement ls = mScope.checkLabelReachability();

            if (ls != null) {
                error(ls, "unreachable");
            } else if (callable != null && mScope.isReachable()) {
                // The scope must end with a return statement.
                if (callable.isMacro() || callable.signature().outputType() != BaseVoidType.THE) {
                    error(code.end(), "missing return statement");
                }
            }
        } finally {
            exitScope();
        }
    }

    /**
     * @param forNew when true, the coordinates are for allocating a new array
     * @return null if an error was reported, or else the bindings; if forNew, then the number
     * of bindings returned might be fewer than the number of coordinates
     */
    private List<BaseBinding> visitCoordinates(List<Coordinate> coordinates, boolean forNew) {
        var bindings = new ArrayList<BaseBinding>(coordinates.size());
        boolean noMore = false;

        for (Coordinate c : coordinates) {
            List<Statement> items = c.items;

            if (items.size() != 1) {
                error(c, "only one " + (forNew ? "size" : "index") + "is supported");
                return null;
            }

            Statement coordinate = items.getFirst();

            if (coordinate == null) {
                if (forNew) {
                    if (bindings.isEmpty()) {
                        error(c, "array size is missing");
                        return null;
                    }
                    noMore = true;
                } else {
                    error(c, "array index is missing");
                    return null;
                }
            } else if (noMore) {
                error(c, "no more sizes can be specified");
                return null;
            } else {
                BaseBinding binding = coordinate.accept(this, null);

                if (binding == null) {
                    // Error state.
                    return null;
                }

                // FIXME: Support widening conversion.
                if (!(binding.type() instanceof BaseIntType)) {
                    error(coordinate, "not an int type");
                    return null;
                }

                bindings.add(binding);
            }
        }

        return bindings;
    }

    // Visit methods: Null is returned if an error was reported. Void is returned if the
    // statement returns void, and a non-void target binding is returned otherwise. If a null
    // target binding is passed in, then the visit method must provide a target binding on its
    // own if necessary. Otherwise, it should use the target binding already provided.

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
                    if (mScope.addDeclaration(ds, null) && ds.source != null) {
                        // FIXME: Code must be added to the constructor(s) or static initializer.
                        // If a simple final constant, then initialize the JVM field directly.
                        throw null;
                    }
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
    public BaseBinding visit(CodeScopeStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        visitCode(st, null);

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

        BaseBinding binding = st.source.accept(this, null);

        if (binding == null) {
            // Error state.
            return null;
        }

        List<BaseBinding> coordinateBindings = visitCoordinates(st.coordinates, false);

        if (coordinateBindings == null) {
            // Error state.
            return null;
        }

        Iterator<BaseBinding> it = coordinateBindings.iterator();

        for (Coordinate c : st.coordinates) {
            BaseType elementType = binding.type().arrayElementType();

            if (elementType == null) {
                error(st.source, "not an array type");
                return null;
            }

            BaseBinding indexBinding = it.next();
            BaseBinding getTarget = it.hasNext() ? null : target;

            binding = mScope.activeBlock(st).arrayGet(getTarget, binding, indexBinding);
        }

        return binding;
    }

    @Override
    public BaseBinding visit(DeclarationStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        // The variable should have beed defined earler by visitCode, unless the type is
        // unspecified. The local might also be null if an error was been reported.
        BaseBinding.Local local = mScope.tryFindLocalVariable(st.name.text);

        if (st.source == null) {
            if (local == null && st.type().isUnspecified()) {
                mEnv.error(st, "declaration with an unspecified type must be assigned a value");
            }
            return BaseBinding.Void.THE;
        }

        BaseBinding source = st.source.accept(this, target);

        if (source != null) {
            if (local == null) {
                if (st.type().isUnspecified()) {
                    // Add the declaration now, even if the source type is unspecified (for
                    // whatever reason).
                    mScope.addDeclaration(st, source.type());
                    local = mScope.tryFindLocalVariable(st.name.text);
                }
            }

            if (local != null) {
                // FIXME: type check or convert
                mScope.activeBlock(st).copy(local, source);
            }
        }

        return source;
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

        BaseBinding binding = st.source.accept(this, null);

        if (binding != null) {
            binding = followInstancePath(st, binding, false, List.of(st.name).listIterator());
        }

        return binding;
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

        BaseBinding binding = st.source.accept(this, null);

        if (binding == null) {
            // Error state.
            return null;
        }

        BaseType toType = st.type.tryResolve(env(), mScope.item());

        if (toType == null) {
            // Error state.
            return null;
        }

        BaseType fromType = binding.type();

        BaseBinding result;

        if (fromType.equals(toType) || toType.isJavaLangObject()) {
            result = BaseBinding.Constant.from(BaseBooleanType.THE, st.not == null);
        } else if (fromType.isPrimitive() || toType.isPrimitive()) {
            // FIXME: If primitive, check if wider, which always constant. Otherwise, perform a
            // narrowing conversion check. If constant, then no runtime check is needed. Use
            // ExactConversionsSupport.
            throw null;
        } else if (toType == BaseUnspecifiedType.THE) {
            result = BaseBinding.Constant.from(BaseBooleanType.THE, st.not != null);
        } else {
            var classClass = LoadedClass.classFrom(Class.class);
            var objectClass = LoadedClass.classFrom(Object.class);
            var clazzObj = BaseBinding.Constant.from(classClass, toType.asMakerType());
            // Note: The CodeGenerator sees that the class is a constant and uses the
            // instanceof instruction.
            var sig = BaseCallSignature.from
                (BaseBooleanType.THE, "isInstance", BaseTupleType.from(objectClass), true);
            var isInstance = classClass.method(sig);
            BaseBlock block = mScope.activeBlock(st);
            result = block.callVirtual(target, isInstance, clazzObj, binding);
            if (st.not != null) {
                result = block.not(null, result);
            }
        }

        if (target == null) {
            return result;
        }

        // FIXME: Automatic conversions when possible.

        mScope.activeBlock(st).copy(target, result);

        return target;
    }

    @Override
    public BaseBinding visit(JumpStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        String label = st.target.text;

        switch (st.keyword.text) {
            default -> {
                /* FIXME: Support break and continue statements.

                   The label search goes starts with the current scope and goes outward until
                   it finds a matching label or method name.

                   Break branches to a special block managed by ModelScope, which becomes the
                   active block when the scope is finished.

                   Continue branches to a special block managed by ModelScope, which is added
                   to the end when the scope is finished.

                 */

                error(st.keyword, "unsupported branch type");
            }

            case "goto" -> {
                BaseBlock block = mScope.findBlockForJump(label);
                if (block == null) {
                    error(st.target, "label not found");
                } else {
                    mScope.activeBlock(st).jump(block);
                }
            }
        }

        return BaseBinding.Void.THE;
    }

    @Override
    public BaseBinding visit(LabeledStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        if (!mScope.labelVisited(st)) {
            // This likely indicates a compiler bug.
            error(st.label, "label not declared");
        }

        return st.source.accept(this, null);
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

        boolean autoThis = false;
        ListIterator<Token.Identifier> pathIt = st.path.listIterator();

        BaseBinding instanceBinding = tryMatchFirstSymbol(pathIt);

        tryStatic: if (instanceBinding == null) {
            BaseClassTypeItem clazz = tryResolveClass(st, pathIt);

            if (clazz == null) {
                BaseBinding thisBinding = tryAccessThis();

                if (thisBinding != null) {
                    instanceBinding = thisBinding;
                    autoThis = true;
                    pathIt = st.path.listIterator();
                    break tryStatic;
                }

                error(st.path, "cannot find symbol");
                return null;
            }

            if (!pathIt.hasNext()) {
                error(st.path, "no field is specified");
                return null;
            }

            Token.Identifier nameToken = pathIt.next();
            BaseFieldItem fieldItem = findStaticField(clazz, nameToken);

            if (fieldItem == null) {
                // Error state.
                return null;
            }

            instanceBinding = BaseBinding.Static.from(fieldItem);
        }

        return followInstancePath(st, instanceBinding, autoThis, pathIt);
    }

    @Override
    public BaseBinding visit(MethodCallStatement st, BaseBinding target) {
        if (checkUnreachable(st)) {
            return null;
        }

        BaseBinding sourceBinding = null;

        if (st.source != null) {
            sourceBinding = st.source.accept(this, null);
            if (sourceBinding == null) {
                // Error state.
                return null;
            }
        }

        List<Statement> items = st.params.items;

        var inputTypes = new BaseType[items.size()];
        var inputBindings = new BaseBinding[inputTypes.length];

        int i = 0;
        for (Statement paramItem : items) {
            BaseBinding param = paramItem.accept(this, null);
            if (param == null) {
                // Error state.
                return null;
            }
            inputTypes[i] = param.type();
            inputBindings[i] = param;
            i++;
        }

        if (i != inputTypes.length) {
            throw new AssertionError();
        }

        boolean autoThis = false;
        BaseBinding instance;
        Token.Identifier nameToken;

        tryStatic: {
            if (st.path.size() == 1) {
                nameToken = st.path.getLast();

                if (sourceBinding != null) {
                    instance = sourceBinding;
                    break tryStatic;
                }

                int numErrors = mEnv.numErrors();

                BaseBinding result = tryMakeMethodCall
                    (st, target, mScope.item(), null, nameToken, inputTypes, inputBindings, false);

                if (result != null || numErrors != mEnv.numErrors()) {
                    return result;
                }

                BaseClassTypeItem classItem = mEnv.findImportedClassByMember(nameToken);

                if (classItem != null) {
                    numErrors = mEnv.numErrors();

                    result = tryMakeMethodCall
                        (st, target, classItem, null, nameToken, inputTypes, inputBindings, false);

                    if (result != null || numErrors != mEnv.numErrors()) {
                        return result;
                    }
                }

                BaseBinding thisBinding = tryAccessThis();

                if (thisBinding != null) {
                    autoThis = true;
                    instance = thisBinding;
                    break tryStatic;
                }

                // FIXME: If a matching instance method exists, report a better error?
                error(st.path, "cannot find symbol");
                return null;
            }

            if (sourceBinding != null) {
                throw new AssertionError();
            }

            ListIterator<Token.Identifier> pathIt = st.path.listIterator();

            BaseBinding firstBinding = tryMatchFirstSymbol(pathIt);

            if (firstBinding != null) {
                instance = firstBinding;
            } else {
                BaseClassTypeItem classItem = tryResolveClass(st, pathIt);

                if (classItem == null) {
                    error(st.path, "cannot find symbol");
                    return null;
                }

                if (!pathIt.hasNext()) {
                    error(st.path, "no method is specified");
                    return null;
                }

                nameToken = pathIt.next();

                if (!pathIt.hasNext()) {
                    int numErrors = mEnv.numErrors();

                    BaseBinding result = tryMakeMethodCall
                        (st, target, classItem, null, nameToken, inputTypes, inputBindings, false);

                    if (result == null && numErrors == mEnv.numErrors()) {
                        error(nameToken, "cannot find static method");
                    }

                    return result;
                }

                BaseFieldItem fieldItem = findStaticField(classItem, nameToken);

                if (fieldItem == null) {
                    // Error state.
                    return null;
                }

                BaseBinding fieldBinding = BaseBinding.Static.from(fieldItem);

                instance = followInstancePath(st, fieldBinding, false, pathIt);

                if (instance == null) {
                    // Error state.
                    return null;
                }
            }

            nameToken = pathIt.next();
        }

        int numErrors = mEnv.numErrors();

        BaseBinding result = tryMakeMethodCall
            (st, target, null, instance, nameToken, inputTypes, inputBindings, false);

        if (result == null && numErrors == mEnv.numErrors()) {
            String message;
            if (autoThis) {
                message = "cannot find symbol";
            } else {
                message = "cannot find instance method";
            }
            error(nameToken, message);
        }

        return result;
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

        BaseBinding inputBinding = st.source.accept(this, null);

        if (inputBinding == null) {
            // Error state.
            return null;
        }

        BaseBlock block = mScope.activeBlock(st);

        switch (st.operator.type()) {
            default -> {
                error(st.operator, "unsupported prefix operator");
                return null;
            }

            case T_PLUS  -> {return inputBinding;}
            case T_MINUS -> {return block.neg(target, inputBinding);}
            case T_TILDE -> {return block.com(target, inputBinding);}
            case T_BANG  -> {return block.not(target, inputBinding);}

            // FIXME: T_INC, T_DEC; LoadStatement, FieldLoadStatement, CoordinateLoadStatement
        }
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
