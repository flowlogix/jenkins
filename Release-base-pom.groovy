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
        string(name: 'Version', description: 'Version number to release', trim: true)
        choice(name: 'releaseToRepo', description: 'Which repository to publish the release',
                choices: ['Maven Central', 'Hope Nexus'])
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

                    if (releaseToRepo.startsWith('Hope')) {
                        repository_name = 'hope-nexus-artifacts'
                        repository_url = 'https://nexus.hope.nyc.ny.us/repository/maven-releases'
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
                    -Darguments=\"-Djar.skip-if-empty=true -Dmaven.install.skip=true \
                    -Dpayara.start.skip=true \
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
            sh "git push origin Version-$Version"
        }
        failure {
            sh "git tag -d Version-$Version || true"
        }
    }
}
