@Library('util') _l1

def tag_name = ""
def payara_version = '6.2024.6'

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
    }
    parameters {
        string(name: 'Version', description: 'Version number to release', trim: true)
        string(name: 'arquillian_version', description: 'Arquillian Core version', trim: true)
    }

    stages {
        stage('Maven Info') {
            steps {
                script {
                    if (Version.empty || arquillian_version.empty) {
                        def msg = 'Version or Arquillian Core version cannot be empty'
                        currentBuild.description = msg
                        error msg
                    }
                    tag_name = "parent-payara-containers-$Version"
                    if (env.GIT_BRANCH.contains('payara5')) {
                        payara_version = '5.2022.5'
                    }
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
                git commit -am "[Update Arquillian Core]" || exit 0
                """
            }
        }
        stage('Maven - Release') {
            steps {
                sh """
                mvn -B -ntp -C release:prepare release:perform \
                -DreleaseVersion=$Version -Darguments=\"-DtrimStackTrace=false -Dmaven.install.skip=true \
                -DskipTests -Dgpg.skip -Dmaven.javadoc.skip=true -Dversion.maven.enforcer.java.limit=28 \
                -Dversion.payara=$payara_version \
                -DaltDeploymentRepository=flowlogix-nexus-artifacts::https://nexus.flowlogix.com/repository/maven-releases/\"
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
