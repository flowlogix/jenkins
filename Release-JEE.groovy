@Library('payara') _
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
                sh "mvn -V -N -P$profiles help:all-profiles"
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
                    # TODO: Remove after OSSRH-66257, NEXUS-26993 are fixed. Workaround for JDK 16+ support
                    export MAVEN_OPTS="\$MAVEN_OPTS \
                    --add-opens=java.base/java.util=ALL-UNNAMED \
                    --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
                    --add-opens=java.base/java.text=ALL-UNNAMED \
                    --add-opens=java.desktop/java.awt.font=ALL-UNNAMED"

                    mvn -B -P$profiles release:prepare release:perform -DreleaseVersion=$Version \
                    -Darguments=\"-Dauto.release=$releaseInMaven -DtrimStackTrace=false \
                    -Dmaven.install.skip=true -DadminPort=$payara_config.admin_port\"
                    """
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '**/payara5/**/server.log*'
            stopPayara payara_config
        }
        success {
            sh "git push origin Version-$Version"
        }
        failure {
            sh "git tag -d Version-$Version || true"
        }
    }
}
