@Library('payara') _
env.domain_name = 'test-domain'
def profiles = 'all-tests,payara-server-remote'

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        quietPeriod 0
    }
    triggers {
        pollSCM('@daily')
    }

    stages {
        stage('Maven Info') {
            steps {
                sh "mvn -V -B -N -P$profiles help:all-profiles"
            }
        }
        stage('Maven Verify - All Tests') {
            steps {
                startPayara()
                withMaven {
                    sh """\
                    MAVEN_OPTS="$JAVA_TOOL_OPTIONS" \
                    env -u JAVA_TOOL_OPTIONS \
                    mvn -B verify -P$profiles -fae \
                    -Dmaven.test.failure.ignore=true -DtrimStackTrace=false \
                    -Dmaven.install.skip=true -DadminPort=$env.admin_port \
                    """
                }
            }
        }
        stage('Deploy Snapshot Release') {
            steps {
                sh """\
                mvn -B deploy -P$profiles -DtrimStackTrace=false \
                -Dmaven.install.skip=true -DadminPort=$env.admin_port \
                -Dmaven.test.skip=true -DskipITs=true \
                """
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
