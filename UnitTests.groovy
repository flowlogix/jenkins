@Library('payara') _l1
@Library('util') _l2

def mavenVersion = 4
def profiles = optionalMavenProfiles mavenVersion, 'payara-server-local,ui-test,ci'
def payara_config = [ domain_name : 'test-domain' ]
def mvn_cmd = 'mvn'
def payara_build_options = ''
def mavenParamsFromFile = ''
def qualityThreshold = 1

pipeline {
    agent any

    options {
        quietPeriod 0
    }

    stages {
        stage('Checkout and Setup') {
            steps {
                script {
                    currentBuild.description = "Commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                    if (env.GIT_URL.contains('shiro')) {
                        shiroPayaraConfig payara_config
                        qualityThreshold = 2
                    }
                    payara_config << [ jacoco_profile : profiles + optionalMavenProfiles(mavenVersion, ',coverage') ]
                    def mavenParamFileName = "$WORKSPACE/.jenkins_maven_args"
                    if (fileExists(mavenParamFileName)) {
                        mavenParamsFromFile = readFile(file: mavenParamFileName).trim()
                    }
                }
            }
        }
        stage('Maven Info') {
            steps {
                sh "$mvn_cmd -V -B -ntp -C -N -P$profiles help:all-profiles"
            }
        }
        stage('Start Payara') {
            steps {
                startPayara payara_config
                script {
                    payara_build_options = "-DadminPort=$payara_config.admin_port -DsslPort=$payara_config.ssl_port"
                    if (payara_config.jacoco_started) {
                        profiles += optionalMavenProfiles mavenVersion, ',coverage-remote'
                        payara_build_options += " -DjacocoPort=$payara_config.jacoco_port"
                    } else {
                        profiles += optionalMavenProfiles mavenVersion, ',coverage'
                    }
                }
            }
        }
        stage('Maven Verify - Tests') {
            steps {
                withMaven(options: [ jacocoPublisher(disabled: true) ]) {
                    sh """
                    export MAVEN_OPTS="\$MAVEN_OPTS $JAVA_TOOL_OPTIONS"
                    unset JAVA_TOOL_OPTIONS
                    $mvn_cmd -B -ntp -C verify -fae -P$profiles \$(eval echo \$MAVEN_ADD_OPTIONS) \
                    -Ddrone.chrome.binary="\$(eval echo \$CHROME_BINARY)" \
                    -Dmaven.test.failure.ignore=true -DtrimStackTrace=false \
                    -Dmaven.install.skip=true $payara_build_options $mavenParamsFromFile"""
                }
            }
        }
    }
    post {
        always {
            stopPayara payara_config
            archiveArtifacts artifacts: '**/logs/server.log*', allowEmptyArchive: true
            checkLogs payara_config.asadmin ? '**/logs/server.log*' : null, true, qualityThreshold
        }
    }
}
