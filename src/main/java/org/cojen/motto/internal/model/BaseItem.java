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

import java.util.LinkedHashMap;
import java.util.Map;

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
    permits BaseClassTypeItem, BaseCallableItem, BaseFieldItem, BaseScopeItem
{
    private int mModifierBits;

    private Map<String, NewLocalClass> mLocalInnerClasses;

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
        return Modifiers.isPrivate(modifierBits());
    }

    public final boolean isBridge() {
        return (modifierBits() & BRIDGE) != 0;
    }

    public final boolean isMacro() {
        return (modifierBits() & MACRO) != 0;
    }

    @Override
    public boolean isAccessibleVia(Item via) {
        int modifierBits = modifierBits();

        if (Modifiers.isPublic(modifierBits)) {
            // FIXME: All parents must be public too. Ignore inheritance stuff. A public method
            // defined by an interface implemented in an internal class isn't public.
            return true;
        }

        if (via == null) {
            return false;
        }

        BaseType thisType = nearestType();
        Type viaType = via.nearestType();

        if (thisType.isEquivalentTo(viaType)) {
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

            if (!Modifiers.isPrivate(modifierBits) &&
                thisClass.packagePath().equals(viaClass.packagePath()))
            {
                return true;
            }

            if (thisClass.nestType().isEquivalentTo(viaClass.nestType())) {
                return true;
            }

            if (Modifiers.isProtected(modifierBits)) {
                return thisClass.isAssignableFrom(viaClass);
            }
        }

        return false;
    }

    void applyModifiers(Maker maker) {
        int modifiers = modifierBits();

        if ((modifiers & Modifiers.PUBLIC) != 0) {
            maker.public_();
        } else if ((modifiers & Modifiers.PRIVATE) != 0) {
            maker.private_();
        } else if ((modifiers & Modifiers.PROTECTED) != 0) {
            maker.protected_();
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

    /**
     * @param name pass null if class is anonymous
     * @return null if an inner class with the same name exists
     * @throws UnsupportedOperationException if not supported by this item
     */
    public final NewLocalClass tryAddLocalInnerClass(int modifierBits, final String name) {
        String methodName = captureMethodName(this);

        if (!(nearestClass() instanceof NewClass outer)) {
            throw new UnsupportedOperationException();
        }

        Map<String, NewLocalClass> localMap = mLocalInnerClasses;

        if (localMap == null) {
            mLocalInnerClasses = localMap = new LinkedHashMap<>();
        } else if (name != null && localMap.containsKey(name)) {
            return null;
        }

        // Note: Don't use '$' separator, since it can be interpreted as a new scope.
        String baseName = name == null ? methodName : (methodName + '_' + name);

        Map<String, BaseClassTypeItem> outerMap = outer.innerClassesMap();

        String actualName = baseName;
        int num = 0;

        if (outerMap.containsKey(actualName)) {
            while (true) {
                actualName = baseName + '_' + num;
                if (!outerMap.containsKey(actualName)) {
                    break;
                }
                num++;
            }
        }

        NewLocalClass local;

        while (true) {
            local = new NewLocalClass(outer.env(), outer, modifierBits, outer.packagePath(),
                                      outer.namePath().append(actualName), outer.origin());

            if (outer.tryAddInnerClass(local)) {
                break;
            }

            actualName = baseName + '_' + (++num);
        }

        if (name != null) {
            localMap.put(name, local);
        }

        return local;
    }

    /**
     * @throws UnsupportedOperationException if local inner classes aren't supported
     */
    private static final String captureMethodName(BaseItem item) {
        while (true) {
            if (item instanceof BaseCallableItem callable) {
                return callable.signature().name();
            }
            if (item instanceof BaseScopeItem scope) {
                item = scope.enclosingItem();
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    public final NewLocalClass tryFindLocalInnerClass(String name) {
        Map<String, NewLocalClass> localMap = mLocalInnerClasses;
        return localMap == null ? null : localMap.get(name);
    }
}
