//mvn clean install ...

@Library("bellevue-ci-libraries") _

import com.vmware.devops.JUnit
import com.vmware.devops.Maven

identifiers = [
        "gerritRefSpec" : env.GERRIT_REFSPEC
]

if (findBuildAndMarkAlreadyBuilt(identifiers)) {
    return
}

node("default") {
    stage("Checkout") {
        dir("wavefront-jenkins-plugin") {
            gerritCheckout("wavefront-jenkins-plugin")
            sh("git clean -fdx")
        }
    }

    stage ("Prepare environment") {
        try {
            env.JAVA_HOME="${tool '1.8.latest'}"
            env.MVN_HOME="${tool 'mvn_latest'}"
            env.PATH="${env.JAVA_HOME}/bin:${env.MVN_HOME}/bin:${env.PATH}"
        } catch (any) {
            addErrorBadge(text: env.STAGE_NAME)
            gerritPostVerified(result: -1) // MiCgrate to step
            throw any
        }
    }

    stage("Run tests") {
        try {
            sh("""cd wavefront-jenkins-plugin
mvn clean install -e -B ${new Maven().defaultMavenRepoLocalArg}
""")
        } catch (any) {
            addErrorBadge(text: env.STAGE_NAME)
            gerritPostVerified(result: -1)
            junit(JUnit.archivePattern)
            throw any
        }
    }

    gerritPostVerified()
    junit(JUnit.archivePattern)
}