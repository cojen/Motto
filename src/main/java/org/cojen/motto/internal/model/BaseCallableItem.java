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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.util.Objects;

import org.cojen.maker.MethodMaker;

import org.cojen.motto.model.CallableItem;
import org.cojen.motto.model.Code;
import org.cojen.motto.model.Item;

import org.cojen.motto.internal.compiler.CompilationEnv;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed class BaseCallableItem extends BaseItem implements CallableItem {
    public static BaseCallableItem from(int modifierBits, BaseClassTypeItem enclosingClass,
                                        BaseCallSignature signature)
    {
        return (modifierBits & Modifiers.MACRO) == 0
            ? new BaseCallableItem(modifierBits, enclosingClass, signature)
            : new BaseCallableItem.Macro(modifierBits, enclosingClass, signature);
    }

    private final BaseClassTypeItem mEnclosingClass;
    private final BaseCallSignature mSignature;

    private CapturedSet mCapturedSet;

    private BaseBlock mCode;

    /**
     * @see Modifiers
     */
    private BaseCallableItem(int modifierBits, BaseClassTypeItem enclosingClass,
                             BaseCallSignature signature)
    {
        super(modifierBits);
        mEnclosingClass = Objects.requireNonNull(enclosingClass);
        mSignature = Objects.requireNonNull(signature);
    }

    @Override
    public BaseClassTypeItem enclosingType() {
        return mEnclosingClass;
    }

    @Override
    public BaseClassTypeItem nearestType() {
        return mEnclosingClass;
    }

    @Override
    public BaseClassTypeItem nearestClass() {
        return mEnclosingClass;
    }

    @Override
    public final boolean isAccessibleVia(Item via) {
        return super.isAccessibleVia(via)
            && mSignature.outputType().isAccessibleVia(via)
            && mSignature.inputType().isAccessibleVia(via);
    }

    public BaseCallSignature signature() {
        return mSignature;
    }

    /**
     * @see BaseCallSignature#forMacro
     */
    public BaseCallSignature macroSignature() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns this signature if not a macro, or else returns the macro signature.
     */
    public BaseCallSignature implSignature() {
        return mSignature;
    }

    void setMacroImpl(String className, String methodName) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return MethodHandle with a macro method signature
     * @see BaseCallSignature#forMacro
     */
    public MethodHandle findMacroImpl(CompilationEnv env) {
        throw new UnsupportedOperationException();
    }

    /**
     * Calls the namesMatch method against the signature output and input types. True is
     * returned when all calls return true.
     *
     * @param otherSig for an instance call, don't supply the implicit "this" parameter
     */
    boolean typeNamesMatch(BaseCallSignature otherSig) {
        return mSignature.outputType().namesMatch(0, otherSig.outputType())
            && mSignature.inputType().namesMatch(isStatic() ? 0 : 1, otherSig.inputType());
    }

    /**
     * Capture a local variable of the given type and name.
     *
     * @throws IllegalStateException if already captured and the type doesn't match
     */
    public final BaseBinding.Captured capture(BaseType type, String name, NewLocalClass usedBy) {
        var cs = mCapturedSet;

        if (cs == null) {
            mCapturedSet = cs = new CapturedSet(this);
        }

        return cs.capture(type, name, usedBy);
    }

    public final void assignCode(BaseBlock code) {
        mCode = code;
    }

    public final BaseBlock code() {
        return mCode;
    }

    void applyModifiers(MethodMaker mm) {
        super.applyModifiers(mm);

        int modifiers = modifierBits();

        if ((modifiers & Modifiers.SYNCHRONIZED) != 0) {
            mm.synchronized_();
        }

        if ((modifiers & Modifiers.ABSTRACT) != 0) {
            mm.abstract_();
        }

        if ((modifiers & Modifiers.NATIVE) != 0) {
            mm.native_();
        }

        if ((modifiers & Modifiers.BRIDGE) != 0) {
            mm.bridge();
        }
    }

    public static final class Macro extends BaseCallableItem {
        private final BaseCallSignature mMacroSignature;

        private String mImplClassName, mImplMethodName;

        private CompilationEnv mMacroEnv;
        private MethodHandle mMacroHandle;

        private Macro(int modifierBits, BaseClassTypeItem enclosingClass,
                      BaseCallSignature signature)
        {
            super(modifierBits, enclosingClass, signature);
            mMacroSignature = signature.forMacro();
        }

        /**
         * @see BaseCallSignature#forMacro
         */
        @Override
        public BaseCallSignature macroSignature() {
            return mMacroSignature;
        }

        @Override
        public BaseCallSignature implSignature() {
            return mMacroSignature;
        }

        @Override
        synchronized void setMacroImpl(String className, String methodName) {
            mImplClassName = className;
            mImplMethodName = methodName;
        }

        @Override
        public synchronized MethodHandle findMacroImpl(CompilationEnv env) {
            if (env == mMacroEnv && mMacroHandle != null) {
                return mMacroHandle;
            }

            mMacroEnv = null;
            mMacroHandle = null;

            if (mImplClassName == null || mImplMethodName == null) {
                return null;
            }

            Class<?> clazz;

            try {
                clazz = env.macroClassLoader().loadClass(mImplClassName);
            } catch (ClassNotFoundException e) {
                return null;
            }

            BaseTupleType inputType = mMacroSignature.inputType();
            var paramTypes = new Class[inputType.numFields()];

            for (int i=0; i<paramTypes.length; i++) {
                org.cojen.maker.Type type = inputType.fieldType(i).asMakerType();
                Class<?> typeClass = type.classType();
                if (typeClass == null) {
                    try {
                        typeClass = Class.forName(type.name());
                    } catch (ClassNotFoundException e) {
                        env.uncaught(e);
                        return null;
                    }
                }
                paramTypes[i] = typeClass;
            }

            MethodType mt = MethodType.methodType(Code.class, paramTypes);

            MethodHandle mh;

            try {
                mh = MethodHandles.publicLookup().findStatic(clazz, mImplMethodName, mt);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                env.uncaught(e);
                return null;
            }

            mMacroEnv = env;
            mMacroHandle = mh;

            return mh;
        }
    }
}
