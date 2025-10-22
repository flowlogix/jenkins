@Library('util') _l1

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
            choices: ['Maven Central', 'FlowLogix Nexus'])
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
                    if (releaseToRepo.startsWith('FlowLogix')) {
                        release_profile = 'release-to-flowlogix'
                    }
                    if (releaseInMaven.toBoolean()) {
                        automaticCommand = '-Dnjord.publishingType=automatic'
                    }
                }
                sh "mvn -V -N -B -ntp -C help:all-profiles"
                script {
                    currentBuild.description = "Commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                }
            }
        }
        stage('Maven - Release') {
            steps {
                mavenSettingsCredentials false, {
                    sh """
                    mvn -B -ntp -C release:prepare release:perform \
                    -DreleaseVersion=$Version -Drelease.profile=$release_profile -Dgoals=deploy \
                    -Darguments=\"-DtrimStackTrace=false -Dmaven.install.skip=true \
                    -Dnjord.autoPublish=true -Dnjord.waitForStates=true $automaticCommand \"
                    """
                }
            }
        }
    }

    post {
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
