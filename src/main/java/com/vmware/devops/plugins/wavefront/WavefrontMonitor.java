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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.wavefront.sdk.proxy.WavefrontProxyClient;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AperiodicWork;
import hudson.model.Label;
import hudson.model.LoadStatistics.LoadStatisticsSnapshot;
import jenkins.model.Jenkins;

@Extension
public class WavefrontMonitor extends AperiodicWork {
    private static final Logger LOGGER = Logger.getLogger(WavefrontMonitor.class.getName());

    private static final String SYSTEM_CPU = "system-cpu";
    private static final String TOTAL_PHYSICAL_MEMORY = "total-physical-memory";
    private static final String FREE_PHYSICAL_MEMORY = "free-physical-memory";
    private static final String MAX_HEAP_MEMORY = "max-heap-memory";
    private static final String USED_HEAP_MEMORY = "used-heap-memory";

    private static final String AVAILABLE_EXECUTORS = "available-executors";
    private static final String BUSY_EXECUTORS = "busy-executors";
    private static final String CONNECTING_EXECUTORS = "connecting-executors";
    private static final String DEFINED_EXECUTORS = "defined-executors";
    private static final String IDLE_EXECUTORS = "idle-executors";
    private static final String ONLINE_EXECUTORS = "online-executors";
    private static final String QUEUE_LENGTH = "queue-length";

    private static final String LABEL = "label";

    private static WavefrontProxyClient wavefrontSender;
    private static WavefrontManagement wfManagement;
    private static WavefrontMonitor currentTask = null;
    private static boolean isWavefrontSenderClosed = false;

    public WavefrontMonitor() {
        wfManagement = WavefrontManagement.get();
    }

    @Override
    public AperiodicWork getNewInstance() {
        if (currentTask != null) {
            currentTask.cancel();
        }

        currentTask = new WavefrontMonitor();

        return currentTask;
    }

    @Override
    protected void doAperiodicRun() {
        if (wfManagement.getProxyHostname() != null && !wfManagement.getProxyHostname().equals("")) {
            LOGGER.log(Level.FINE, "Sending data to wavefront");
            SystemMetrics.SystemMetricsSnapshot snapshot = SystemMetrics.getSystemMetricsSnapshot();
            String source = wfManagement.getProxyHostname();
            try {
                sendMetricsToWavefront(snapshot, source);
                sendMetricsToWavefrontFromLabels(source);
                LOGGER.log(Level.FINE, "Successfully sent data");
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to send metrics to Wavefront", e);
            }
        }
    }

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.MINUTES.toMillis(wfManagement.getFlushInterval());
    }


    public void sendMetricsToWavefront(SystemMetrics.SystemMetricsSnapshot snapshot, String source) throws IOException {
        sendMetricsToWavefront(SYSTEM_CPU, snapshot.getCpuLoad(), source);
        sendMetricsToWavefront(TOTAL_PHYSICAL_MEMORY, snapshot.getTotalPhysicalMemory(), source);
        sendMetricsToWavefront(FREE_PHYSICAL_MEMORY, snapshot.getFreePhysicalMemory(), source);
        sendMetricsToWavefront(MAX_HEAP_MEMORY, snapshot.getMaxHeapMemory(), source);
        sendMetricsToWavefront(USED_HEAP_MEMORY, snapshot.getUsedHeapMemory(), source);
    }

    public void sendMetricsToWavefrontFromLabels(String source) throws IOException {
        Label[] labels = Jenkins.getInstanceOrNull().getLabels().toArray(new Label[0]);
        for (Label l : labels) {
            sendMetricsToWavefront(LABEL + "." + l.getDisplayName(), l.loadStatistics.computeSnapshot(), source);
        }
    }

    public void sendMetricsToWavefront(String labelName, LoadStatisticsSnapshot computeSnapshot, String source) throws IOException {
        sendMetricsToWavefront(labelName + "." + AVAILABLE_EXECUTORS, computeSnapshot.getAvailableExecutors(), source);
        sendMetricsToWavefront(labelName + "." + BUSY_EXECUTORS, computeSnapshot.getBusyExecutors(), source);
        sendMetricsToWavefront(labelName + "." + CONNECTING_EXECUTORS, computeSnapshot.getConnectingExecutors(), source);
        sendMetricsToWavefront(labelName + "." + DEFINED_EXECUTORS, computeSnapshot.getDefinedExecutors(), source);
        sendMetricsToWavefront(labelName + "." + IDLE_EXECUTORS, computeSnapshot.getIdleExecutors(), source);
        sendMetricsToWavefront(labelName + "." + ONLINE_EXECUTORS, computeSnapshot.getOnlineExecutors(), source);
        sendMetricsToWavefront(labelName + "." + QUEUE_LENGTH, computeSnapshot.getQueueLength(), source);
    }

    public void sendMetricsToWavefront(String metricName, double metricValue, String source) throws IOException {
        String name = wfManagement.getMetricsPrefixName() + "." + metricName;
        getWavefrontSender().sendMetric(name, metricValue, System.currentTimeMillis(),
                source, null);
    }

    public static WavefrontProxyClient createWavefrontProxyClient() {
        WavefrontProxyClient.Builder wfProxyClientBuilder = new WavefrontProxyClient.Builder(wfManagement.getProxyHostname());

        // Set the proxy port to send metrics to. Default: 2878
        wfProxyClientBuilder.metricsPort(wfManagement.getProxyPort());

        // Set a proxy port to send histograms to.  Recommended: 2878
        wfProxyClientBuilder.distributionPort(wfManagement.getProxyPort());

        // Set a proxy port to send trace data to. Recommended: 30000
        //wfProxyClientBuilder.tracingPort(wfManagement.getTracingPort());

        // Optional: Set a custom socketFactory to override the default SocketFactory
        //wfProxyClientBuilder.socketFactory(<SocketFactory>);

        // Optional: Set a nondefault interval (in seconds) for flushing data from the sender to the proxy. Default: 5 seconds
        //wfProxyClientBuilder.flushIntervalSeconds(5);

        return wfProxyClientBuilder.build();
    }

    public static WavefrontMonitor getInstance() {
        ExtensionList<WavefrontMonitor> list = Jenkins.getInstanceOrNull().getExtensionList(WavefrontMonitor.class);
        assert list.size() == 1;
        return list.iterator().next();
    }

    public static WavefrontProxyClient getWavefrontSender() {
        if (!isWavefrontSenderInitialized()) {
            initializeWavefrontSender();
        }

        return wavefrontSender;
    }

    private static synchronized void initializeWavefrontSender() {
        if (!isWavefrontSenderInitialized()) {
            wavefrontSender = createWavefrontProxyClient();
            isWavefrontSenderClosed = false;
        }
    }

    public static boolean isWavefrontSenderInitialized() {
        return !(wavefrontSender == null || isWavefrontSenderClosed);
    }

    public void closeWavefrontSender() {
        try {
            wavefrontSender.flush();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to flush wavefront sender", e);
        }
        wavefrontSender.close();
    }

    public static WavefrontMonitor getCurrentTask() {
        return currentTask;
    }

    public void setWavefrontSenderClosed(boolean isClosed) {
        this.isWavefrontSenderClosed = isClosed;
    }
}