@Library('util') _l1

def repository_name = 'flowlogix-maven-central-s01'
def repository_url = 'https://s01.oss.sonatype.org/service/local/staging/deploy/maven2'

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
                }
                mavenSettingsCredentials false, {
                    sh """
                    export MAVEN_OPTS="\$MAVEN_OPTS -Dsettings.security=$HOME/.m2/settings-security.xml"
                    mvn -B -ntp -C release:prepare release:perform \
                    -DpushChanges=false -DlocalCheckout=true \
                    -DreleaseVersion=$Version -DtagNameFormat=Version-$Version \
                    -Dgoals=\"resources:resources jar:jar gpg:sign deploy\" \
                    -Darguments=\"-Dgpg.keyname=\\"Flow Logix, Inc.\\" -Djar.skip-if-empty=true \
                    -Dmaven.install.skip=true -Dpayara.start.skip=true \
                    -DaltDeploymentRepository=$repository_name::$repository_url \"
                    """
                }
            }
        }
        stage('Maven Central - Close (and optionally Release)') {
            when {
                expression { repository_name != 'hope-nexus-artifacts' }
            }
            steps {
                mavenCentralCredentials {
                    sh "$HOME/infra/scripts/nexus/maven-central-release.sh com.flowlogix${releaseInMaven.toBoolean() ? ' --release' : ''}"
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
