@Library('payara') _l1
final def profiles = "payara-server-remote,ui-test"
def payara_config = [domain_name : 'test-domain']
def ci_context = 'CI/unit-tests/pr-merge'

@Library('util') _l2
def guardParameters = [ : ]

pipeline {
    agent any

    options {
        quietPeriod 0
    }

    stages {
        stage('Maven Info') {
            steps {
                sh "mvn -V -B -N -P$profiles help:all-profiles"
                script {
                    currentBuild.description = "Working on git commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                }
            }
        }
        stage('Maven Verify - Tests') {
            steps {
                startPayara payara_config
                withMaven {
                    sh """
                    export MAVEN_OPTS="\$MAVEN_OPTS $JAVA_TOOL_OPTIONS"
                    unset JAVA_TOOL_OPTIONS
                    mvn -B verify -fae -P$profiles \$(eval echo \$MAVEN_ADD_OPTIONS) \
                    -Dwebdriver.chrome.binary="\$(eval echo \$CHROME_BINARY)" \
                    -Dmaven.test.failure.ignore=true -DtrimStackTrace=false \
                    -Dmaven.install.skip=true -DadminPort=$payara_config.admin_port
                    """
                }
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: '**/payara5/**/server.log*', allowEmptyArchive: true
            stopPayara payara_config
        }
    }
}
