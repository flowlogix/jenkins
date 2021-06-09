@Library('payara') _
env.domain_name = 'prod-domain'
def profiles = 'all-tests,payara-server-remote'

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
    }
    parameters {
        booleanParam(name: 'releaseInMaven', defaultValue: true,
            description: 'Whether to release in Maven Central, or just close the repository')
        string(name: 'Version', description: 'Version number to release', trim: true)
    }

    stages {
        stage('Maven Info') {
            steps {
                script {
                    if (Version.empty) {
                        error 'Version cannot be empty'
                    }
                }
                sh "mvn -V -N -P$profiles help:all-profiles"
                script {
                    currentBuild.description = "Working on git commit $env.GIT_COMMIT"
                }
            }
        }
        stage('Maven - Release') {
            steps {
                startPayara()
                withMaven {
                    sh """
                    mvn -B -P$profiles release:prepare release:perform -DreleaseVersion=$Version \
                    -Darguments=\"-Dauto.release=$releaseInMaven -DtrimStackTrace=false \
                    -Dmaven.install.skip=true -DadminPort=$env.admin_port\"
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
        success {
            sh "git push origin Version-$Version"
        }
        failure {
            sh "git tag -d Version-$Version || true"
        }
    }
}
