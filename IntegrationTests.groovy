@Library('payara') _l1
@Library('util') _l2
def mavenVersion = 4
final def profiles           = optionalMavenProfiles mavenVersion, 'payara-server-local,coverage,all-tests'
final def profiles_no_stress = optionalMavenProfiles mavenVersion, 'payara-server-local,coverage,ui-test'
def payara_config = [ domain_name : 'test-domain', jacoco_profile : profiles ]
def mvnCommandLine
def jbake_maven_project = 'jbake-maven'
def qualityThreshold = 1

pipeline {
    agent any
    options {
        disableConcurrentBuilds()
        quietPeriod 0
    }
    triggers {
        pollSCM('@daily')
    }

    stages {
        stage('Maven Info') {
            steps {
                sh "mvn -V -B -ntp -C -N -P$profiles help:all-profiles"
                script {
                    currentBuild.description = "Commit ${env.GIT_COMMIT[0..7]} Node $env.NODE_NAME"
                    if (env.GIT_URL.contains('flowlogix/flowlogix') && env.GIT_BRANCH == '5.x') {
                        qualityThreshold = 3
                    }
                }
            }
        }
        stage ('Set up Payara and Maven') {
            steps {
                startPayara payara_config
                withMaven {
                    script {
                        mvnCommandLine =
                            """
                                export MAVEN_OPTS="\$(eval echo \$MAVEN_OPTS)"
                                maven_interceptor_opts="\$(eval echo \$JAVA_TOOL_OPTIONS)"
                                unset JAVA_TOOL_OPTIONS
                                mvn -B -ntp -C -fae \$(eval echo \$MAVEN_ADD_OPTIONS) \$maven_interceptor_opts \
                                -Ddrone.chrome.binary="\$(eval echo \$CHROME_BINARY)" \
                                -Dmaven.test.failure.ignore=true -DtrimStackTrace=false \
                                -Dmaven.install.skip=true -DadminPort=$payara_config.admin_port \
                                -Dpayara.https.port=$payara_config.ssl_port -DjacocoPort=$payara_config.jacoco_port \
                            """
                    }
                }
            }
        }
        stage('Maven Verify - All Tests') {
            steps {
                withMaven(options: [ jacocoPublisher(disabled: true) ]) {
                    sh "$mvnCommandLine verify -P${profiles}"
                }
            }
        }
        stage('Maven - JaCoCo Coverage') {
            steps {
                script {
                    if (payara_config.jacoco_started) {
                        sh """mvn -B -ntp -C initialize jacoco:dump \
                              -Djacoco.destFile=$WORKSPACE/target/jacoco-it.exec \\
                              -DjacocoPort=$payara_config.jacoco_port -N -P$profiles"""
                    }
                    def jacocoExecFiles = findFiles glob: '**/jacoco*.exec'
                    if (jacocoExecFiles.length > 0) {
                        sh """mvn -B -ntp -C initialize jacoco:merge jacoco:report \
                             -DjacocoPort=$payara_config.jacoco_port -N -P$profiles"""
                    }
                }
            }
        }
        stage('Maven Deploy Javadoc and Snapshots') {
            when {
                expression { currentBuild.currentResult == 'SUCCESS' }
            }
            steps {
                sh "mvn -B -C -fae deploy -Dmaven.install.skip=true -Dcheckstyle.skip=true -DskipTests -P$profiles"
            }
        }
        stage('Maven Deploy documentation') {
            when {
                allOf {
                    anyOf {
                        branch "main"
                        branch "master"
                    }
                    expression { currentBuild.currentResult == 'SUCCESS' && fileExists("${env.WORKSPACE}/docs/") }
                }
            }
            steps {
                sh """
                export MAVEN_OPTS="\$MAVEN_OPTS --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
                    --add-opens java.base/java.io=ALL-UNNAMED"
                mvn -B -C -ntp process-resources -Dsass.skip=true -f ${env.WORKSPACE}/docs/${jbake_maven_project}/

                ssh ${websiteHost()} mkdir -p /var/flowlogix/html/javadoc/jee-apidocs \
                                              /var/flowlogix/html/javadoc/datamodel-apidocs
                rsync -aH --delete-after ${env.WORKSPACE}/docs/$jbake_maven_project/target/output/ \
                    ${websiteHost()}:/var/flowlogix/html/docs/
                rsync -aH --delete-after ${env.WORKSPACE}/jakarta-ee/flowlogix-jee/target/reports/apidocs/ \
                    ${websiteHost()}:/var/flowlogix/html/javadoc/jee-apidocs/
                rsync -aH --delete-after ${env.WORKSPACE}/jakarta-ee/flowlogix-datamodel/target/reports/apidocs/ \
                    ${websiteHost()}:/var/flowlogix/html/javadoc/datamodel-apidocs/
                """
            }
        }
    }

    post {
        always {
            stopPayara payara_config
            archiveArtifacts artifacts: '**/logs/server.log*'
            checkLogs payara_config.asadmin ? '**/logs/server.log*' : null, true, qualityThreshold
        }
        success {
            githubNotify description: 'Deploy Snapshots', context: 'CI/Deploy', status: 'SUCCESS',
                targetUrl: 'https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/com/flowlogix'
        }
        changed {
            mail to: "lprimak@hope.nyc.ny.us", subject: "Jenkins: Project name -> ${env.JOB_NAME}",
            body: "<b>Jenkins Build Status Change [${currentBuild.currentResult}]</b><br>" +
                  "Project: ${env.JOB_NAME} <br>Build Number: ${env.BUILD_NUMBER} <br>Build URL: ${env.BUILD_URL}",
            charset: 'UTF-8', mimeType: 'text/html'
        }
    }
}
