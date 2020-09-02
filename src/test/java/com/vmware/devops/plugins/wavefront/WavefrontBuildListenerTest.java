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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.gargoylesoftware.htmlunit.html.HtmlForm;

import hudson.model.BooleanParameterDefinition;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.TaskListener;
import hudson.plugins.jacoco.JacocoBuildAction;
import hudson.plugins.jacoco.JacocoHealthReportThresholds;
import hudson.plugins.jacoco.model.Coverage;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;

public class WavefrontBuildListenerTest {
    private static int MIN_PORT_NUMBER = 50000;
    private static int MAX_PORT_NUMBER = 60000;
    private static String LOCALHOST = "localhost";
    private int port;
    private MockWavefrontProxy proxy;
    private String jobMetricPrefix;
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
        jobMetricPrefix = WavefrontManagement.get().getJobMetricsPrefixName();
    }

    @After
    public void closeConnections() throws IOException, InterruptedException {
        if (proxy.isTerminated()) {
            proxy.terminate();
        }
    }

    @Test
    public void testSendingMetricsFromJob() throws IOException, InterruptedException {
        List<String> expected = new ArrayList<>(Arrays.asList(
                jobMetricPrefix + ".test_job 3000.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".test_job2 4000.0 source=localhost Status=FAILURE Build-Number=2",
                jobMetricPrefix + ".test-job3 15000.0 source=localhost Status=ABORTED Build-Number=33"
        ));
        Run run = getRun();
        WavefrontBuildListener buildLister = new WavefrontBuildListener();
        configureRun(run, "Test Job", 3000L, Result.SUCCESS, "1", false, false);
        buildLister.onCompleted(run, mock(TaskListener.class));
        configureRun(run, "Test Job2", 4000L, Result.FAILURE, "2", false, false);
        buildLister.onCompleted(run, mock(TaskListener.class));
        configureRun(run, "test-job3", 15000L, Result.ABORTED, "33", false, false);
        buildLister.onCompleted(run, mock(TaskListener.class));
        List<String> messages = proxy.terminate();

        checkExpectedResult(expected, messages);
    }

    @Test
    public void testSendingMetricsFromPipelineStages() throws Exception {
        List<String> expected = new ArrayList<>(Arrays.asList(
                jobMetricPrefix + ".test_pipeline",
                jobMetricPrefix + ".test_pipeline.stage.testing_stage1",
                jobMetricPrefix + ".test_pipeline.stage.testing_stage2",
                jobMetricPrefix + ".test_pipeline.parallel.thread-1",
                jobMetricPrefix + ".test_pipeline.parallel.thread-2",
                jobMetricPrefix + ".test_pipeline.stage.last_stage",
                jobMetricPrefix + ".step.metricname1",
                jobMetricPrefix + ".step.metricname2"

        ));

        WorkflowJob job = jenkinsRule.createProject(WorkflowJob.class, "Test Pipeline");
        WavefrontJobProperty junitReportProperty = mock(WavefrontJobProperty.class);
        when(junitReportProperty.isEnableSendingJunitReportData()).thenReturn(false);
        job.addProperty(junitReportProperty);
        job.setDefinition(new CpsFlowDefinition("node {\n" +
                "wavefrontTimedCall(\"metricName1\"){" +
                "   stage(\"Testing Stage1\") {\n" +
                "   }\n" +
                "   stage(\"Testing Stage2\") {\n" +
                "       parallel(\n" +
                "           \"Thread-1\": {},\n" +
                "           \"Thread-2\": {})\n" +
                "   }\n" +
                "   stage(\"Last Stage\") {\n" +
                "     wavefrontTimedCall(\"metricName2\"){" +
                "     }\n" +
                "   }\n" +
                "  }\n" +
                "}", true));

        jenkinsRule.buildAndAssertSuccess(job);
        List<String> messages = proxy.terminate();
        Set<String> actualMetrics = WavefrontMonitorTest.parseMessages(messages);
        boolean result = actualMetrics.containsAll(expected);
        if (!result) {
            expected.removeAll(actualMetrics);
            String message = "Messages above are missing, not as expected:";
            for (String metricName : expected) {
                message += "\n" + metricName;
            }
            Assert.fail(message);
        }
    }


    @Test
    public void testSendingJobParametersToWavefront() throws Exception {
        List<String> expected = new ArrayList<>(Arrays.asList(
                "p_branch=master",
                "p_isDevMode=true"
        ));

        WorkflowJob job = jenkinsRule.createProject(WorkflowJob.class, "Test Job");

        ParameterDefinition paramDef0 = new StringParameterDefinition("dummyParam", "dummyValue", "");
        ParameterDefinition paramDef1 = new StringParameterDefinition("branch", "master", "");
        ParameterDefinition paramDef2 = new BooleanParameterDefinition("isDevMode", true, "");
        ParametersDefinitionProperty prop = new ParametersDefinitionProperty(paramDef0, paramDef1, paramDef2);
        job.addProperty(prop);

        WavefrontJobProperty junitReportProperty = mock(WavefrontJobProperty.class);
        String jobParametersToSend = "branch\nisDevMode";
        when(junitReportProperty.getJobParameters()).thenReturn(jobParametersToSend);
        job.addProperty(junitReportProperty);

        job.setDefinition(new CpsFlowDefinition("node {\n" +
                "}", true));

        jenkinsRule.buildAndAssertSuccess(job);

        List<String> messages = proxy.terminate();
        String actual = messages.get(0).replaceAll("[\"]", "");

        String message = "The expected point tags are not present";
        for (String e : expected) {
            if (!actual.contains(e)) {
                Assert.fail(message);
            }
        }
    }

    @Test
    public void testWavefrontJobPropertyForPipelineFailedTests() throws IOException, InterruptedException {
        List<String> expected = new ArrayList<>(Arrays.asList(
                jobMetricPrefix + ".job-with-test-reports 20000.0 source=localhost Status=FAILURE Build-Number=1",
                jobMetricPrefix + ".junit.job-with-test-reports 127000.0 source=localhost",
                jobMetricPrefix + ".junit.job-with-test-reports.failcount 10.0 source=localhost",
                jobMetricPrefix + ".junit.job-with-test-reports.skipcount 10.0 source=localhost",
                jobMetricPrefix + ".junit.job-with-test-reports.passcount 80.0 source=localhost",
                jobMetricPrefix + ".junit.job-with-test-reports.totalcount 100.0 source=localhost",
                jobMetricPrefix + ".junit.test.java.com.vmware.devops.wavefront.testingsendingmetricsfailedtest" +
                        " 7000.0 source=localhost Build-Number=1 Job-Name=job-with-test-reports Test-Status=Failed",
                jobMetricPrefix + ".junit.test.java.com.vmware.devops.wavefront.testingsendingmetricsfailedtest2" +
                        " 120000.0 source=localhost Build-Number=1 Job-Name=job-with-test-reports Test-Status=Failed"

        ));
        TestResult actionResult = mock(TestResult.class);
        when(actionResult.getDuration()).thenReturn(127f);

        List<CaseResult> failedTests = getMockedFailedTests();

        TestResultAction action = mock(TestResultAction.class);
        when(action.getResult()).thenReturn(actionResult);
        when(actionResult.getFailedTests()).thenReturn(failedTests);
        when(action.getSkipCount()).thenReturn(10);
        when(action.getFailCount()).thenReturn(10);
        when(action.getTotalCount()).thenReturn(100);

        Run run = getRun();
        when(run.getAction(TestResultAction.class)).thenReturn(action);
        WavefrontBuildListener buildLister = new WavefrontBuildListener();
        configureRun(run, "job-with-test-reports", 20000L, Result.FAILURE, "1", true, false);
        buildLister.onCompleted(run, mock(TaskListener.class));
        List<String> messages = proxy.terminate();

        checkExpectedResult(expected, messages);
    }

    @Test
    public void testWavefrontJobPropertyForPipelineSkippedAndPassedTests() throws IOException, InterruptedException {
        List<String> expected = new ArrayList<>(Arrays.asList(
                jobMetricPrefix + ".job-with-test-reports 20000.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".junit.job-with-test-reports 14000.0 source=localhost",
                jobMetricPrefix + ".junit.test.java.com.vmware.devops.wavefront.testingsendingmetricsskippedtest" +
                        " 7000.0 source=localhost Build-Number=1 Job-Name=job-with-test-reports Test-Status=Skipped",
                jobMetricPrefix + ".junit.test.java.com.vmware.devops.wavefront.testingsendingmetricspassedtest" +
                        " 7000.0 source=localhost Build-Number=1 Job-Name=job-with-test-reports Test-Status=Passed"

        ));
        TestResult actionResult = mock(TestResult.class);
        when(actionResult.getDuration()).thenReturn(14f);
        doReturn(mockTestResults("test.java.com.vmware.devops.wavefront.testingSendingMetricsSkippedTest", 7f)).when(actionResult).getSkippedTests();
        doReturn(mockTestResults("test.java.com.vmware.devops.wavefront.testingSendingMetricsPassedTest", 7f)).when(actionResult).getPassedTests();

        List<CaseResult> failedTests = new ArrayList<>();
        TestResultAction action = mock(TestResultAction.class);
        when(action.getResult()).thenReturn(actionResult);
        when(action.getFailedTests()).thenReturn(failedTests);

        Run run = getRun();
        when(run.getAction(TestResultAction.class)).thenReturn(action);
        WavefrontBuildListener buildLister = new WavefrontBuildListener();
        configureRun(run, "job-with-test-reports", 20000L, Result.SUCCESS, "1", true, false);
        buildLister.onCompleted(run, mock(TaskListener.class));
        List<String> messages = proxy.terminate();

        checkExpectedResult(expected, messages);
    }

    @Test
    public void testWavefrontJacocoDataSending() throws IOException, InterruptedException {
        List<String> expected = new ArrayList<>(Arrays.asList(
                jobMetricPrefix + ".job-with-jacoco-metrics 20000.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.instructions-coverage 85.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.branch-coverage 85.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.complexity-coverage 85.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.line-coverage 85.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.method-coverage 85.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.class-coverage 85.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.instructions-coverage.minimum 50.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.branch-coverage.minimum 60.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.complexity-coverage.minimum 70.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.line-coverage.minimum 80.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.method-coverage.minimum 80.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.class-coverage.minimum 80.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.instructions-coverage.maximum 100.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.branch-coverage.maximum 100.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.complexity-coverage.maximum 100.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.line-coverage.maximum 100.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.method-coverage.maximum 100.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.class-coverage.maximum 100.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.instructions-coverage.covered 0.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.branch-coverage.covered 0.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.complexity-coverage.covered 0.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.line-coverage.covered 0.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.method-coverage.covered 0.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.class-coverage.covered 0.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.instructions-coverage.total 0.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.branch-coverage.total 0.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.complexity-coverage.total 0.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.line-coverage.total 0.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.method-coverage.total 0.0 source=localhost Status=SUCCESS Build-Number=1",
                jobMetricPrefix + ".job-with-jacoco-metrics.jacoco.class-coverage.total 0.0 source=localhost Status=SUCCESS Build-Number=1"

        ));

        JacocoHealthReportThresholds thresholds = mock(JacocoHealthReportThresholds.class);
        when(thresholds.getMinInstruction()).thenReturn(50);
        when(thresholds.getMinBranch()).thenReturn(60);
        when(thresholds.getMinComplexity()).thenReturn(70);
        when(thresholds.getMinLine()).thenReturn(80);
        when(thresholds.getMinMethod()).thenReturn(80);
        when(thresholds.getMinClass()).thenReturn(80);

        when(thresholds.getMaxInstruction()).thenReturn(100);
        when(thresholds.getMaxBranch()).thenReturn(100);
        when(thresholds.getMaxComplexity()).thenReturn(100);
        when(thresholds.getMaxLine()).thenReturn(100);
        when(thresholds.getMaxMethod()).thenReturn(100);
        when(thresholds.getMaxClass()).thenReturn(100);

        Coverage coverage = mock(Coverage.class);
        when(coverage.getPercentage()).thenReturn(85);

        JacocoBuildAction jacocoAction = mock(JacocoBuildAction.class);
        when(jacocoAction.getThresholds()).thenReturn(thresholds);
        when(jacocoAction.getInstructionCoverage()).thenReturn(coverage);
        when(jacocoAction.getBranchCoverage()).thenReturn(coverage);
        when(jacocoAction.getComplexityScore()).thenReturn(coverage);
        when(jacocoAction.getLineCoverage()).thenReturn(coverage);
        when(jacocoAction.getMethodCoverage()).thenReturn(coverage);
        when(jacocoAction.getClassCoverage()).thenReturn(coverage);

        Run run = getRun();
        when(run.getAction(JacocoBuildAction.class)).thenReturn(jacocoAction);
        WavefrontBuildListener buildLister = new WavefrontBuildListener();
        configureRun(run, "job-with-jacoco-metrics", 20000L, Result.SUCCESS, "1", false, true);
        buildLister.onCompleted(run, mock(TaskListener.class));
        List<String> messages = proxy.terminate();

        checkExpectedResult(expected, messages);
    }

    private void checkExpectedResult(List<String> expected, List<String> messages) {
        Set<String> actualMetrics = parseMessages(messages);
        boolean result = actualMetrics.containsAll(expected);
        if (!result) {
            expected.removeAll(actualMetrics);
            String message = "Messages above are missing, not as expected:";
            for (String metricName : expected) {
                message += "\n" + metricName;
            }
            Assert.fail(message);
        }
    }

    private List<CaseResult> getMockedFailedTests() {
        List<CaseResult> failedTests = new ArrayList<>();
        CaseResult failedTest = mock(CaseResult.class);
        when(failedTest.getFullDisplayName()).thenReturn("test.java.com.vmware.devops.wavefront.testingSendingMetricsFailedTest");
        when(failedTest.getDuration()).thenReturn(7f);
        failedTests.add(failedTest);
        CaseResult failedTest2 = mock(CaseResult.class);
        when(failedTest2.getFullDisplayName()).thenReturn("test.java.com.vmware.devops.wavefront.testingSendingMetricsFailedTest2");
        when(failedTest2.getDuration()).thenReturn(120f);
        failedTests.add(failedTest2);
        return failedTests;
    }

    private List<hudson.tasks.test.TestResult> mockTestResults(String testName, float testDuration) {
        List<hudson.tasks.test.TestResult> tests = new ArrayList<>();
        hudson.tasks.test.TestResult result = mock(hudson.tasks.test.TestResult.class);
        when(result.getFullDisplayName()).thenReturn(testName);
        when(result.getDuration()).thenReturn(testDuration);
        tests.add(result);
        return tests;
    }

    private Run getRun() {
        Job job = mock(Job.class);
        Run run = mock(Run.class);
        when(run.getParent()).thenReturn(job);
        return run;
    }

    private void configureRun(Run run, String jobName, long duration, Result result, String buildNumber, boolean junitProperty, boolean jacocoProperty) {
        when(run.getParent().getFullName()).thenReturn(jobName);
        when(run.getDuration()).thenReturn(duration);
        when(run.getResult()).thenReturn(result);
        when(run.getId()).thenReturn(buildNumber);
        WavefrontJobProperty junitReportProperty = mock(WavefrontJobProperty.class);
        when(junitReportProperty.isEnableSendingJunitReportData()).thenReturn(junitProperty);
        when(junitReportProperty.isEnableSendingJacocoReportData()).thenReturn(jacocoProperty);
        when(run.getParent().getProperty(WavefrontJobProperty.class)).thenReturn(junitReportProperty);
    }

    private Set<String> parseMessages(List<String> messages) {
        Set<String> result = new HashSet<>();
        for (String message : messages) {
            String toRemove = message.split(" ")[2];
            message = message.replaceAll(toRemove + " ", "").replaceAll("[\"]", "");
            result.add(message);
        }
        return result;
    }
}
