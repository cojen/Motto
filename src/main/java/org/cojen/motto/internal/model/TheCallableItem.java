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

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class TheCallableItem extends BaseItem implements CallableItem {
    private final BaseClassTypeItem mEnclosingClass;

    /**
     * @see Modifiers
     */
    TheCallableItem(int modifierBits, BaseClassTypeItem enclosingClass) {
        super(modifierBits);
        mEnclosingClass = Objects.requireNonNull(enclosingClass);
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
    public org.cojen.motto.model.CallSignature signature() {
        // FIXME
        throw null;
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
}
