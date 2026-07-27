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

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;

import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.InnerClassInfo;

import java.lang.reflect.Member;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class Modifiers {
    public static final int PUBLIC = 0x0001, INTERNAL = 0x0002, PROTECTED = 0x0004,
        STATIC = 0x0008, FINAL = 0x0010, SYNCHRONIZED = 0x0020, VOLATILE = 0x0040, BRIDGE = 0x0040,
        TRANSIENT = 0x0080, VARARGS = 0x0080, NATIVE = 0x0100, INTERFACE = 0x0200,
        ABSTRACT = 0x0400, STRICT = 0x0800, SYNTHETIC = 0x1000, ANNOTATION = 0x2000, ENUM = 0x4000,
        MODULE = 0x8000;

    static final int LOADED = 0x8000_0000, // see ExternalClass
        PSEUDO = 0x4000_0000, CLASS = 0x2000_0000, MACRO = 0x1000_0000;

    public static int from(ClassModel model) {
        int modifiers = adjustModifiers(model.flags().flagsMask());

        if ((modifiers & INTERFACE) == 0) {
            modifiers |= CLASS;
        }

        InnerClassesAttribute attr  = model.findAttribute(Attributes.innerClasses()).orElse(null);

        if (attr != null) {
            for (InnerClassInfo info : attr.classes()) {
                if (!info.outerClass().isEmpty() && info.innerClass().equals(model.thisClass())) {
                    modifiers |= adjustModifiers(info.flagsMask());
                    break;
                }
            }
        }

        return modifiers;
    }

    public static int from(FieldModel model) {
        return adjustModifiers(model.flags().flagsMask());
    }

    public static int from(MethodModel model) {
        return adjustModifiers(model.flags().flagsMask());
    }

    public static int from(Class<?> clazz) {
        return adjustModifiers(clazz.getModifiers());
    }

    public static int from(Member member) {
        return adjustModifiers(member.getModifiers());
    }

    /**
     * Converts modifiers from JVM format to/from this format.
     */
    static int adjustModifiers(int modifiers) {
        // Java class files use 0x0002 for PRIVATE. Flip things if necessary.

        if ((modifiers & (PUBLIC | PROTECTED | INTERNAL)) == 0) {
            // package-private --> internal
            modifiers |= INTERNAL;
        } else if ((modifiers & INTERNAL) != 0) {
            // private --> implicitly private
            modifiers &= ~(PUBLIC | INTERNAL | PROTECTED);
        }

        return modifiers;
    }
}
