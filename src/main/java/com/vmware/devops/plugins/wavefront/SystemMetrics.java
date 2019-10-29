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

import java.lang.management.ManagementFactory;

public class SystemMetrics {
    private SystemMetrics() {

    }

    public static double getCpuLoadMetrics() {
        return ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class).getProcessCpuLoad();
    }

    public static long getTotalPhysicalMemorySizeMetrics() {
        return ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class).getTotalPhysicalMemorySize();
    }

    public static long getFreePhysicalMemorySizeMetrics() {
        return ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class).getFreePhysicalMemorySize();
    }

    public static double getMaxHeapMemoryUsageMetrics() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
    }

    public static double getUsedHeapMemoryUsageMetrics() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    public static SystemMetricsSnapshot getSystemMetricsSnapshot() {
        double cpuLoad = getCpuLoadMetrics();
        long totalPhysicalMemory = getTotalPhysicalMemorySizeMetrics();
        long freePhysicalMemory = getFreePhysicalMemorySizeMetrics();
        double maxHeapMemory = getMaxHeapMemoryUsageMetrics();
        double usedHeapMemory = getUsedHeapMemoryUsageMetrics();
        return new SystemMetricsSnapshot(cpuLoad, totalPhysicalMemory, freePhysicalMemory, maxHeapMemory,
                usedHeapMemory);
    }

    public static class SystemMetricsSnapshot {
        private double cpuLoad;
        private long totalPhysicalMemory;
        private long freePhysicalMemory;
        private double maxHeapMemory;
        private double usedHeapMemory;

        public SystemMetricsSnapshot(double cpuLoad, long totalPhysicalMemory, long freePhysicalMemory,
                                     double maxHeapMemory, double usedHeapMemory) {
            this.cpuLoad = cpuLoad;
            this.totalPhysicalMemory = totalPhysicalMemory;
            this.freePhysicalMemory = freePhysicalMemory;
            this.maxHeapMemory = maxHeapMemory;
            this.usedHeapMemory = usedHeapMemory;
        }

        public double getCpuLoad() {
            return cpuLoad;
        }

        public long getTotalPhysicalMemory() {
            return totalPhysicalMemory;
        }

        public long getFreePhysicalMemory() {
            return freePhysicalMemory;
        }

        public double getMaxHeapMemory() {
            return maxHeapMemory;
        }

        public double getUsedHeapMemory() {
            return usedHeapMemory;
        }
    }
}