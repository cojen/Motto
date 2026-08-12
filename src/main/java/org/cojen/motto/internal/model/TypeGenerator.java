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

// FIXME: Use the classfile API to avoid the runtime dependency.
import org.cojen.maker.ClassMaker;
import org.cojen.maker.MethodMaker;

// Generated classes are in the "motto" package. If this changes, update GENERATED_PREFIX.
import static motto.Lookup.LOOKUP;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class TypeGenerator {
    private TypeGenerator() {
    }

    /**
     * @param name generated class name with a slash separator
     */
    public static Class<?> generateFromName(String name) {
        String prefix = EncodableType.GENERATED_PREFIX;
        if (!name.startsWith(prefix + '/')) {
            throw new IllegalArgumentException();
        }
        return generateFromEncoded(name.substring(prefix.length() + 1));
    }

    /**
     * @param encoded base-64 string created by TypeEncoder
     */
    public static Class<?> generateFromEncoded(String encoded) {
        String className = EncodableType.GENERATED_PREFIX + '.' + encoded;

        try {
            try {
                return LOOKUP.findClass(className);
            } catch (ClassNotFoundException e) {
            }

            DecodedType type = new TypeDecoder().tryDecode(encoded);

            byte[] bytes;

            switch (type) {
                case DecodedType.TupleT tt -> {
                    bytes = makeTupleClass(className, tt);
                }
                case DecodedType.FunctionT lt -> {
                    bytes = makeFunctionClass(className, lt);
                }
                default -> {
                    throw new IllegalArgumentException();
                }
            }

            try {
                return LOOKUP.defineClass(bytes);
            } catch (LinkageError e) {
                // Check again if already defined.
                try {
                    return LOOKUP.findClass(className);
                } catch (ClassNotFoundException e2) {
                    throw e;
                }
            }
        } catch (IllegalAccessException e) {
            var error = new IllegalAccessError();
            error.initCause(e);
            throw error;
        }
    }

    private static byte[] makeTupleClass(String className, DecodedType.TupleT type) {
        int num = type.numFields();

        if (num == 1 && type.fieldName(0) == null) {
            // A tuple which wraps a single unnamed field isn't useful, and the code generator
            // shouldn't use it. The code generator should erase the tuple type or transform it
            // to something else. If the field is a primitive type, it should be transformed to
            // the corresponding boxed type.
            throw new IllegalArgumentException();
        }

        ClassMaker cm = ClassMaker.beginExternal(className).public_().final_().synthetic();

        // Tuples cannot be reliably serialized because the class might not have been loaded
        // yet by the time it's read from an ObjectInputStream.
        //cm.implement(Serializable.class);

        for (int i=0; i<num; i++) {
            cm.addField(type.fieldType(i).asClassDesc(), type.mangledFieldName(i))
                .private_().final_();
        }

        // FIXME: Define a toString method which doesn't emit the long class name. The string
        // should match the tuple source code syntax. Unnamed elements should have no labels.

        // FIXME: If all elements are comparable, then the tuple should be comparable too.

        // FIXME: Should be a value record (JEP 401).
        MethodMaker ctor = cm.asRecord();

        if (num == 0) {
            // Define an empty singleton.
            ctor.private_();
            String instanceName = "\\=_";
            cm.addField(cm, instanceName).public_().static_().final_();
            MethodMaker clinit = cm.addClinit();
            clinit.field(instanceName).set(clinit.new_(cm));
        }

        return cm.finishBytes();
    }

    private static byte[] makeFunctionClass(String className, DecodedType.FunctionT type) {
        ClassMaker cm = ClassMaker.beginExternal(className).public_().interface_().synthetic();

        // Not required, so don't make the class bigger than it needs to be.
        //cm.addAnnotation(FunctionalInterface.class, true);

        DecodedType inputType = type.inputType();
        Object[] inputTypes;

        if (inputType instanceof DecodedType.TupleT tt) {
            inputTypes = new Object[tt.numFields()];
            for (int i=0; i<inputTypes.length; i++) {
                inputTypes[i] = tt.fieldType(i).asClassDesc();
            }
        } else {
            inputTypes = new Object[] {inputType.asClassDesc()};
        }

        MethodMaker mm = cm.addMethod(type.outputType().asClassDesc(), "apply", inputTypes);
        mm.public_().abstract_();

        return cm.finishBytes();
    }
}
