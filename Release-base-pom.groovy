@Library('util') _l1

def repository_name = 'flowlogix-maven-central-portal'
def repository_url = 'njord:'
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
        stage('Maven - Release') {
            steps {
                script {
                    if (Version.empty) {
                        def msg = 'Version cannot be empty'
                        currentBuild.description = msg
                        error msg
                    }

                    if (releaseToRepo.startsWith('FlowLogix')) {
                        repository_name = 'flowlogix-nexus-artifacts'
                        repository_url = 'https://nexus.flowlogix.com/repository/maven-releases'
                    }
                    if (releaseInMaven.toBoolean()) {
                        automaticCommand = '-Dnjord.publishingType=automatic'
                    }
                }
                mavenSettingsCredentials false, {
                    sh """
                    mvn -B -ntp -C -Pflowlogix-base-release release:prepare release:perform \
                    -DpushChanges=false -DlocalCheckout=true \
                    -DreleaseVersion=$Version -DtagNameFormat=Version-$Version \
                    -Dgoals=\"resources:resources jar:jar gpg:sign deploy\" \
                    -Darguments=\"-Dmaven.install.skip=true \
                    -Dnjord.publisher=sonatype-cp -Dnjord.autoPublish=true \
                    -Dnjord.waitForStates=true $automaticCommand \
                    -Dnjord.publisher.sonatype-cp.releaseRepositoryId=$repository_name \
                    -DaltDeploymentRepository=$repository_name::$repository_url \"
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
