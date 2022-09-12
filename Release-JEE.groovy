@Library('payara') _l1
@Library('util') _l2

def payara_config = [domain_name : 'prod-domain']
final def profiles = 'all-tests,payara-server-remote'

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
                sh "mvn -V -N -B -C -P$profiles help:all-profiles"
                script {
                    currentBuild.description = "Working on git commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                }
            }
        }
        stage('Maven - Release') {
            steps {
                startPayara payara_config
                withMaven {
                    sh """
                    mvn -B -C -P$profiles release:prepare release:perform -DreleaseVersion=$Version \
                    -Darguments=\"-Dauto.release=$releaseInMaven -DtrimStackTrace=false \
                    \$(eval echo \$MAVEN_ADD_OPTIONS) -Dwebdriver.chrome.binary='\$(eval echo \$CHROME_BINARY)' \
                    -Dmaven.install.skip=true -DadminPort=$payara_config.admin_port -DsslPort=$payara_config.ssl_port \"
                    """
                }
            }
        }
    }

    post {
        always {
            stopPayara payara_config
            archiveArtifacts artifacts: '**/payara5/**/server.log*'
            checkLogs '**/payara5/**/server.log*'
        }
        success {
            sh "git push origin Version-$Version"
        }
        failure {
            sh "git tag -d Version-$Version || true"
        }
    }
}
