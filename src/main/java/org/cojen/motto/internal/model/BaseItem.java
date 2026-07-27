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

import org.cojen.motto.model.ClassTypeItem;
import org.cojen.motto.model.Item;
import org.cojen.motto.model.Type;

import org.cojen.maker.Maker;

import static org.cojen.motto.internal.model.Modifiers.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class BaseItem implements Item
    permits BaseClassTypeItem, TheCallableItem, TheFieldItem
{
    private final int mModifierBits;

    /**
     * @see Modifiers
     */
    BaseItem(int modifierBits) {
        mModifierBits = modifierBits;
    }

    public final int modifierBits() {
        return mModifierBits;
    }

    @Override
    public abstract BaseType enclosingType();

    @Override
    public abstract BaseClassTypeItem enclosingClass();

    @Override
    public final boolean isStatic() {
        return (mModifierBits & STATIC) != 0;
    }

    @Override
    public final boolean isFinal() {
        return (mModifierBits & FINAL) != 0;
    }

    @Override
    public final boolean isPrivate() {
        return (mModifierBits & (PUBLIC | INTERNAL | PROTECTED)) == 0;
    }

    public final boolean isBridge() {
        return (mModifierBits & BRIDGE) != 0;
    }

    public final boolean isPseudo() {
        return (mModifierBits & PSEUDO) != 0;
    }

    public final boolean isMacro() {
        return (mModifierBits & MACRO) != 0;
    }

    @Override
    public boolean isAccessibleVia(Item via) {
        int modifierBits = mModifierBits;

        if ((modifierBits & PUBLIC) != 0) {
            // FIXME: All parents must be public too. Ignore inheritance stuff. A public method
            // defined by an interface implemented in an internal class isn't public.
            return true;
        }

        if (via == null) {
            return false;
        }

        BaseType thisType = enclosingType();
        Type viaType = via.enclosingType();

        if (thisType.equals(viaType)) {
            return true;
        }

        if (thisType instanceof BaseClassTypeItem thisClass &&
            viaType instanceof ClassTypeItem viaClass)
        {
            // FIXME: check if in the same source file

            if ((modifierBits & (INTERNAL | PROTECTED)) != 0 &&
                thisClass.packagePath().equals(viaClass.packagePath()))
            {
                return true;
            }

            if (thisClass.nestType().equals(viaClass.nestType())) {
                return true;
            }

            if ((modifierBits & PROTECTED) != 0) {
                return thisClass.isAssignableFrom(viaClass);
            }
        }

        return false;
    }

    void applyModifiers(Maker maker) {
        int modifiers = modifierBits();

        if ((modifiers & Modifiers.PUBLIC) != 0) {
            maker.public_();
        } else if ((modifiers & Modifiers.INTERNAL) != 0) {
            // Nothing to do.
        } else if ((modifiers & Modifiers.PROTECTED) != 0) {
            maker.protected_();
        } else {
            maker.private_();
        }

        if ((modifiers & Modifiers.STATIC) != 0) {
            maker.static_();
        }

        if ((modifiers & Modifiers.FINAL) != 0) {
            maker.final_();
        }

        if ((modifiers & Modifiers.SYNTHETIC) != 0) {
            maker.synthetic();
        }
    }
}
