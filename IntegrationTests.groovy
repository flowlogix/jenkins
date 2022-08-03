@Library('payara') _l1
@Library('util') _l2
def payara_config = [domain_name : 'test-domain']
final def profiles = 'all-tests,payara-server-remote'

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
        stage('Maven Verify - All Tests') {
            steps {
                startPayara payara_config
                withMaven {
                    sh """
                       export MAVEN_OPTS="\$MAVEN_OPTS $JAVA_TOOL_OPTIONS"
                       unset JAVA_TOOL_OPTIONS
                       mvn -B -C verify -P$profiles -fae \$(eval echo \$MAVEN_ADD_OPTIONS) \
                       -Dwebdriver.chrome.binary="\$(eval echo \$CHROME_BINARY)" \
                       -Dmaven.test.failure.ignore=true -DtrimStackTrace=false \
                       -Ddocs.phase=package -Dmaven.install.skip=true -DadminPort=$payara_config.admin_port
                       """
                }
            }
        }
        stage('Maven Deploy Snapshots') {
            when {
                expression { currentBuild.currentResult == 'SUCCESS' }
            }
            steps {
                sh """
                mvn -B -C jar:jar javadoc:jar source:jar-no-fork \
                org.sonatype.plugins:nexus-staging-maven-plugin:deploy \
                -P$profiles -fae -Dmaven.install.skip=true
                """
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '**/payara5/**/server.log*'
            checkLogs()
            stopPayara payara_config
        }
        success {
            githubNotify description: 'Deploy Snapshots', context: 'CI/Deploy', status: 'SUCCESS',
                targetUrl: 'https://oss.sonatype.org/content/repositories/snapshots/com/flowlogix/'
        }
    }
}
