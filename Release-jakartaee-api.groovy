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
        stage('Version Check') {
            steps {
                script {
                    if (Version.empty) {
                        def msg = 'Version cannot be empty'
                        currentBuild.description = msg
                        error msg
                    }
                    tag_name = "jakartaee-api-$Version"
                }
                script {
                    currentBuild.description = "Commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                }
            }
        }
        stage('Maven - Update Versions') {
            steps {
                sh """
                GIT_TERMINAL_PROMPT=0 git fetch origin docs-profile:docs-profile
                GIT_TERMINAL_PROMPT=0 git cherry-pick docs-profile
                mvn -B -ntp -C -N versions:set-property -DgenerateBackupPoms=false -Dproperty=jakartaee.version -DnewVersion=10.0.0
                mvn -B -ntp -C -N versions:set-property -DgenerateBackupPoms=false -Dproperty=maven.compiler.release -DnewVersion=11
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
                mvn -B -ntp -C -Dmaven.install.skip=true -DtrimStackTrace=false \
                -DaltDeploymentRepository=hope-nexus-artifacts::default::https://nexus.hope.nyc.ny.us/repository/maven-releases/ \
                -Dparent.deploy.skip=false -Pdocs-profile deploy
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
