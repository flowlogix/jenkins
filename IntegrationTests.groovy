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
                script {
                    currentBuild.description = "Working on git commit $env.GIT_COMMIT"
                }
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
        stage('Maven Deply Snapshots') {
            when {
                expression { currentBuild.currentResult == 'SUCCESS' }
            }
            steps {
                sh """\
		mvn -B jar:jar \
		org.sonatype.plugins:nexus-staging-maven-plugin:deploy \
		-P$profiles -fae -Dmaven.install.skip=true \
                """
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '**/payara5/**/server.log*'
            stopPayara()
        }
        success {
            githubNotify description: 'Deploy Snapshots', context: 'CI/Deploy', status: 'SUCCESS',
                targetUrl: 'https://oss.sonatype.org/content/repositories/snapshots/com/flowlogix/'
        }
    }
}
