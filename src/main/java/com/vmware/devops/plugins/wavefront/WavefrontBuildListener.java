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
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.jacoco.JacocoBuildAction;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.test.TestResult;

@Extension
public class WavefrontBuildListener extends RunListener<Run> {
    private static final Logger LOGGER = Logger.getLogger(WavefrontBuildListener.class.getName());
    private static final String STATUS = "Status";
    private static final String BUILD_NUMBER = "Build Number";
    private static final String JOB_NAME = "Job Name";
    private static final String TEST_STATUS = "Test Status";
    private static final String PASSED = "Passed";
    private static final String FAILED = "Failed";
    private static final String SKIPPED = "Skipped";
    private WavefrontManagement wfManagement;

    public WavefrontBuildListener() {
        wfManagement = WavefrontManagement.get();
    }


    /**
     * Called when a build is completed.
     *
     * @param run      - A Run object representing a particular execution of Job.
     * @param listener - A TaskListener object which receives events that happen during some
     *                 operation.
     */
    @Override
    public final void onCompleted(final Run run, @Nonnull final TaskListener listener) {
        if (run != null && getWavefrontManagement().getProxyHostname() != null && !getWavefrontManagement().getProxyHostname().equals("")) {
            try {
                sendJobMetricsToWavefront(run);
                if (run instanceof WorkflowRun) {
                    sendPipelineMetricsToWavefront((WorkflowRun) run);
                }
                WavefrontJobProperty junitReportProperty = (WavefrontJobProperty) run.getParent().getProperty(WavefrontJobProperty.class);
                if (junitReportProperty != null && junitReportProperty.isEnableSendingJunitReportData()) {
                    sendJunitReportMetricsToWavefront(run);
                }
                if (junitReportProperty != null && junitReportProperty.isEnableSendingJacocoReportData()) {
                    sendJacocoReportMetricsToWavefront(run);
                }

                LOGGER.log(Level.FINE, "Job metrics successfully sent");
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to send job metrics to Wavefront", e);
            }
        }
    }

    private void sendJobMetricsToWavefront(Run run) throws IOException {
        Map<String, String> tags = new HashMap<>();
        tags.put(STATUS, run.getResult().toString());
        tags.put(BUILD_NUMBER, run.getId());
        long duration = run.getDuration();
        String jobName = sanitizeMetricCategory(run.getParent().getName());
        sendMetricsToWavefront(jobName, duration, tags);
    }

    private void sendPipelineMetricsToWavefront(WorkflowRun run) throws IOException {
        String pipelineName = sanitizeMetricCategory(run.getParent().getName());
        String buildNumber = run.getId();

        if (run.getExecution() != null) {
            Deque<Map.Entry<FlowNode, String>> endNodes = new ArrayDeque<>(); // used as stack
            FlowGraphWalker w = new FlowGraphWalker(run.getExecution());

            for (FlowNode node : w) {
                if (node instanceof BlockStartNode) {
                    Entry<FlowNode, String> endNode = endNodes.pop();
                    FlowNodeData flowNodeData = new FlowNodeData()
                            .setDuration(calculateDuration(node, endNode.getKey()))
                            .setPipelineName(pipelineName)
                            .addTag(STATUS, endNode.getValue())
                            .addTag(BUILD_NUMBER, buildNumber);

                    if (isStageNode(node)) {
                        flowNodeData.setNodeName(sanitizeMetricCategory(node.getDisplayName()));
                        sendStageMetricsData(pipelineName, flowNodeData);
                    } else if (hasParallelLabelAction(node)) {
                        flowNodeData.setNodeName(sanitizeMetricCategory(node.getDisplayName().replaceFirst("Branch: ", "")));
                        sendParallelMetricsData(pipelineName, flowNodeData);
                    }
                }
                if (node instanceof BlockEndNode) {
                    endNodes.push(new AbstractMap.SimpleEntry<>(node, getNodeStatus(node)));
                }
            }
        }
    }

    private static class FlowNodeData {

        public String nodeName = "UNDEFINED";
        public String pipelineName = "UNDEFINED";
        public Long duration = -1L;
        public Map<String, String> tags = new HashMap<>();

