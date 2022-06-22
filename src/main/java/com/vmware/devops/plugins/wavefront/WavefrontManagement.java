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

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.vmware.devops.plugins.wavefront.util.Sanitizer;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import hudson.util.FormValidation;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/**
 * Wavefront page under "Manage Jenkins" page.
 */
@Extension
public class WavefrontManagement extends ManagementLink implements StaplerProxy,
        Describable<WavefrontManagement>, Saveable {
    private static final Logger LOGGER = Logger.getLogger(WavefrontManagement.class.getName());

    private static final String DEFAULT_PROXY_HOSTNAME = "";
    private static final int DEFAULT_PROXY_PORT = 2878;
    private static final int DEFAULT_FLUSH_INTERVAL = 5;
    private static final String DEFAULT_METRICS_PREFIX_NAME = "wjp";
    private static final String DEFAULT_JOB_METRICS_PREFIX_NAME = "wjp.job";

    private String proxyHostname = DEFAULT_PROXY_HOSTNAME;
    private int proxyPort = DEFAULT_PROXY_PORT;
    private int flushInterval = DEFAULT_FLUSH_INTERVAL;
    private String metricsPrefixName = DEFAULT_METRICS_PREFIX_NAME;
    private String jobMetricsPrefixName = DEFAULT_JOB_METRICS_PREFIX_NAME;
    private boolean enableSendingJunitReportDataForAllJobs = false;
    private boolean enableSendingJacocoReportDataForAllJobs = false;
    private boolean enableSendingParametersAsTagsForAllJobs = false;

    private static String VALIDATION_SUCCESS = "Success";
    private static String INVALID_PORT_ERROR_MESSAGE = "Invalid port specified. Range must be 0-65535";
    private static String INVALID_FLUSH_INTERVAL_ERROR_MESSAGE = "Invalid flush interval specified.";
    private static String INVALID_INPUT_ERROR_MESSAGE = "Invalid input. Must be integer value";

    public WavefrontManagement() throws IOException {
        load();
    }

    @DataBoundConstructor
    public WavefrontManagement(String proxyHostname) throws IOException {
        this.proxyHostname = proxyHostname;
        load();
    }

    public String getProxyHostname() {
        return proxyHostname;
    }

    public void setProxyHostname(String proxyHostname) {
        this.proxyHostname = proxyHostname;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/wavefront/images/wavefront-plugin-logo.png";
    }

    @Override
    public String getUrlName() {
        return "wavefront-plugin";
    }

    @Override
    public String getDisplayName() {
        return "Wavefront";
    }

    @Override
    public String getDescription() {
        return "Wavefront plugin configuration";
    }

    /**
     * Saves the form to the configuration and disk.
     *
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws ServletException if something unfortunate happens.
     * @throws IOException      if something unfortunate happens.
     */
    @RequirePOST
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws ServletException,
            IOException,
            InterruptedException {
        LOGGER.log(Level.FINE, "Setting new configuration");
        JSONObject form = req.getSubmittedForm();

        int proxyPort;
        int flushInterval;
        try {
            proxyPort = form.getInt("proxyPort");
            flushInterval = form.getInt("flushInterval");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Invalid input, configuration not set");
            rsp.sendRedirect(".");
            return;
        }

        FormValidation fv = getDescriptor().doValidateConfiguration(proxyPort, flushInterval);
        if (fv.getMessage().equals(INVALID_PORT_ERROR_MESSAGE) || fv.getMessage().equals(INVALID_FLUSH_INTERVAL_ERROR_MESSAGE)
                || fv.getMessage().equals(INVALID_FLUSH_INTERVAL_ERROR_MESSAGE)) {
            LOGGER.log(Level.WARNING, "Invalid input, configuration not set");
            rsp.sendRedirect(".");
            return;
        }
        setProxyHostname(form.getString("proxyHostname"));
        setProxyPort(proxyPort);
        setFlushInterval(flushInterval);
        setMetricsPrefixName(Sanitizer.sanitizeFullMetricCategory(form.getString("metricsPrefixName")));
        setJobMetricsPrefixName(Sanitizer.sanitizeFullMetricCategory(form.getString("jobMetricsPrefixName")));
        setEnableSendingJunitReportDataForAllJobs(form.getBoolean("enableSendingJunitReportDataForAllJobs"));
        setEnableSendingJacocoReportDataForAllJobs(form.getBoolean("enableSendingJacocoReportDataForAllJobs"));
        setEnableSendingParametersAsTagsForAllJobs(form.getBoolean("enableSendingParametersAsTagsForAllJobs"));
        rsp.sendRedirect(".");
        save();
    }

    @Override
    public void save() throws IOException {
        WavefrontMonitor wm = WavefrontMonitor.getInstance();

        if (WavefrontMonitor.isWavefrontSenderInitialized()) {
            wm.closeWavefrontSender();
        }

        if (WavefrontMonitor.getCurrentTask() != null) {
            WavefrontMonitor.getCurrentTask().setWavefrontSenderClosed(true);
        }

        getConfigXml().write(this);
    }

    public static WavefrontManagement get() {
        return ManagementLink.all().get(WavefrontManagement.class);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        Jenkins jenkinsInstance = Jenkins.getInstanceOrNull();
        if (jenkinsInstance != null) {
            return jenkinsInstance.getDescriptorByType(DescriptorImpl.class);
        }
        throw new IllegalStateException("Can't retrieve Jenkins instance");
    }

    public XmlFile getConfigXml() {
        Jenkins jenkinsInstance = Jenkins.getInstanceOrNull();
        if (jenkinsInstance != null) {
            return new XmlFile(Jenkins.XSTREAM, new File(jenkinsInstance.getRootDir(), this.getXmlFileName()));
        }
        throw new IllegalStateException("Can't retrieve Jenkins instance");
    }

    protected String getXmlFileName() {
        return getClass().getName() + ".xml";
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public int getFlushInterval() {
        return flushInterval;
    }

    public void setFlushInterval(int flushInterval) {
        this.flushInterval = flushInterval;
    }

    public String getMetricsPrefixName() {
        return metricsPrefixName;
    }

    public void setMetricsPrefixName(String metricsPrefixName) {
        this.metricsPrefixName = metricsPrefixName;
    }

    public String getJobMetricsPrefixName() {
        return jobMetricsPrefixName;
    }

    public void setJobMetricsPrefixName(String jobMetricsPrefixName) {
        this.jobMetricsPrefixName = jobMetricsPrefixName;
    }

    public boolean isEnableSendingJunitReportDataForAllJobs() {
        return enableSendingJunitReportDataForAllJobs;
    }

    public void setEnableSendingJunitReportDataForAllJobs(boolean enableSendingJunitReportDataForAllJobs) {
        this.enableSendingJunitReportDataForAllJobs = enableSendingJunitReportDataForAllJobs;
    }

    public boolean isEnableSendingJacocoReportDataForAllJobs() {
        return enableSendingJacocoReportDataForAllJobs;
    }

    public void setEnableSendingJacocoReportDataForAllJobs(boolean enableSendingJacocoReportDataForAllJobs) {
        this.enableSendingJacocoReportDataForAllJobs = enableSendingJacocoReportDataForAllJobs;
    }

    public boolean isEnableSendingParametersAsTagsForAllJobs() {
        return enableSendingParametersAsTagsForAllJobs;
    }

    public void setEnableSendingParametersAsTagsForAllJobs(boolean enableSendingParametersAsTagsForAllJobs) {
        this.enableSendingParametersAsTagsForAllJobs = enableSendingParametersAsTagsForAllJobs;
    }

    /**
     * Descriptor is only used for UI form bindings.
     */
    @Extension
    public static final class DescriptorImpl extends Descriptor<WavefrontManagement> {

        @Override
        public String getDisplayName() {
            return "Wavefront management"; // unused
        }

        public FormValidation doValidateConfiguration(@QueryParameter("proxyPort") final Integer proxyPort,
                                                      @QueryParameter("flushInterval") final Integer flushInterval) {
            try {
                if (proxyPort >= 0 && proxyPort <= 65535) {
                    if (flushInterval >= 1) {
                        return FormValidation.ok(VALIDATION_SUCCESS);
                    }
                    return FormValidation.error(INVALID_FLUSH_INTERVAL_ERROR_MESSAGE);
                }
                return FormValidation.error(INVALID_PORT_ERROR_MESSAGE);
            } catch (Exception e) {
                return FormValidation.error(INVALID_INPUT_ERROR_MESSAGE);
            }
        }
    }

    @Override
    public Object getTarget() {
        Jenkins.getInstanceOrNull().checkPermission(Jenkins.ADMINISTER);
        return this;
    }

    public synchronized void load() {
        XmlFile file = getConfigXml();
        if (!file.exists()) {
            return;
        }

        try {
            file.unmarshal(this);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load " + file, e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WavefrontManagement that = (WavefrontManagement) o;
        return proxyPort == that.proxyPort &&
                flushInterval == that.flushInterval &&
                Objects.equals(proxyHostname, that.proxyHostname) &&
                Objects.equals(metricsPrefixName, that.metricsPrefixName) &&
                Objects.equals(jobMetricsPrefixName, that.jobMetricsPrefixName) &&
                enableSendingJunitReportDataForAllJobs == that.enableSendingJunitReportDataForAllJobs &&
                enableSendingJacocoReportDataForAllJobs == that.enableSendingJacocoReportDataForAllJobs &&
                enableSendingParametersAsTagsForAllJobs == that.enableSendingParametersAsTagsForAllJobs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(proxyHostname, proxyPort, flushInterval, metricsPrefixName, jobMetricsPrefixName,
                enableSendingJunitReportDataForAllJobs, enableSendingJacocoReportDataForAllJobs, enableSendingParametersAsTagsForAllJobs);
    }

    @Override
    public String toString() {
        return "WavefrontManagement{" +
                "proxyHostname='" + proxyHostname + '\'' +
                ", proxyPort=" + proxyPort +
                ", flushInterval=" + flushInterval +
                ", metricsPrefixName='" + metricsPrefixName + '\'' +
                ", jobMetricsPrefixName='" + jobMetricsPrefixName + '\'' +
                ", enableSendingJunitReportDataForAllJobs=" + enableSendingJunitReportDataForAllJobs +
                ", enableSendingJacocoReportDataForAllJobs=" + enableSendingJacocoReportDataForAllJobs +
                ", enableSendingParametersAsTagsForAllJobs=" + enableSendingParametersAsTagsForAllJobs +
                '}';
    }
}