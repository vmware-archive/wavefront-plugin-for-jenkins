/*
 * Copyright (c) 2019 VMware, Inc. All Rights Reserved.
 *
 * SPDX-License-Identifier: MIT
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.vmware.devops.plugins.wavefront.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Sanitizer {
    private static final Logger LOGGER = Logger.getLogger(Sanitizer.class.getName());

    private Sanitizer() {

    }

    public static String sanitizeMetricCategory(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9/_-]", "_");
    }

    public static String sanitizeFullMetricCategory(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9/_\\.-]", "_");
    }

    public static String sanitizeJUnitTestMetricCategory(String name) {
        if (name.endsWith("]")) {
            name = name
                    .replace("[", ".")
                    .substring(0, name.length() - 1)
                    .replaceAll("(\\.)\\1+", ".");
        }
        return sanitizeFullMetricCategory(name);
    }

    public static String sanitizeStepMetricCategory(String name) {
        if (name == null || name.equals("")) {
            LOGGER.log(Level.FINE, "wavefrontTimedCall method has no parameter");
            return "null";
        }
        return sanitizeMetricCategory(getDecodeJobName(name));
    }

    public static String getDecodeJobName(String name) {
        if (name.contains("%")) {
            try {
                return URLDecoder.decode(name, "utf-8");
            } catch (UnsupportedEncodingException e) {
                LOGGER.log(Level.WARNING, "Unsupported character in the job name", e);
            }
        }
        return name;
    }
}
