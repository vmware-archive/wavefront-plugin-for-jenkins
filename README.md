![wavefront-logo](https://user-images.githubusercontent.com/56251894/67003694-32172500-f0e7-11e9-92b2-7952d76f84d9.png)
# Wavefront Plugin for Jenkins

### Content
- [Overview](#overview)
- [What is Wavefront Plugin for Jenkins](#what-is-wavefront-plugin-for-jenkins)
- [Prerequisites](#prerequisites)
- [Provided metrics](#provided-metrics)
- [Configuration](#configuration)
- [Contributing](#contributing)
- [License](#license)

## Overview

Wavefront offers a real-time metrics monitoring and streaming analytics platform designed for developers to optimize their clouds and modern applications that rely on containers and microservices.
A cloud-hosted service, Wavefront offers Digital Enterprises the ability to send time-series (metric) data from anywhere in their data center and to perform queries, render charts to see analytics,
anomalies or KPI dashboards. (For more information: [Wavefront](https://cloud.vmware.com/wavefront))

## What is Wavefront Plugin for Jenkins?

Wavefront Plugin for Jenkins is monitoring plugin which sends different kinds of metrics about Jenkins itself to Wavefront.
The plugin provides in-depth information about how the system is working in form of statistics.
Using the notification hooks Jenkins provides the plugin sends the metrics as soon as they are available.
It removes the need for external monitoring solutions that poll recurrently the system for data as these solutions can lead to degradation of Jenkins performance.

## Prerequisites

Before installing Wavefront Plugin for Jenkins on your instance you need to install the Wavefront proxy from [here](https://docs.wavefront.com/proxies_installing.html)

## Provided metrics

There are 2 kinds of metric data that the plugin process:
* Jenkins system and node label metrics - these are sent periodically every 5 minutes by default. This can be customized from the [*Wavefront plugin configuration*](#configuration) page (See *Flush Interval* field).
* Job and pipeline metrics - these are sent on job run completion.

#### Metrics types prefixes
Jenkins system and node label metrics are prefixed and sent to Wavefront with ***wavefront.jenkins.plugin*** and
job metrics are prefixed with ***wavefront.jenkins.plugin.job*** prefix by default. These prefixes can be customized from the *Wavefront plugin configuration* page.

#### Optional metrics
You can set whether to send JUnit and Jacoco reports from the respective job properties.

![job-property](https://user-images.githubusercontent.com/56251894/67005222-d2bb1400-f0ea-11e9-813c-b8ada4b20a0f.png)

#### Available metrics:
1.	**Jenkins system** – System CPU (This value is a double in the [0.0, 1.0] interval), Total physical memory (in bytes), Free physical memory (in bytes), Max heap memory (in bytes), Used heap memory (in bytes). Metric name: *\<metric-prefix\>.system-cpu*. List:
	* *wavefront.jenkins.plugin.system-cpu*
	* *wavefront.jenkins.plugin.total-physical-memory*
	* *wavefront.jenkins.plugin.free-physical-memory*
	* *wavefront.jenkins.plugin.max-heap-memory*
	* *wavefront.jenkins.used-heap-memory*
 
2.	**Label nodes** – For each node label the plugin sends number of available executors, busy executors, connecting executors, defined executors, idle executors, online executors and queue length. Metric name: *\<metric-prefix\>.label.available-executors*. List:
	* *wavefront.jenkins.plugin.label.available-executors*
	* *wavefront.jenkins.plugin.label.busy-executors*
	* *wavefront.jenkins.plugin.label.connecting-executors*
	* *wavefront.jenkins.plugin.label.defined-executors*
	* *wavefront.jenkins.plugin.label.idle-executors*
	* *wavefront.jenkins.plugin.label.online-executors*
	* *wavefront.jenkins.plugin.label.queue-length*

3.	**Job metrics** – Total duration (in milliseconds) of the job, status and build number. Metric name: *\<job-metric-prefix\>.jobname*. Tags: *job status, build number.* List:
	* *wavefront.jenkins.plugin.job.jobname*

4.	**Pipeline metrics (stages and parallel branches)** – In addition to the job metrics, duration (in milliseconds) for each stage and branch in parallel step. Metric name: *\<job-metric-prefix\>.jobname.{stage, parralel}.stagename*. Tags: *job status, build number.* List:
	* *wavefront.jenkins.plugin.job.jobname.stage.stagename*
    * *wavefront.jenkins.plugin.job.jobname.parallel.branchname*
    
5.	**JUnit report** – If it's enabled, duration (in milliseconds) for each JUnit test per job (not send by default, needs Jenkins JUnit plugin). Metric name: *\<job-metric-prefix\>.junit.full.path.to.test*. Tags: *job name, build number, test status.* List:
	* *wavefront.jenkins.plugin.job.junit.com.vmware.plugins.testclass.testingmethod*

6.	**Jacoco report** – If it's enabled, sends minimum, maximum, covered and total number of instructions-coverage, branch-coverage, complexity-coverage, line-coverage, method-coverage and class-coverage per job (not sent by default, needs Jenkins Jacoco plugin). Metric name: *\<job-metric-prefix\>.jobname.jacoco.line-coverage.minimum*. Tags: *job status, build number.* List:
	* *wavefront.jenkins.plugin.job.jobname.jacoco.instructions-coverage.{minimum, maximum, covered, total}*
	* *wavefront.jenkins.plugin.job.jobname.jacoco.branch-coverage.{minimum, maximum, covered, total}*
	* *wavefront.jenkins.plugin.job.jobname.jacoco.complexity-coverage.{minimum, maximum, covered, total}*
	* *wavefront.jenkins.plugin.job.jobname.jacoco.line-coverage.{minimum, maximum, covered, total}*
	* *wavefront.jenkins.plugin.job.jobname.jacoco.method-coverage.{minimum, maximum, covered, total}*
	* *wavefront.jenkins.plugin.job.jobname.jacoco.class-coverage.{minimum, maximum, covered, total}*

7.  **Custom step (wavefrontTimedCall)** – The wavefrontTimedCall step measure duration (in milliseconds) in given block. Syntax: *wavefrontTimedCall(“metricName”) {…}.* Metric name: *\<job-metric-prefix\>.step.metricname*. Tags: *job name, build number.* List:
    * *wavefront.jenkins.plugin.job.step.metricname*

## Configuration

Plugin can be configured from Jenkins UI.

![manage-plugin](https://user-images.githubusercontent.com/56251894/67005281-ee261f00-f0ea-11e9-8d9a-5e0cc8beb496.png)

The plugin can be configured from the *Wavefront plugin configuration* section under Jenkins management page.
From *Wavefront proxy configuration section* we need to set up:
* Wavefront Proxy hostname and port
* Metric prefixes ([see](#metrics-types-prefixes))
* Send metrics recurrence interval

If proxy hostname is not specified (left empty) then no metrics will be sent to Wavefront.

![plugin-configuration](https://user-images.githubusercontent.com/56251894/67005306-fd0cd180-f0ea-11e9-8985-e53a3984f83d.png)

## Contributing

The wavefront-plugin-for-jenkins project team welcomes contributions from the community. Before you start working with wavefront-plugin-for-jenkins, please read our Developer Certificate of Origin.
All contributions to this repository must be signed as described on that page.
Your signature certifies that you wrote the patch or have the right to pass it on as an open-source patch. For more detailed information, refer to CONTRIBUTING.md.

## License

Copyright: Copyright 2015-2019 VMware, Inc. All Rights Reserved.

SPDX-License-Identifier: https://spdx.org/licenses/MIT.html
