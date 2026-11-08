@Library('util') _l1

def tag_name = ""
def nexus_staging_profile = ""
def alt_repository = ""
def altReleaseFlags = ""
def java_setup = ""

// maven 3 compatible settings
def maven_command = "mvn"
def maven_options = "-Dmaven.install.skip=true -DobrRepository=NONE"

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
                        altReleaseFlags = "-DdistMgmtReleasesId=flowlogix-nexus-artifacts -DdistMgmtReleasesName=FlowLogixJenkins \
                                           -DdistMgmtReleasesUrl=https://nexus.flowlogix.com/repository/maven-releases"
                    } else {
                        java_setup = java25()
                        maven_command = "./mvnw -s \$HOME/.m2/settings-shiro-release-maven3.xml"
                        maven_options = "${maven_options} -Dnjord.enabled=false"
                        // nexus_staging_profile = "nexus-staging"
                        alt_repository = "-DaltDeploymentRepository=apache.releases.https::https://repository.apache.org/service/local/staging/deploy/maven2"
                    }
                    tag_name = "shiro-root-$Version"
                }
                sh """
                   ${java_setup}
                   MAVEN_OPTS="$maven_options" $maven_command -V -N -B -ntp -C help:all-profiles
                   """
                script {
                    currentBuild.description = "Commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                }
            }
        }
        stage('Maven - Release') {
            steps {
                mavenSettingsCredentials true, {
                    sh """
                        ${java_setup}
                        export MAVEN_OPTS="\$MAVEN_OPTS $maven_options $altReleaseFlags $alt_repository"
                        $maven_command -B -ntp -C release:prepare release:perform -Pskip_jakarta_ee_tests \
                        -Dnexus-staging-profile=$nexus_staging_profile $altReleaseFlags \
                        -DreleaseVersion=$Version -Darguments=\"-DtrimStackTrace=false $maven_options \
                        $alt_repository\" \
                    """
                }
            }
        }
    }

    post {
        success {
            script {
                if (createTag.toBoolean()) {
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
