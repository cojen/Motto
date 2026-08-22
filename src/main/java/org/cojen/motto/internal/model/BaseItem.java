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

import org.cojen.motto.internal.compiler.CompilationEnv;

import static org.cojen.motto.internal.model.Modifiers.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class BaseItem implements Item
    permits BaseClassTypeItem, BaseCallableItem, BaseFieldItem
{
    private int mModifierBits;

    /**
     * @see Modifiers
     */
    BaseItem(int modifierBits) {
        mModifierBits = modifierBits;
    }

    public int modifierBits() {
        return mModifierBits;
    }

    protected final void setModifierBits(int modifierBits) {
        mModifierBits = modifierBits;
    }

    @Override
    public abstract BaseType enclosingType();

    @Override
    public abstract BaseType nearestType();

    @Override
    public abstract BaseClassTypeItem nearestClass();

    @Override
    public final boolean isStatic() {
        return (modifierBits() & STATIC) != 0;
    }

    @Override
    public final boolean isFinal() {
        return (modifierBits() & FINAL) != 0;
    }

    @Override
    public final boolean isPrivate() {
        return (modifierBits() & (PUBLIC | INTERNAL | PROTECTED)) == 0;
    }

    public final boolean isBridge() {
        return (modifierBits() & BRIDGE) != 0;
    }

    public final boolean isPseudo() {
        return (modifierBits() & PSEUDO) != 0;
    }

    public final boolean isMacro() {
        return (modifierBits() & MACRO) != 0;
    }

    @Override
    public boolean isAccessibleVia(Item via) {
        int modifierBits = modifierBits();

        if ((modifierBits & PUBLIC) != 0) {
            // FIXME: All parents must be public too. Ignore inheritance stuff. A public method
            // defined by an interface implemented in an internal class isn't public.
            return true;
        }

        if (via == null) {
            return false;
        }

        BaseType thisType = nearestType();
        Type viaType = via.nearestType();

        if (thisType.equals(viaType)) {
            return true;
        }

        if (thisType instanceof BaseClassTypeItem thisClass &&
            viaType instanceof BaseClassTypeItem viaClass)
        {
            CompilationEnv thisEnv = thisClass.env();
            if (thisEnv != null && thisEnv == viaClass.env()) {
                // In the same source file.
                return true;
            }

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
