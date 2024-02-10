@Library('util') _l1

def tag_name = ""
def arquillian_version = '1.8.0.Final'

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
    }
    parameters {
        string(name: 'Version', description: 'Version number to release', trim: true)
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
                    tag_name = "parent-payara-containers-$Version"
                }
                sh "mvn -V -N -B -ntp -C help:all-profiles"
                script {
                    currentBuild.description = "Commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                }
            }
        }
        stage('Maven - Update Versions') {
            steps {
                sh """
                mvn -B -ntp -C -N versions:set-property -DgenerateBackupPoms=false \
                -Dversion.arquillian_core=$arquillian_version \
                -Dproperty=version.arquillian_core -DnewVersion=$arquillian_version
                git commit -am "[Update Arquillian Core]"
                """
            }
        }
        stage('Maven - Release') {
            steps {
                sh """
                mvn -B -ntp -C release:prepare release:perform \
                -DreleaseVersion=$Version -Darguments=\"-DtrimStackTrace=false -Dmaven.install.skip=true \
                -DskipTests -Dgpg.skip -Dversion.maven.enforcer.java.limit=28 -Dversion.payara=6.2024.1 \
                -DaltDeploymentRepository=hope-nexus-artifacts::https://nexus.hope.nyc.ny.us/repository/maven-releases/\"
                """
            }
        }
    }

    post {
        success {
            sh "git push origin $tag_name"
        }
        failure {
            sh "git tag -d $tag_name || true"
        }
    }
}
