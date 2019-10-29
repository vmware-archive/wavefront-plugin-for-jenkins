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

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlNumberInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;

public class WavefrontManagementTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void configRoundTrip() throws Exception {
        HtmlForm form = jenkinsRule.createWebClient().goTo("wavefront-plugin").getFormByName("config");
        form.getInputByName("_.proxyHostname").setValueAttribute("xxx");
        jenkinsRule.submit(form);
        WavefrontManagement wm = WavefrontManagement.get();
        Assert.assertEquals("Unexpected proxy host value", "xxx", wm.getProxyHostname());

        WavefrontManagement loaded = (WavefrontManagement) wm.getConfigXml().unmarshal(WavefrontManagement.class);
        Assert.assertEquals("Loaded configuration is not the same as runtime", wm, loaded);
    }

    @Test
    public void testDefaultValuesOfConfiguration() throws IOException, SAXException {
        HtmlPage page = jenkinsRule.createWebClient().goTo("wavefront-plugin");

        HtmlTextInput inputProxyHostname = page.getElementByName("_.proxyHostname");
        Assert.assertEquals("Not right default value for proxy hostname", "", inputProxyHostname.getText());

        HtmlNumberInput inputProxyPort = page.getElementByName("_.proxyPort");
        Assert.assertEquals("Not right default value for proxy port", 2878, Integer.parseInt(inputProxyPort.getText()));

        HtmlNumberInput inputFlushInterval = page.getElementByName("_.flushInterval");
        Assert.assertEquals("Not right default value for flush interval", 5, Integer.parseInt(inputFlushInterval.getText()));
    }
}
