@Library('payara') _l1
@Library('util') _l2
final def profiles           = '?payara-server-remote,?coverage,?all-tests'
final def profiles_no_stress = '?payara-server-remote,?coverage,?ui-test'
def payara_config = [ domain_name : 'test-domain', jacoco_profile : profiles ]
def mvnCommandLine

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
                sh "mvn -V -B -ntp -C -N -P$profiles help:all-profiles"
                script {
                    currentBuild.description = "Working on git commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                }
            }
        }
        stage ('Set up Payara and Maven') {
            steps {
                startPayara payara_config
                withMaven {
                    script {
                        mvnCommandLine =
                            """
                                export MAVEN_OPTS="\$(eval echo \$MAVEN_OPTS \$JAVA_TOOL_OPTIONS)"
                                unset JAVA_TOOL_OPTIONS
                                mvn -B -ntp -C -fae \$(eval echo \$MAVEN_ADD_OPTIONS) \
                                -Dwebdriver.chrome.binary="\$(eval echo \$CHROME_BINARY)" \
                                -Dmaven.test.failure.ignore=true -DtrimStackTrace=false \
                                -Dmaven.install.skip=true -DadminPort=$payara_config.admin_port \
                                -DsslPort=$payara_config.ssl_port -DjacocoPort=$payara_config.jacoco_port \
                            """
                    }
                }
            }
        }
        stage('Maven Verify - All Tests') {
            steps {
                withMaven(options: [ jacocoPublisher(disabled: true) ]) {
                    sh "$mvnCommandLine verify -P${profiles}"
                }
            }
        }
        stage('Maven Deploy Docs and Snapshots') {
            when {
                expression { currentBuild.currentResult == 'SUCCESS' }
            }
            steps {
                sh """
                set +x; . "$HOME/.sdkman/bin/sdkman-init.sh"
                sdk use maven 3.9.0
                set -x
                mvn -B -C jar:jar javadoc:jar source:jar-no-fork \
                org.sonatype.plugins:nexus-staging-maven-plugin:deploy \
                -fae -Dmaven.install.skip=true
                """
            }
        }
    }

    post {
        always {
            stopPayara payara_config
            archiveArtifacts artifacts: '**/logs/server.log*'
            checkLogs payara_config.asadmin ? '**/logs/server.log*' : null
        }
        success {
            githubNotify description: 'Deploy Snapshots', context: 'CI/Deploy', status: 'SUCCESS',
                targetUrl: 'https://oss.sonatype.org/content/repositories/snapshots/com/flowlogix/'
        }
        changed {
            mail to: "lprimak@hope.nyc.ny.us", subject: "Jenkins: Project name -> ${env.JOB_NAME}",
            body: "<b>Jenkins Build Status Change [${currentBuild.currentResult}]</b><br>" +
                  "Project: ${env.JOB_NAME} <br>Build Number: ${env.BUILD_NUMBER} <br>Build URL: ${env.BUILD_URL}",
            charset: 'UTF-8', mimeType: 'text/html'
        }
    }
}
