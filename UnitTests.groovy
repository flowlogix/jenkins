@Library('payara') _l1
final def profiles = "?payara-server-remote,?ui-test,?coverage,?ci"
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
                sh "mvn -V -B -C -N -P$profiles help:all-profiles \
                    -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
                script {
                    currentBuild.description = "Working on git commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                    if (env.GIT_URL.contains('shiro')) {
                        payara_config.jacoco_expr_args = '-pl :jakarta-ee-support'
                        payara_config.force_start = true
                        payara_config.jacoco_tcp_server = false
                    }
                }
            }
        }
        stage('Maven Verify - Tests') {
            steps {
                startPayara payara_config
                withMaven(options: [ jacocoPublisher(disabled: true) ]) {
                    sh """
                    export MAVEN_OPTS="\$MAVEN_OPTS $JAVA_TOOL_OPTIONS"
                    unset JAVA_TOOL_OPTIONS
                    mvn -B -C verify -fae -P$profiles \$(eval echo \$MAVEN_ADD_OPTIONS) \
                    -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn \
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
