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

package org.cojen.motto.internal;

import java.io.*;

import java.util.*;

import org.cojen.motto.internal.compiler.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
// FIXME: testing
public class CompileTest {
    public static void main(String[] args) throws Exception {
        File sourceFile = new File(args[0]);

        var registry = ClassRegistry.fromClasspath();
        var compiler = new Compiler(new ErrorListener.Basic(), registry);

        compiler.compile(List.of(sourceFile));

        Map<File, Map<String, byte[]>> completed = compiler.waitForCompletion();

        if (compiler.numErrors() != 0) {
            compiler.close();
            return;
        }

        compiler.close();

        writeTemp(completed.values());
    }

    static void writeTemp(Collection<Map<String, byte[]>> classes) {
        for (Map<String, byte[]> map : classes) {
            writeTemp(map);
        }
    }

    static void writeTemp(Map<String, byte[]> classes) {
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            writeTemp(e.getKey(), e.getValue());
        }
    }

    static void writeTemp(String className, byte[] classBytes) {
        File file = new File("CompileTest/" + className + ".class");
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            file = new File(tempDir, file.getPath());
            file.getParentFile().mkdirs();

            System.out.println("CompileTest writing to " + file);

            try (var out = new FileOutputStream(file)) {
                out.write(classBytes);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }
}
