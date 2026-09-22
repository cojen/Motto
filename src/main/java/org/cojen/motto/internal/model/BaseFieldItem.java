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

import org.cojen.maker.FieldMaker;

import org.cojen.motto.model.FieldItem;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class BaseFieldItem extends BaseItem implements FieldItem {
    private final BaseClassTypeItem mEnclosingClass;
    private final BaseType mType;
    private final String mName;

    /**
     * @see Modifiers
     */
    BaseFieldItem(int modifierBits, BaseClassTypeItem enclosingClass, BaseType type, String name) {
        super(modifierBits);
        mEnclosingClass = Objects.requireNonNull(enclosingClass);
        mType = Objects.requireNonNull(type);
        mName = Objects.requireNonNull(name);
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
    public BaseType type() {
        return mType;
    }

    @Override
    public String name() {
        return mName;
    }

    void applyModifiers(FieldMaker fm) {
        super.applyModifiers(fm);

        int modifiers = modifierBits();

        if ((modifiers & Modifiers.VOLATILE) != 0) {
            fm.volatile_();
        }

        if ((modifiers & Modifiers.TRANSIENT) != 0) {
            fm.transient_();
        }
    }
}
