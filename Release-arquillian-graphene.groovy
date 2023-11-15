@Library('util') _l1

def tag_name = ""

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
    }
    parameters {
        string(name: 'Version', description: 'Version number to release', trim: true)
        string(name: 'droneVersion', description: 'Arquillian Drone version', trim: true)
    }

    stages {
        stage('Check Versions') {
            steps {
                script {
                    if (Version.empty || droneVersion.empty) {
                        def msg = 'Version or Drone version cannot be empty'
                        currentBuild.description = msg
                        error msg
                    }
                    tag_name = "arquillian-graphene-$Version"
                }
                script {
                    currentBuild.description = "Commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                }
            }
        }
        stage('Maven - Update Versions') {
            steps {
                sh """
                mvn -B -ntp -C -N versions:set-property -DgenerateBackupPoms=false -Dversion.arquillian.drone=$droneVersion \
                -Dproperty=version.arquillian.drone -DnewVersion=$droneVersion
                mvn -B -ntp -C versions:set -DprocessAllModules=true -DgenerateBackupPoms=false -DnewVersion=$Version versions:set
                git commit -am "[Release Version]"
                """
            }
        }
        stage('Maven Info') {
            steps {
                sh "mvn -V -N -B -ntp -C help:all-profiles"
            }
        }
        stage('Tag Release') {
            steps {
                sh "git tag $tag_name"
            }
        }
        stage('Maven - Deploy') {
            steps {
                sh """
                mvn -B -ntp -C -Dmaven.install.skip=true -DtrimStackTrace=false -DskipTests \
                -DaltDeploymentRepository=hope-nexus-artifacts::default::https://nexus.hope.nyc.ny.us/repository/maven-releases/ \
                deploy
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
