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

import java.util.Objects;

import org.cojen.maker.MethodMaker;

import org.cojen.motto.model.CallableItem;
import org.cojen.motto.model.Item;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed class BaseCallableItem extends BaseItem implements CallableItem {
    public static BaseCallableItem from(int modifierBits, BaseClassTypeItem enclosingClass,
                                        TheCallSignature signature)
    {
        return (modifierBits & Modifiers.MACRO) == 0
            ? new BaseCallableItem(modifierBits, enclosingClass, signature)
            : new BaseCallableItem.Macro(modifierBits, enclosingClass, signature);
    }

    private final BaseClassTypeItem mEnclosingClass;
    private final TheCallSignature mSignature;

    /**
     * @see Modifiers
     */
    private BaseCallableItem(int modifierBits, BaseClassTypeItem enclosingClass,
                             TheCallSignature signature)
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
    public BaseClassTypeItem enclosingClass() {
        return mEnclosingClass;
    }

    @Override
    public final boolean isAccessibleVia(Item via) {
        return super.isAccessibleVia(via)
            && mSignature.outputType().isAccessibleVia(via)
            && mSignature.inputType().isAccessibleVia(via);
    }

    public TheCallSignature signature() {
        return mSignature;
    }

    /**
     * @see CallSignature#forMacro
     */
    public TheCallSignature macroSignature() {
        throw new UnsupportedOperationException();
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
        private final TheCallSignature mMacroSignature;

        private Macro(int modifierBits, BaseClassTypeItem enclosingClass,
                      TheCallSignature signature)
        {
            super(modifierBits, enclosingClass, signature);
            mMacroSignature = signature.forMacro();
        }

        /**
         * @see CallSignature#forMacro
         */
        @Override
        public TheCallSignature macroSignature() {
            return mMacroSignature;
        }
    }
}
