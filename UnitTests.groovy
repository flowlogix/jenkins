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
            }
        }
        stage('Maven Verify - Tests') {
            steps {
                startPayara()
                withMaven {
                    sh """\
                    MAVEN_OPTS="$JAVA_TOOL_OPTIONS" \
                    env -u JAVA_TOOL_OPTIONS \
                    mvn -B verify -fae -P$profiles \
                    -Dmaven.test.failure.ignore=true -DtrimStackTrace=false \
                    -Dmaven.install.skip=true -DadminPort=$env.admin_port \
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
