@Library('payara') _l1
final def profiles = "payara-server-remote,ui-test,coverage,ci"
def payara_config = [ domain_name : 'test-domain', jacoco_profile : profiles ]
def extra_build_options = ''

@Library('util') _l2

pipeline {
    agent any

    options {
        quietPeriod 0
    }

    stages {
        stage('Maven Info') {
            steps {
                sh "mvn -V -B -ntp -C -N -P$profiles help:all-profiles"
                script {
                    currentBuild.description = "Commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                    if (env.GIT_URL.contains('shiro')) {
                        shiroPayaraConfig payara_config
                    }
                }
            }
        }
        stage('Start Payara') {
            steps {
                startPayara payara_config
            }
        }
        stage('Maven Verify - Tests') {
            steps {
                withMaven(options: [ jacocoPublisher(disabled: true) ]) {
                    sh """
                    export MAVEN_OPTS="\$MAVEN_OPTS $JAVA_TOOL_OPTIONS"
                    unset JAVA_TOOL_OPTIONS
                    mvn -B -ntp -C verify -fae -P$profiles \$(eval echo \$MAVEN_ADD_OPTIONS) \
                    -Dwebdriver.chrome.binary="\$(eval echo \$CHROME_BINARY)" \
                    -Dmaven.test.failure.ignore=true -DtrimStackTrace=false \
                    -Dmaven.install.skip=true -DadminPort=$payara_config.admin_port \
                    -DsslPort=$payara_config.ssl_port -DjacocoPort=$payara_config.jacoco_port \
                    $extra_build_options"""
                }
            }
        }
    }
    post {
        always {
            stopPayara payara_config
            archiveArtifacts artifacts: '**/logs/server.log*', allowEmptyArchive: true
            checkLogs payara_config.asadmin ? '**/logs/server.log*' : null
        }
    }
}
