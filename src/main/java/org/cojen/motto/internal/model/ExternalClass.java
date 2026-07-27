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

import org.cojen.maker.ClassMaker;
import org.cojen.maker.FieldMaker;
import org.cojen.maker.Maker;
import org.cojen.maker.MethodMaker;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class ExternalClass extends BaseClassTypeItem
    implements org.cojen.maker.Type.Provider
{
    ExternalClass(int modifierBits) {
        super(modifierBits);
    }

    @Override
    public org.cojen.maker.Type asMakerType() {
        return org.cojen.maker.Type.external(fullMangledName(), this);
    }

    @Override // Type.Provider
    public void init(ClassMaker cm) {
        applyModifiers(cm);

        BaseClassTypeItem superType = superType();

        if (superType != null) {
            cm.extend(superType.asMakerType());
        }

        for (BaseClassTypeItem iface : interfaces()) {
            cm.implement(iface.asMakerType());
        }
    }

    @Override // Type.Provider
    public void addFields(ClassMaker cm) {
        fields().filter(f -> !f.isPseudo()).forEach(field -> {
            FieldMaker fm = cm.addField(field.type().asMakerType(), Maker.mangle(field.name()));
            field.applyModifiers(fm);
        });
    }

    @Override // Type.Provider
    public void addMethods(ClassMaker cm) {
        methods().filter(m -> !m.isPseudo()).forEach(method -> {
            // FIXME
        });
    }

    @Override // Type.Provider
    public void addConstructors(ClassMaker cm) {
        constructors().filter(c -> !c.isPseudo()).forEach(ctor -> {
            /* FIXME
            MethodMaker mm = cm.addConstructor(makerParamsFor(ctor, ctor.signature(), cm));
            ctor.applyModifiers(mm);
            */
        });
    }

    /* FIXME
    private static Object[] makerParamsFor(TheCallableItem item, BaseCallSignature sig,
                                           ClassMaker cm)
    {
        BaseTupleType inputType = sig.inputType();
        int numElements = inputType.numElements();

        Object[] params;
        int offset;

        if (item.isStatic()) {
            params = new Object[numElements];
            offset = 0;
        } else {
            // Drop the implicit "this" parameter.
            params = new Object[numElements - 1];
            offset = 1;
        }

        for (int ix = offset; ix < numElements; ix++) {
            params[ix - offset] = inputType.elementType(ix).asMakerType();
        }

        return params;
    }
    */
}
