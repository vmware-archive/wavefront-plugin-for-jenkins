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

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;

import net.sf.json.JSONObject;

public class WavefrontJobProperty<T extends Job<?, ?>> extends JobProperty<T> {
    private boolean enableSendingJunitReportData = false;
    private boolean enableSendingJacocoReportData = false;
    private String jobParameters = "";

    @DataBoundConstructor
    public WavefrontJobProperty() {
    }


    public boolean isEnableSendingJunitReportData() {
        return enableSendingJunitReportData;
    }

    /**
     * @param enableSendingJunitReportData - The configured checkbox in the job configuration
     */
    @DataBoundSetter
    public void setEnableSendingJunitReportData(boolean enableSendingJunitReportData) {
        this.enableSendingJunitReportData = enableSendingJunitReportData;
    }

    public boolean isEnableSendingJacocoReportData() {
        return enableSendingJacocoReportData;
    }

    /**
     * @param enableSendingJacocoReportData - The configured checkbox in the job configuration
     */
    @DataBoundSetter
    public void setEnableSendingJacocoReportData(boolean enableSendingJacocoReportData) {
        this.enableSendingJacocoReportData = enableSendingJacocoReportData;
    }

    public String getJobParameters() {
        return jobParameters;
    }

    @DataBoundSetter
    public void setJobParameters(String jobParameters) {
        this.jobParameters = jobParameters;
    }

    /**
     * This method is called whenever the Job form is saved. We use the 'on' property
     * to determine if the controls are selected.
     *
     * @param req  - The request
     * @param form - A JSONObject containing the submitted form data from the job configuration
     * @return a {@link JobProperty} object representing the tagging added to the job
     * @throws hudson.model.Descriptor.FormException if querying of form throws an error
     */
    @Override
    public JobProperty<?> reconfigure(StaplerRequest req, @Nonnull JSONObject form)
            throws Descriptor.FormException {

        WavefrontJobProperty prop = (WavefrontJobProperty) super.reconfigure(req, form);
        boolean isEnableToSendJunitReportData = form.getBoolean("enableSendingJunitReportData");
        if (!isEnableToSendJunitReportData) {
            enableSendingJunitReportData = false;
        }
        boolean isEnableToSendJacocoReportData = form.getBoolean("enableSendingJacocoReportData");
        if (!isEnableToSendJacocoReportData) {
            enableSendingJacocoReportData = false;
        }
        jobParameters = form.getString("jobParameters");
        return prop;
    }


    @Extension
    public static final class WavefrontJobPropertyDescriptorImpl
            extends JobPropertyDescriptor {

        /**
         * Getter function for a human readable class display name.
         *
         * @return a String containing the human readable display name for the {@link JobProperty} class.
         */
        @Override
        public String getDisplayName() {
            return "Wavefront job property";
        }

        /**
         * Indicates where this property can be used
         *
         * @param jobType - a Job object
         * @return Always true. This property can be set for all Job types.
         */
        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }
    }
}
