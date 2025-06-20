@Library('payara') _l1
@Library('util') _l2

def payara_config = [domain_name : 'prod-domain']
final def profiles = 'all-tests,payara-server-local'
def release_profile = 'flowlogix-central-portal'
def automaticCommand = ''

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
    }
    parameters {
        booleanParam(name: 'releaseInMaven', defaultValue: true,
            description: 'Whether to release in Maven Central, or just close the repository')
        booleanParam(name: 'createTag', defaultValue: true,
                description: 'Whether to create tag in GitHub')
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
                    if (releaseInMaven.toBoolean()) {
                        automaticCommand = '-Dnjord.publishingType=automatic'
                    }
                }
                sh "mvn -V -N -B -ntp -C -P$profiles help:all-profiles"
                script {
                    currentBuild.description = "Commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                }
            }
        }
        stage('Maven - Release') {
            steps {
                startPayara payara_config
                mavenSettingsCredentials false, {
                    sh """
                    mvn -B -ntp -C -P$profiles release:prepare release:perform \
                    -DreleaseVersion=$Version -Drelease.profile=$release_profile -Dgoals=deploy \
                    -Darguments=\"-DtrimStackTrace=false -Dmaven.install.skip=true \
                    -Dnjord.autoPublish=true -Dnjord.waitForStates=true $automaticCommand \
                    \$(eval echo \$MAVEN_ADD_OPTIONS) -Ddrone.chrome.binary='\$(eval echo \$CHROME_BINARY)' \
                    -DadminPort=$payara_config.admin_port -Dpayara.https.port=$payara_config.ssl_port \"
                    """
                }
            }
        }
    }

    post {
        always {
            stopPayara payara_config
            archiveArtifacts artifacts: '**/logs/server.log*'
            checkLogs(payara_config.asadmin ? '**/logs/server.log*' : null, false, env.GIT_BRANCH == '5.x' ? 5 : 1)
        }
        success {
            script {
                if (createTag.toBoolean()
                        || (releaseInMaven.toBoolean() && releaseToRepo.startsWith('Maven'))) {
                    sh "git push origin Version-$Version"
                } else {
                    sh "git tag -d Version-$Version || true"
                }
            }
        }
        failure {
            sh "git tag -d Version-$Version || true"
        }
    }
}
