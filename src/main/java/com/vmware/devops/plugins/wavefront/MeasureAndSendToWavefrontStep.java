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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback.TailCall;
import org.jenkinsci.plugins.workflow.steps.GeneralNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;

public class MeasureAndSendToWavefrontStep extends Step {
    private static final Logger LOGGER = Logger.getLogger(MeasureAndSendToWavefrontStep.class.getName());
    private String metricName;
    private long startTime;

    @DataBoundConstructor
    public MeasureAndSendToWavefrontStep(String metricName) {
        this.metricName = metricName;
        startTime = System.currentTimeMillis();
    }

    public String getMetricName() {
        return metricName;
    }

    @DataBoundSetter
    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public long getStartTime() {
        return startTime;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    private static final class Execution extends GeneralNonBlockingStepExecution {
        private static final long serialVersionUID = 1L;
        private transient MeasureAndSendToWavefrontStep step;

        Execution(MeasureAndSendToWavefrontStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        public boolean start() throws Exception {
            run(this::doStart);
            return false;
        }

        private void doStart() throws Exception {
            getContext().newBodyInvoker()
                    .withCallback(new Callback(step.getStartTime(), step.getMetricName()))
                    .start();
        }
    }

    private static class Callback extends TailCall {
        private static final String JOB_NAME = "Job Name";
        private static final String BUILD_NUMBER = "Build Number";
        private static final long serialVersionUID = 1L;
        private long startTime;
        private String metricName;

        public Callback(long startTime, String metricName) {
            this.startTime = startTime;
            this.metricName = metricName;
        }

        @Override
        protected void finished(StepContext context) throws Exception {
            long durationMetricValue = System.currentTimeMillis() - startTime;
            String jobName = context.get(Run.class).getParent().getName();
            String buildNumber = String.valueOf(context.get(Run.class).getNumber());

            Map<String, String> tags = new HashMap<>();
            tags.put(JOB_NAME, jobName);
            tags.put(BUILD_NUMBER, buildNumber);

            String sanitizedName = sanitizeMetricCategory(metricName);
            String metricName = "step." + sanitizedName;

            sendMetricsToWavefront(metricName, durationMetricValue, tags, WavefrontManagement.get().getProxyHostname());
        }

        private void sendMetricsToWavefront(String metricName, double metricValue, Map<String, String> tags, String source) {
            String name = WavefrontManagement.get().getJobMetricsPrefixName() + "." + metricName;
            try {
                WavefrontMonitor.getWavefrontSender().sendMetric(name, metricValue, System.currentTimeMillis(),
                        source, tags);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to send metrics from wavefrontTimedCall step to Wavefront", e);
            }
        }

        private String sanitizeMetricCategory(String name) {
            if (name == null || name.equals("")) {
                LOGGER.log(Level.FINE, "wavefrontTimedCall method has no parameter");
                return "null";
            }
            return name.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @SuppressWarnings("unchecked")
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, EnvVars.class, Node.class, Run.class, FilePath.class, Launcher.class);
        }

        @Override
        public String getFunctionName() {
            return "wavefrontTimedCall";
        }

        @Override
        public String getDisplayName() {
            return "Sets up wavefront closure";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }
}