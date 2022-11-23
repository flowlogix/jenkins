@Library('payara') _l1
@Library('util') _l2

def payara_config = [domain_name : 'prod-domain']
final def profiles = 'all-tests,payara-server-remote'
def release_profile = 'release-flowlogix-to-central'

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
    }
    parameters {
        booleanParam(name: 'releaseInMaven', defaultValue: true,
            description: 'Whether to release in Maven Central, or just close the repository')
        string(name: 'Version', description: 'Version number to release', trim: true)
        choice(name: 'releaseToRepo', description: 'Which repository to publish the release',
            choices: ['Maven Central', 'Hope Nexus'])
    }

    stages {
        stage('Maven Info') {
            steps {
                script {
                    if (Version.empty) {
                        def msg = 'Version cannot be empty'
                        currentBuild.description = msg
                        error msg
                    }
                    if (releaseToRepo.startsWith('Hope')) {
                        release_profile = 'release-flowlogix-to-hope'
                    }
                }
                sh "mvn -V -N -B -C -P$profiles help:all-profiles \
                    -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
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
                    mvn -B -C -P$profiles release:prepare release:perform \
                    -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn \
                    -DreleaseVersion=$Version -Drelease.profile=$release_profile \
                    -Darguments=\"-Dauto.release=$releaseInMaven -DtrimStackTrace=false -Dcheckstyle.skip=true \
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
            archiveArtifacts artifacts: '**/logs/server.log*'
            checkLogs payara_config.asadmin ? '**/logs/server.log*' : null
        }
        success {
            sh "git push origin Version-$Version"
        }
        failure {
            sh "git tag -d Version-$Version || true"
        }
    }
}