        public FlowNodeData setNodeName(String nodeName) {
            this.nodeName = nodeName;
            return this;
        }

        public FlowNodeData setPipelineName(String pipelineName) {
            this.pipelineName = pipelineName;
            return this;
        }

        public FlowNodeData setDuration(Long duration) {
            this.duration = duration;
            return this;
        }

        public FlowNodeData addTag(String key, String value) {
            tags.put(key, value);
            return this;
        }

    }

    private long calculateDuration(FlowNode start, FlowNode end) {
        TimingAction startTimeAction = start.getAction(TimingAction.class);
        TimingAction endTimeAction = end.getAction(TimingAction.class);
        return endTimeAction.getStartTime() - startTimeAction.getStartTime();
    }

    private void sendStageMetricsData(
            String pipelineName,
            FlowNodeData nodeData
    ) throws IOException {
        sendMetricsToWavefront(pipelineName + ".stage." + nodeData.nodeName, nodeData.duration, nodeData.tags);
    }

    private void sendParallelMetricsData(
            String pipelineName,
            FlowNodeData nodeData
    ) throws IOException {
        sendMetricsToWavefront(pipelineName + ".parallel." + nodeData.nodeName, nodeData.duration, nodeData.tags);
    }

    public String getNodeStatus(FlowNode node) {
        List<ErrorAction> errors = node.getActions(ErrorAction.class);
        if (errors.isEmpty()) {
            return "SUCCESS";
        } else if (errors.get(0).getDisplayName() == null) {
            return "ABORTED";
        }

        return "FAILURE";
    }

    private boolean hasParallelLabelAction(FlowNode node) {
        try {
            Class<?> requiredClass = Class.forName("org.jenkinsci.plugins.workflow.cps.steps.ParallelStepExecution$ParallelLabelAction");
            for (Action action : node.getActions()) {
                if (action.getClass() == requiredClass) {
                    return true;
                }
            }
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.WARNING, "Failed to get class: ParallelStepExecution$ParallelLabelAction", e);
        }

