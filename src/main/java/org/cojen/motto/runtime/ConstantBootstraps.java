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

package org.cojen.motto.runtime;

import java.lang.constant.ClassDesc;

import java.lang.invoke.MethodHandles;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.cojen.motto.model.Type;

import org.cojen.motto.internal.model.BasePrimitiveType;
import org.cojen.motto.internal.model.BaseType;
import org.cojen.motto.internal.model.EncodableType;
import org.cojen.motto.internal.model.TypeGenerator;

import org.cojen.motto.internal.util.Canonicalizer;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class ConstantBootstraps {
    private static final Canonicalizer mPool;

    static {
        mPool = new Canonicalizer();

        mPool.apply(BigDecimal.ZERO);
        mPool.apply(BigDecimal.ONE);
        mPool.apply(BigDecimal.TWO);
        mPool.apply(BigDecimal.TEN);

        mPool.apply(BigInteger.ZERO);
        mPool.apply(BigInteger.ONE);
        mPool.apply(BigInteger.TWO);
        mPool.apply(BigInteger.TEN);
    }

    private ConstantBootstraps() {
    }

    /* FIXME: remove
    public static Type type(MethodHandles.Lookup lookup, String name, Class<?> unused, String desc)
        throws ReflectiveOperationException
    {
        if (desc.length() == 1) {
            Type type = BasePrimitiveType.trySelectByDescriptor(desc);
            if (type != null) {
                return type;
            }
        }

        if (desc.length() == 0) {
            throw new IllegalArgumentException();
        }

        if (desc.charAt(0) == '[') {
            return type(lookup, name, unused, desc.substring(1)).asArray();
        }

        Class<?> clazz;

        String prefix = EncodableType.GENERATED_PREFIX;

        find: {
            if (desc.startsWith('L' + prefix + '/') && desc.endsWith(";")) {
                String encoded = desc.substring(prefix.length() + 2, desc.length() - 1);
                try {
                    clazz = TypeGenerator.generateFromEncoded(encoded);
                    break find;
                } catch (RuntimeException e) {
                    // Assume it's not actually a generated type.
                }
            }

            clazz = ClassDesc.ofDescriptor(desc).resolveConstantDesc(lookup);
        }

        return BaseType.from(clazz);
    }
    */

    public static BigDecimal bigDecimal(MethodHandles.Lookup lookup, String name,
                                        Class<? extends BigDecimal> type, long value)
    {
        return mPool.apply(BigDecimal.valueOf(value));
    }

    public static BigDecimal bigDecimal(MethodHandles.Lookup lookup, String name,
                                        Class<? extends BigDecimal> type, double value)
    {
        return mPool.apply(BigDecimal.valueOf(value));
    }

    public static BigDecimal bigDecimal(MethodHandles.Lookup lookup, String name,
                                        Class<? extends BigDecimal> type, String value)
    {
        return mPool.apply(new BigDecimal(value));
    }

    public static BigDecimal bigDecimal(MethodHandles.Lookup lookup, String name,
                                        Class<? extends BigDecimal> type, BigInteger value)
    {
        return mPool.apply(new BigDecimal(value));
    }

    public static BigInteger bigInteger(MethodHandles.Lookup lookup, String name,
                                        Class<? extends BigInteger> type, long value)
    {
        return mPool.apply(BigInteger.valueOf(value));
    }

    public static BigInteger bigInteger(MethodHandles.Lookup lookup, String name,
                                        Class<? extends BigInteger> type, String value)
    {
        return mPool.apply(new BigInteger(value));
    }
}
