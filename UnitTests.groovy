@Library('payara') _
env.domain_name = 'test-domain'
def profiles = "payara-server-remote,ui-test"

pipeline {
    agent any

    options {
        quietPeriod 120
    }

    stages {
        stage('Maven Info') {
            steps {
                sh "mvn -V -B -N -P$profiles help:all-profiles"
                script {
                    currentBuild.description = "Working on git commit $env.GIT_COMMIT"
                }
            }
        }
        stage('Maven Verify - Tests') {
            steps {
                startPayara()
                withMaven {
                    sh """/bin/bash -pl
                    MAVEN_OPTS="$JAVA_TOOL_OPTIONS"
                    unset JAVA_TOOL_OPTIONS
                    mvn -B verify -fae -P$profiles \
                    -Dmaven.test.failure.ignore=true -DtrimStackTrace=false \
                    -Dmaven.install.skip=true -DadminPort=$env.admin_port
                    """
                }
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: '**/payara5/**/server.log*'
            stopPayara()
        }
    }
}