        return false;
    }

    public static boolean isStageNode(FlowNode node) {
        return (node.getAction(LabelAction.class) != null && node.getAction(ThreadNameAction.class) == null);
    }

    private void sendJunitReportMetricsToWavefront(Run run) throws IOException {
        String jobName = sanitizeMetricCategory(run.getParent().getName());
        String buildNumber = run.getId();

        TestResultAction action = run.getAction(TestResultAction.class);
        if (action != null) {
            String metricName = "junit." + jobName;
            double fullDurationForTests = action.getResult().getDuration() * 1000;
            sendMetricsToWavefront(metricName, fullDurationForTests, null); // send whole time required for tests

            Map<String, String> tags = new HashMap<>();
            tags.put(JOB_NAME, jobName);
            tags.put(BUILD_NUMBER, buildNumber);

            tags.put(TEST_STATUS, FAILED);
            sendJUnitTestResultMetricsToWavefront(action.getResult().getFailedTests(), tags);
            tags.put(TEST_STATUS, SKIPPED);
            sendJUnitTestResultMetricsToWavefront(action.getResult().getSkippedTests(), tags);
            tags.put(TEST_STATUS, PASSED);
            sendJUnitTestResultMetricsToWavefront(action.getResult().getPassedTests(), tags);
        }
    }

    private void sendJUnitTestResultMetricsToWavefront(Collection<? extends TestResult> testResults, Map<String, String> tags) throws IOException {

        for (TestResult testResult : testResults) {
            String fullTestName = testResult.getFullDisplayName();
            String metricName = "junit." + sanitizeJUnitTestMetricCategory(fullTestName);
            double testDuration = testResult.getDuration() * 1000; // in milliseconds
            sendMetricsToWavefront(metricName, testDuration, tags);
        }
    }

    private void sendJacocoReportMetricsToWavefront(Run run) throws IOException {
        String jobName = sanitizeMetricCategory(run.getParent().getName());
        String buildNumber = run.getId();

        JacocoBuildAction action = run.getAction(JacocoBuildAction.class);
        if (action != null) {
            Map<String, Integer> metrics = new HashMap<>();
            metrics.put("instructions-coverage", action.getInstructionCoverage().getPercentage());
            metrics.put("branch-coverage", action.getBranchCoverage().getPercentage());
            metrics.put("complexity-coverage", action.getComplexityScore().getPercentage());
            metrics.put("line-coverage", action.getLineCoverage().getPercentage());
            metrics.put("method-coverage", action.getMethodCoverage().getPercentage());
            metrics.put("class-coverage", action.getClassCoverage().getPercentage());

            metrics.put("instructions-coverage.minimum", action.getThresholds().getMinInstruction());
            metrics.put("branch-coverage.minimum", action.getThresholds().getMinBranch());
            metrics.put("complexity-coverage.minimum", action.getThresholds().getMinComplexity());
            metrics.put("line-coverage.minimum", action.getThresholds().getMinLine());
            metrics.put("method-coverage.minimum", action.getThresholds().getMinMethod());
            metrics.put("class-coverage.minimum", action.getThresholds().getMinClass());

            metrics.put("instructions-coverage.maximum", action.getThresholds().getMaxInstruction());
            metrics.put("branch-coverage.maximum", action.getThresholds().getMaxBranch());
            metrics.put("complexity-coverage.maximum", action.getThresholds().getMaxComplexity());
            metrics.put("line-coverage.maximum", action.getThresholds().getMaxLine());
            metrics.put("method-coverage.maximum", action.getThresholds().getMaxMethod());
            metrics.put("class-coverage.maximum", action.getThresholds().getMaxClass());

            metrics.put("instructions-coverage.covered", action.getInstructionCoverage().getCovered());
            metrics.put("branch-coverage.covered", action.getBranchCoverage().getCovered());
            metrics.put("complexity-coverage.covered", action.getComplexityScore().getCovered());
            metrics.put("line-coverage.covered", action.getLineCoverage().getCovered());
            metrics.put("method-coverage.covered", action.getMethodCoverage().getCovered());
            metrics.put("class-coverage.covered", action.getClassCoverage().getCovered());

            metrics.put("instructions-coverage.total", action.getInstructionCoverage().getTotal());
            metrics.put("branch-coverage.total", action.getBranchCoverage().getTotal());
            metrics.put("complexity-coverage.total", action.getComplexityScore().getTotal());
            metrics.put("line-coverage.total", action.getLineCoverage().getTotal());
            metrics.put("method-coverage.total", action.getMethodCoverage().getTotal());
            metrics.put("class-coverage.total", action.getClassCoverage().getTotal());

            Map<String, String> tags = new HashMap<>();
            tags.put(STATUS, run.getResult().toString());
            tags.put(BUILD_NUMBER, buildNumber);
            sendCodeCoverageMetricsToWavefront(jobName, metrics, tags);
        }
    }

    private void sendCodeCoverageMetricsToWavefront(String jobName, Map<String, Integer> metrics, Map<String, String> tags) throws IOException {
        for (Entry<String, Integer> metric : metrics.entrySet()) {
            String metricName = jobName + ".jacoco." + metric.getKey();
            sendMetricsToWavefront(metricName, metric.getValue(), tags);
        }
    }

    private void sendMetricsToWavefront(String jobName, double metricValue, Map<String, String> tags) throws IOException {
        String name = wfManagement.getJobMetricsPrefixName() + "." + jobName;
        WavefrontMonitor.getWavefrontSender().sendMetric(name, metricValue, System.currentTimeMillis(),
                wfManagement.getProxyHostname(), tags);
    }

    private String sanitizeMetricCategory(String name) {
        return name.toLowerCase().replaceAll("[&\\s\\.]", "_");
    }

    private String sanitizeJUnitTestMetricCategory(String name) {
        String result = name.toLowerCase().replaceAll("[&\\s]", "_");
        if (result.endsWith("]")) {
            result = result.replace("[", ".").substring(0, result.length() - 1);
        }
        return result;
    }

    private WavefrontManagement getWavefrontManagement() {
        if (wfManagement == null) {
            wfManagement = WavefrontManagement.get();
        }
        return wfManagement;
    }
}
