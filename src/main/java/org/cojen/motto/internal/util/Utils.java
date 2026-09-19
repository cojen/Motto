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

package org.cojen.motto.internal.util;

import java.util.List;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class Utils {
    public static <C extends Comparable<C>> int compare(List<C> a, List<C> b) {
        int len = Math.min(a.size(), b.size());

        for (int i=0; i<len; i++) {
            int cmp = a.get(i).compareTo(b.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }

        return Integer.compare(a.size(), b.size());
    }
}
