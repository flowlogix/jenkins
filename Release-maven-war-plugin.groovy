@Library('util') _l1

def tag_name = ""

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
                    tag_name = "maven-war-plugin-$Version"
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
                mvn -B -ntp -C versions:set -DprocessAllModules=true -DgenerateBackupPoms=false -DnewVersion=$Version versions:set
                git commit -am "[Release Version]"
                """
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
