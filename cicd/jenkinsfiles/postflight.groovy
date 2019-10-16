// DEV_SOURCE = bellevue-ci/tools/jenkins/jobs/bellevue-ci-libraries/pipelines/postflight.groovy

@Library("bellevue-ci-libraries") _


import com.vmware.devops.GitLabProjects
import com.vmware.devops.Maven

import static com.vmware.devops.GitHelpers.*;

node("default") {
    stage("Checkout") {
        dir("wavefront-jenkins-plugin") {
            gitlabCheckout(GitLabProjects.WAVEFRONT_JENKINS_PLUGIN)
            sh("git clean -fdx")
            changeset = sh(script: "git rev-parse HEAD", returnStdout: true).trim()
        }
    }

    identifiers = ["changeset": changeset]
    if (findBuildAndMarkAlreadyBuilt(identifiers)) {
        return
    }

    stage("Prepare environment") {
        try {
            env.JAVA_HOME = "${tool '1.8.latest'}"
            env.MVN_HOME = "${tool 'mvn_latest'}"
            env.PATH = "${env.JAVA_HOME}/bin:${env.MVN_HOME}/bin:${env.PATH}"
        } catch (any) {
            addErrorBadge(text: env.STAGE_NAME)
            throw any
        }
    }

    stage("Run tests") {
        try {
            sh("""cd wavefront-jenkins-plugin
mvn clean deploy -e -B ${new Maven().defaultMavenRepoLocalArg} \
${new Maven().symphonyMavenLocalSettingsArg} \
-DaltDeploymentRepository=symphony-maven-local::default::https://build-artifactory.eng.vmware.com/artifactory/symphony-maven-local
                    """)
        } catch (any) {
            addErrorBadge(text: env.STAGE_NAME)
            throw any
        }
    }
}