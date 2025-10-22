@Library('util') _l1

def tag_name = ""
def nexus_staging_profile = ""
def alt_repository = ""

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
    }
    parameters {
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
                        alt_repository = "-DaltDeploymentRepository=flowlogix-nexus-artifacts::https://nexus.flowlogix.com/repository/maven-releases"
                    } else {
                        // nexus_staging_profile = "nexus-staging"
                        alt_repository = "-DaltDeploymentRepository=apache.releases.https::https://repository.apache.org/service/local/staging/deploy/maven2"
                    }
                    tag_name = "shiro-root-$Version"
                }
                sh "mvn -V -N -B -ntp -C help:all-profiles"
                script {
                    currentBuild.description = "Commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                }
            }
        }
        stage('Maven - Release') {
            steps {
                mavenSettingsCredentials true, {
                    sh """
                    mvn -B -ntp -C release:prepare release:perform -Dnexus-staging-profile=$nexus_staging_profile \
                    -DreleaseVersion=$Version -Darguments=\"-DtrimStackTrace=false -Dmaven.install.skip=true \
                    -DskipTests -Djakartaee.it.skip=true -Dpayara.start.skip=true -Dpayara.restart.skip=true \
                    $alt_repository \"
                    """
                }
            }
        }
    }

    post {
        success {
            script {
                if (createTag.toBoolean() && releaseToRepo.startsWith('Maven')) {
                    sh "git push origin $tag_name"
                } else {
                    sh "git tag -d $tag_name || true"
                }
            }
        }
        failure {
            sh "git tag -d $tag_name || true"
        }
    }
}
