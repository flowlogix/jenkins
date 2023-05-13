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
                sh "mvn -V -N -B -ntp -C -P$profiles help:all-profiles"
                script {
                    currentBuild.description = "Working on git commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                }
            }
        }
        stage('Maven - Release') {
            steps {
                startPayara payara_config
                sh """
                export MAVEN_OPTS="\$MAVEN_OPTS -Dsettings.security=$HOME/.m2/settings-security.xml"
                mvn -B -ntp -C -P$profiles release:prepare release:perform \
                -DreleaseVersion=$Version -Drelease.profile=$release_profile \
                -Darguments=\"-DtrimStackTrace=false -Dcheckstyle.skip=true \
                \$(eval echo \$MAVEN_ADD_OPTIONS) -Dwebdriver.chrome.binary='\$(eval echo \$CHROME_BINARY)' \
                -Dmaven.install.skip=true -DadminPort=$payara_config.admin_port -DsslPort=$payara_config.ssl_port \"
                """
            }
        }
        stage('Maven Central - Close (and optionally Release)') {
            when {
                expression { release_profile == 'release-flowlogix-to-central' }
            }
            steps {
                mavenCentralCredentials {
                    sh "$HOME/infra/scripts/nexus/maven-central-release.sh${releaseInMaven.toBoolean() ? ' --release' : ''}"
                }
            }
        }
    }

    post {
        always {
            stopPayara payara_config
            archiveArtifacts artifacts: '**/logs/server.log*'
            checkLogs(payara_config.asadmin ? '**/logs/server.log*' : null, false)
        }
        success {
            sh "git push origin Version-$Version"
        }
        failure {
            sh "git tag -d Version-$Version || true"
        }
    }
}
