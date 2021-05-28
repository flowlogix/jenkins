@Library('payara') _
env.domain_name = 'prod-domain'
def lastTagName
def lastVersion

String getLastTagName() {
    sh(script: "git describe --abbrev=0 --tags", returnStdout: true).trim()
}

String getVersion(String branchName) {
    branchName.substring(branchName.lastIndexOf('-') + 1, branchName.length())
}

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
                sh "mvn -V -N help:all-profiles"
            }
        }
        stage('Maven - Release') {
            steps {
                startPayara()
                withMaven {
                    sh """\
                    mvn -B release:prepare release:perform -DtrimStackTrace=false \
                    -DreleaseVersion=${Version} -Darguments=\"-Dauto.release=${releaseInMaven} \
                    -Dpayara.start.skip=true -Dmaven.install.skip=true -DadminPort=${env.admin_port}\" \
                    """
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '**/payara5/**/server.log*'
            stopPayara()
        }
        success {
            sh "git push origin Version-${Version}"
        }
        failure {
            sh "git tag -d Version-${Version} || true"
        }
    }
}
