@Library('util') _l2
def jbake_maven_project = 'jbake-maven'

pipeline {
    agent any
    options {
        quietPeriod 0
    }
    stages {
        stage('Deploy documentation') {
            when {
                allOf {
                    anyOf {
                        branch "main"
                        branch "master"
                    }
                }
            }
            steps {
                sh """
                export MAVEN_OPTS="\$MAVEN_OPTS --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
                    --add-opens java.base/java.io=ALL-UNNAMED"
                mvn -B -C -ntp process-resources -Dsass.skip=true -f ${env.WORKSPACE}/docs/${jbake_maven_project}/

                rsync -aH --delete-after ${env.WORKSPACE}/docs/$jbake_maven_project/target/output/ \
                    ${websiteHost()}:/var/flowlogix/html/docs/
                """
            }
        }
    }
}
