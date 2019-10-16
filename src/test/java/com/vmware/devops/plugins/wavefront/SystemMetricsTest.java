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

package com.vmware.devops.plugins.wavefront;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SystemMetricsTest {
    private SystemMetrics.SystemMetricsSnapshot snapshot;

    @Before
    public void initializeSnapshot() {
        snapshot = SystemMetrics.getSystemMetricsSnapshot();
    }

    @Test
    public void testCpuLoadMetric() {
        Assert.assertTrue(snapshot.getCpuLoad() >= 0);
    }

    @Test
    public void testTotalPhysicalMemorySizeMetrics() {
        Assert.assertTrue(snapshot.getTotalPhysicalMemory() >= 0);
    }

    @Test
    public void testFreePhysicalMemorySizeMetrics() {
        Assert.assertTrue(snapshot.getFreePhysicalMemory() >= 0);
    }

    @Test
    public void testMaxHeapMemoryUsageMetrics() {
        Assert.assertTrue(snapshot.getMaxHeapMemory() >= 0);
    }

    @Test
    public void testUsedHeapMemoryUsageMetrics() {
        Assert.assertTrue(snapshot.getUsedHeapMemory() >= 0);
    }
}