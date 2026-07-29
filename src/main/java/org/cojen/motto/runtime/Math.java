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

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class Math {
    private Math() {
    }

    // Note: These methods are treated as intrinsic by CodeGenerator class, and so they won't
    // actually be invoked.

    public static char add(char a, char b) {
        return (char) (a + b);
    }

    public static byte add(byte a, byte b) {
        return (byte) (a + b);
    }

    public static short add(short a, short b) {
        return (short) (a + b);
    }

    public static int add(int a, int b) {
        return a + b;
    }

    public static long add(long a, long b) {
        return a + b;
    }

    public static float add(float a, float b) {
        return a + b;
    }

    public static double add(double a, double b) {
        return a + b;
    }

    public static char sub(char a, char b) {
        return (char) (a - b);
    }

    public static byte sub(byte a, byte b) {
        return (byte) (a - b);
    }

    public static short sub(short a, short b) {
        return (short) (a - b);
    }

    public static int sub(int a, int b) {
        return a - b;
    }

    public static long sub(long a, long b) {
        return a - b;
    }

    public static float sub(float a, float b) {
        return a - b;
    }

    public static double sub(double a, double b) {
        return a - b;
    }

    public static char mul(char a, char b) {
        return (char) (a * b);
    }

    public static byte mul(byte a, byte b) {
        return (byte) (a * b);
    }

    public static short mul(short a, short b) {
        return (short) (a * b);
    }

    public static int mul(int a, int b) {
        return a * b;
    }

    public static long mul(long a, long b) {
        return a * b;
    }

    public static float mul(float a, float b) {
        return a * b;
    }

    public static double mul(double a, double b) {
        return a * b;
    }

    public static char div(char a, char b) {
        return (char) (a / b);
    }

    public static byte div(byte a, byte b) {
        return (byte) (a / b);
    }

    public static short div(short a, short b) {
        return (short) (a / b);
    }

    public static int div(int a, int b) {
        return a / b;
    }

    public static long div(long a, long b) {
        return a / b;
    }

    public static float div(float a, float b) {
        return a / b;
    }

    public static double div(double a, double b) {
        return a / b;
    }

    public static char rem(char a, char b) {
        return (char) (a % b);
    }

    public static byte rem(byte a, byte b) {
        return (byte) (a % b);
    }

    public static short rem(short a, short b) {
        return (short) (a % b);
    }

    public static int rem(int a, int b) {
        return a % b;
    }

    public static long rem(long a, long b) {
        return a % b;
    }

    public static float rem(float a, float b) {
        return a % b;
    }

    public static double rem(double a, double b) {
        return a % b;
    }

    public static char shl(char a, char b) {
        return (char) (a << b);
    }

    public static byte shl(byte a, byte b) {
        return (byte) (a << b);
    }

    public static short shl(short a, short b) {
        return (short) (a << b);
    }

    public static int shl(int a, int b) {
        return a << b;
    }

    public static long shl(long a, long b) {
        return a << b;
    }

    public static char shr(char a, char b) {
        return (char) (a >> b);
    }

    public static byte shr(byte a, byte b) {
        return (byte) (a >> b);
    }

    public static short shr(short a, short b) {
        return (short) (a >> b);
    }

    public static int shr(int a, int b) {
        return a >> b;
    }

    public static long shr(long a, long b) {
        return a >> b;
    }

    public static char ushr(char a, char b) {
        return (char) (a >>> b);
    }

    public static byte ushr(byte a, byte b) {
        return (byte) (a >>> b);
    }

    public static short ushr(short a, short b) {
        return (short) (a >>> b);
    }

    public static int ushr(int a, int b) {
        return a >>> b;
    }

    public static long ushr(long a, long b) {
        return a >>> b;
    }

    public static boolean and(boolean a, boolean b) {
        return a & b;
    }

    public static char and(char a, char b) {
        return (char) (a & b);
    }

    public static byte and(byte a, byte b) {
        return (byte) (a & b);
    }

    public static short and(short a, short b) {
        return (short) (a & b);
    }

    public static int and(int a, int b) {
        return a & b;
    }

    public static long and(long a, long b) {
        return a & b;
    }

    public static boolean or(boolean a, boolean b) {
        return a | b;
    }

    public static char or(char a, char b) {
        return (char) (a | b);
    }

    public static byte or(byte a, byte b) {
        return (byte) (a | b);
    }

    public static short or(short a, short b) {
        return (short) (a | b);
    }

    public static int or(int a, int b) {
        return a | b;
    }

    public static long or(long a, long b) {
        return a | b;
    }

    public static boolean xor(boolean a, boolean b) {
        return a ^ b;
    }

    public static char xor(char a, char b) {
        return (char) (a ^ b);
    }

    public static byte xor(byte a, byte b) {
        return (byte) (a ^ b);
    }

    public static short xor(short a, short b) {
        return (short) (a ^ b);
    }

    public static int xor(int a, int b) {
        return a ^ b;
    }

    public static long xor(long a, long b) {
        return a ^ b;
    }

    public static boolean eq(Object a, Object b) {
        return a == b;
    }

    public static boolean eq(boolean a, boolean b) {
        return a == b;
    }

    public static boolean eq(char a, char b) {
        return a == b;
    }

    public static boolean eq(byte a, byte b) {
        return a == b;
    }

    public static boolean eq(short a, short b) {
        return a == b;
    }

    public static boolean eq(int a, int b) {
        return a == b;
    }

    public static boolean eq(long a, long b) {
        return a == b;
    }

    public static boolean eq(float a, float b) {
        return a == b;
    }

    public static boolean eq(double a, double b) {
        return a == b;
    }

    public static boolean ne(Object a, Object b) {
        return a != b;
    }

    public static boolean ne(boolean a, boolean b) {
        return a != b;
    }

    public static boolean ne(char a, char b) {
        return a != b;
    }

    public static boolean ne(byte a, byte b) {
        return a != b;
    }

    public static boolean ne(short a, short b) {
        return a != b;
    }

    public static boolean ne(int a, int b) {
        return a != b;
    }

    public static boolean ne(long a, long b) {
        return a != b;
    }

    public static boolean ne(float a, float b) {
        return a != b;
    }

    public static boolean ne(double a, double b) {
        return a != b;
    }

    public static boolean lt(char a, char b) {
        return a < b;
    }

    public static boolean lt(byte a, byte b) {
        return a < b;
    }

    public static boolean lt(short a, short b) {
        return a < b;
    }

    public static boolean lt(int a, int b) {
        return a < b;
    }

    public static boolean lt(long a, long b) {
        return a < b;
    }

    public static boolean lt(float a, float b) {
        return a < b;
    }

    public static boolean lt(double a, double b) {
        return a < b;
    }

    public static boolean ge(char a, char b) {
        return a >= b;
    }

    public static boolean ge(byte a, byte b) {
        return a >= b;
    }

    public static boolean ge(short a, short b) {
        return a >= b;
    }

    public static boolean ge(int a, int b) {
        return a >= b;
    }

    public static boolean ge(long a, long b) {
        return a >= b;
    }

    public static boolean ge(float a, float b) {
        return a >= b;
    }

    public static boolean ge(double a, double b) {
        return a >= b;
    }

    public static boolean gt(char a, char b) {
        return a > b;
    }

    public static boolean gt(byte a, byte b) {
        return a > b;
    }

    public static boolean gt(short a, short b) {
        return a > b;
    }

    public static boolean gt(int a, int b) {
        return a > b;
    }

    public static boolean gt(long a, long b) {
        return a > b;
    }

    public static boolean gt(float a, float b) {
        return a > b;
    }

    public static boolean gt(double a, double b) {
        return a > b;
    }

    public static boolean le(char a, char b) {
        return a <= b;
    }

    public static boolean le(byte a, byte b) {
        return a <= b;
    }

    public static boolean le(short a, short b) {
        return a <= b;
    }

    public static boolean le(int a, int b) {
        return a <= b;
    }

    public static boolean le(long a, long b) {
        return a <= b;
    }

    public static boolean le(float a, float b) {
        return a <= b;
    }

    public static boolean le(double a, double b) {
        return a <= b;
    }

    public static char neg(char a) {
        return (char) -a;
    }

    public static byte neg(byte a) {
        return (byte) -a;
    }

    public static short neg(short a) {
        return (short) -a;
    }

    public static int neg(int a) {
        return -a;
    }

    public static long neg(long a) {
        return -a;
    }

    public static float neg(float a) {
        return -a;
    }

    public static double neg(double a) {
        return -a;
    }

    public static char com(char a) {
        return (char) ~a;
    }

    public static byte com(byte a) {
        return (byte) ~a;
    }

    public static short com(short a) {
        return (short) ~a;
    }

    public static int com(int a) {
        return ~a;
    }

    public static long com(long a) {
        return ~a;
    }

    public static boolean not(boolean a) {
        return !a;
    }
}
