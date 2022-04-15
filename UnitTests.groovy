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

    triggers {
        issueCommentTrigger('.*jenkins test.*')
    }

    stages {
        stage('Bootstrap Guard') {
            when {
                not {
                    triggeredBy cause: 'UserIdCause'
                }
            }
            steps {
                script {
                    if (env.CHANGE_ID && !env.GITHUB_COMMENT) {
                        githubNotify description: 'Project Member can initiate a build',
                            context: ci_context, status: 'PENDING'
                        currentBuild.result = 'not_built'
                        currentBuild.description = 'Boostrap Build Only - Initial Build Not Started'
                        error currentBuild.description
                    }
                }
            }
        }
        stage('Maven Info') {
            steps {
                githubNotify description: 'Build and Test Started',
                    context: ci_context, status: 'PENDING'

                guardDuplicateBuilds guardParameters, {
                    sh "mvn -V -B -N -P$profiles help:all-profiles"
                }
                script {
                    currentBuild.description = "Working on git commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                }
            }
        }
        stage('Maven Verify - Tests') {
            steps {
                guardDuplicateBuilds guardParameters, {
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
    }
    post {
        always {
            archiveArtifacts artifacts: '**/payara5/**/server.log*', allowEmptyArchive: true
            stopPayara payara_config
        }
        success {
            githubNotify description: 'Build and Test Success',
                context: ci_context, status: 'SUCCESS'
        }
        unstable {
            githubNotify description: 'Test Failure',
                context: ci_context, status: 'FAILURE'
        }
        failure {
            githubNotify description: 'Build Failed',
                context: ci_context, status: 'ERROR'
        }
        aborted {
            githubNotify description: 'Build and Test Aborted',
                context: ci_context, status: 'ERROR'
        }
    }
}
