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
                sh """
                mvn -B -ntp -C release:prepare release:perform -Dnexus-staging-profile= \
                -DreleaseVersion=$Version -Darguments=\"-DtrimStackTrace=false -Dmaven.install.skip=true \
                -DskipTests -Dgpg.skip -Djakartaee.it.skip=true -Dpayara.start.skip=true -Dpayara.restart.skip=true \
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