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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.gargoylesoftware.htmlunit.html.HtmlForm;

public class WavefrontMonitorTest {
    private static int MIN_PORT_NUMBER = 50000;
    private static int MAX_PORT_NUMBER = 60000;
    private static String LOCALHOST = "localhost";
    private MockWavefrontProxy proxy;
    private int port;

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Before
    public void init() throws Exception {
        Entry<MockWavefrontProxy, Integer> mockedProxy = MockWavefrontProxy.initMockedWavefrontProxy(MIN_PORT_NUMBER, MAX_PORT_NUMBER);
        proxy = mockedProxy.getKey();
        port = mockedProxy.getValue();
        HtmlForm form = jenkinsRule.createWebClient().goTo("wavefront-plugin").getFormByName("config");
        form.getInputByName("_.proxyHostname").setValueAttribute(LOCALHOST);
        form.getInputByName("_.proxyPort").setValueAttribute(String.valueOf(port));
        jenkinsRule.submit(form);
        WavefrontMonitor.getInstance().setWavefrontSenderClosed(true);
    }

    @Test
    public void testSendingSystemMetricsToProxy() throws Exception {
        WavefrontMonitor wm = WavefrontMonitor.getInstance();
        SystemMetrics.SystemMetricsSnapshot snapshot = SystemMetrics.getSystemMetricsSnapshot();
        wm.sendMetricsToWavefront(snapshot, LOCALHOST);
        List<String> messages = proxy.terminate();
        Set<String> metricsNames = parseMessages(messages);
        List<String> expected = new ArrayList<>(Arrays.asList(
                "wjp.system-cpu",
                "wjp.total-physical-memory",
                "wjp.free-physical-memory",
                "wjp.max-heap-memory",
                "wjp.used-heap-memory"
        ));
        boolean result = metricsNames.containsAll(expected);
        if (!result) {
            expected.removeAll(metricsNames);
            String message = "Messages above are missing, not as expected:";
            for (String metricName : expected) {
                message += "\n" + metricName;
            }
            Assert.fail(message);
        }
    }

    @Test
    public void testSendingMetricsFromLabelsToProxy() throws Exception {
        WavefrontMonitor wm = WavefrontMonitor.getInstance();
        wm.sendMetricsToWavefrontFromLabels(LOCALHOST);
        List<String> messages = proxy.terminate();
        Set<String> metricsNames = parseMessages(messages);
        List<String> expected = new ArrayList<>(Arrays.asList(
                "wjp.label.master.available-executors",
                "wjp.label.master.busy-executors",
                "wjp.label.master.connecting-executors",
                "wjp.label.master.defined-executors",
                "wjp.label.master.idle-executors",
                "wjp.label.master.online-executors",
                "wjp.label.master.queue-length"
        ));
        boolean result = metricsNames.containsAll(expected);
        if (!result) {
            expected.removeAll(metricsNames);
            String message = "Messages above are missing, not as expected:";
            for (String metricName : expected) {
                message += "\n" + metricName;
            }
            Assert.fail(message);
        }
    }

    @Test
    public void testSendingSystemMetricsAndMetricsFromLabelsToProxy() throws Exception {
        WavefrontMonitor wm = WavefrontMonitor.getInstance();
        wm.doRun();
        List<String> messages = proxy.terminate();
        Set<String> metricsNames = parseMessages(messages);
        List<String> expected = new ArrayList<>(Arrays.asList(
                "wjp.system-cpu",
                "wjp.total-physical-memory",
                "wjp.free-physical-memory",
                "wjp.max-heap-memory",
                "wjp.used-heap-memory",
                "wjp.label.master.available-executors",
                "wjp.label.master.busy-executors",
                "wjp.label.master.connecting-executors",
                "wjp.label.master.defined-executors",
                "wjp.label.master.idle-executors",
                "wjp.label.master.online-executors",
                "wjp.label.master.queue-length"
        ));
        boolean result = metricsNames.containsAll(expected);
        if (!result) {
            expected.removeAll(metricsNames);
            String message = "Messages above are missing, not as expected:";
            for (String metricName : expected) {
                message += "\n" + metricName;
            }
            Assert.fail(message);
        }
    }

    public static Set<String> parseMessages(List<String> messages) {
        Set<String> result = new HashSet<>();
        for (String m : messages) {
            String name = m.split(" ")[0];
            name = name.substring(1, name.length() - 1);
            result.add(name);
        }
        return result;
    }

    @After
    public void closeConnections() throws IOException, InterruptedException {
        if (proxy.isTerminated()) {
            proxy.terminate();
        }
    }
}
