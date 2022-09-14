@Library('payara') _l1
@Library('util') _l2
def payara_config = [domain_name : 'test-domain']
final def profiles           = 'payara-server-remote,all-tests'
final def profiles_no_stress = 'payara-server-remote,ui-test'
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
                sh "mvn -V -B -C -N -P$profiles help:all-profiles"
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
                                mvn -B -C -fae \$(eval echo \$MAVEN_ADD_OPTIONS) \
                                -Dwebdriver.chrome.binary="\$(eval echo \$CHROME_BINARY)" \
                                -Dmaven.test.failure.ignore=true -DtrimStackTrace=false \
                                -Dmaven.install.skip=true -DadminPort=$payara_config.admin_port \
                                -DsslPort=$payara_config.ssl_port \
                            """
                    }
                }
            }
        }
        stage('Maven Verify - All Tests') {
            steps {
                withMaven {
                    sh "$mvnCommandLine verify -P${profiles}"
                }
            }
        }
        stage('Maven Test - Client state saving') {
            steps {
                withMaven(options: [artifactsPublisher(disabled: true)]) {
                    sh "$mvnCommandLine verify -P${profiles_no_stress} -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false \
                    -Dintegration.test.mode=clientStateSaving"
                }
            }
        }
        stage('Maven Test - Shiro-native sessions') {
            steps {
                withMaven(options: [artifactsPublisher(disabled: true)]) {
                    sh "$mvnCommandLine verify -P${profiles_no_stress} -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false \
                    -Dintegration.test.mode=shiroNativeSessions"
                }
            }
        }
        stage('Maven Test - Disable Shiro-EE') {
            steps {
                withMaven(options: [artifactsPublisher(disabled: true)]) {
                    sh "$mvnCommandLine verify -P${profiles_no_stress} -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false \
                    -Dit.test=LookupIT,ExceptionPageIT -Dintegration.test.mode=disableShiroEE"
                }
            }
        }
        stage('Maven Deploy Docs and Snapshots') {
            when {
                expression { currentBuild.currentResult == 'SUCCESS' }
            }
            steps {
                sh """
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
    }
}
