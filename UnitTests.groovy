@Library('payara') _l1
@Library('util') _l2

def mavenVersion = 4
def profiles = optionalMavenProfiles mavenVersion, 'payara-server-local,ui-test,ci'
def payara_config = [ domain_name : 'test-domain', asadmin : '/usr/share/payara/bin/asadmin' ]
def mvn_cmd = 'mvn'
def payara_build_options = ''
def mavenParamsFromFile = ''
def qualityThreshold = 1

pipeline {
    agent {
        docker {
            label 'docker-agent'
            reuseNode true
            image 'lprimak/jenkins-agent:m4-p5-jdk21'
        }
    }

    options {
        quietPeriod 0
    }

    stages {
        stage('Checkout and Setup') {
            steps {
                script {
                    currentBuild.description = "Commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"

                    def mavenParamFileName = "$WORKSPACE/.jenkins_maven_args"
                    if (fileExists(mavenParamFileName)) {
                        mavenParamsFromFile = readFile(file: mavenParamFileName).trim()
                    }
                    if (env.GIT_URL.contains('shiro')) {
                        shiroPayaraConfig payara_config
                        qualityThreshold = 6
                    }
                    if (env.GIT_URL.contains('flowlogix/flowlogix') && env.CHANGE_TARGET == '5.x') {
                        qualityThreshold = 2
                    }
                    payara_config << [ jacoco_profile : profiles + optionalMavenProfiles(mavenVersion, ',coverage') ]
                    payara_config << [ jacoco_expr_args : mavenParamsFromFile ]
                }
            }
        }
        stage('Maven Info') {
            steps {
                sh "$mvn_cmd -V -B -ntp -C -N -P$profiles $mavenParamsFromFile help:all-profiles"
            }
        }
        stage('Start Payara') {
            steps {
                startPayara payara_config
                script {
                    payara_build_options = "-DadminPort=$payara_config.admin_port -Dpayara.https.port=$payara_config.ssl_port"
                    profiles += optionalMavenProfiles mavenVersion, ',coverage'
                    if (payara_config.jacoco_started) {
                        payara_build_options += " -DjacocoPort=$payara_config.jacoco_port"
                    }
                }
            }
        }
        stage('Maven Verify - Tests') {
            steps {
                withMaven(options: [ jacocoPublisher(disabled: true) ]) {
                    sh """
                    maven_interceptor_opts="$JAVA_TOOL_OPTIONS"
                    unset JAVA_TOOL_OPTIONS
                    $mvn_cmd -B -ntp -C verify -fae -P$profiles \$(eval echo \$MAVEN_ADD_OPTIONS) \$maven_interceptor_opts \
                    -Ddrone.chrome.binary="\$(eval echo \$CHROME_BINARY)" \
                    -Dmaven.test.failure.ignore=true -DtrimStackTrace=false \
                    -Dmaven.install.skip=true $payara_build_options $mavenParamsFromFile"""
                }
            }
        }
        stage('Maven - JaCoCo Coverage') {
            steps {
                script {
                    if (payara_config.jacoco_started) {
                        sh """$mvn_cmd -B -ntp -C initialize jacoco:dump -Djacoco.destFile=$WORKSPACE/target/jacoco-it.exec \
                              $payara_build_options -N -P$profiles"""
                    }
                    def jacocoExecFiles = findFiles glob: '**/jacoco*.exec'
                    if (jacocoExecFiles.length > 0) {
                        sh "$mvn_cmd -B -ntp -C initialize jacoco:merge jacoco:report $payara_build_options -N -P$profiles"
                    }
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
