@Library('util') _

def website_root = 'hope'
def website_subdir = ''
def targetUrlSuffix = 'hope.nyc.ny.us'
def rsyncSuffix = ''
def mavenParamsFromFile = ''
def jbake_maven_project = 'jbake-maven'
def jbake_props_file = 'jbake.properties'

pipeline {
    agent any
    options {
        quietPeriod 0
        disableConcurrentBuilds()
    }
    
    stages {
        stage('Prep') {
            steps {
                script {
                    currentBuild.description = "Commit ${env.GIT_COMMIT[0..7]} branch $env.GIT_BRANCH Node $env.NODE_NAME"
                    if (!env.GIT_URL.contains('hope')) {
                        echo 'Deploying Flow Logix web site'
                        website_root = 'flowlogix'
                        targetUrlSuffix = 'flowlogix.com'
                        rsyncSuffix = '/flowlogix'
                    }
                    switch (env.GIT_BRANCH) {
                        case ["master", "main"]:
                            break
                        default:
                            website_root = "pullrequest_" + website_root
                            website_subdir = "/$env.GIT_BRANCH"
                    }
                    def mavenParamFileName = "$WORKSPACE/.jenkins_maven_args"
                    if (fileExists(mavenParamFileName)) {
                        mavenParamsFromFile = readFile(file: mavenParamFileName).trim()
                    }
                    def configContent = readFile "${WORKSPACE}/${jbake_props_file}"
                    writeFile file: "${WORKSPACE}/${jbake_props_file}",
                            text: configContent + "\ngit.commit=${env.GIT_COMMIT[0..7]}"
                }
            }
        }
        stage('Maven - JBake') {
            steps {
                withMaven {
                    sh """ \
                    set +x;
                    export MAVEN_OPTS="\$MAVEN_OPTS $JAVA_TOOL_OPTIONS \
                        --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
                        --add-opens java.base/java.io=ALL-UNNAMED"
                    unset JAVA_TOOL_OPTIONS
                    set -x
                    mvn -B -C -ntp -f $jbake_maven_project $mavenParamsFromFile generate-resources
                    """
                }
            }
        }
        stage('Publish - Web Host') {
            steps {
                sh """
                ssh ${websiteHost()} mkdir -p /var/flowlogix/html/${website_root}${website_subdir}
                rsync -aH --exclude resume/ --delete-after $jbake_maven_project/target/output/ \
                    ${websiteHost()}:/var/flowlogix/html/${website_root}${website_subdir}/
                """
            }
        }
        stage('Publish - App server') {
            when {
                not {
                    expression {
                        website_subdir as boolean
                    }
                }
            }
            steps {
                sh "rsync -aEH --exclude resume/ --delete-after \
                    $jbake_maven_project/target/output/ $HOME/var/website-content$rsyncSuffix"
            }
        }
    }

    post {
        success {
            script {
                if (website_subdir as boolean) {
                    githubNotify description: 'Preview Changes Link', context: 'CI/deploy-preview', status: 'SUCCESS',
                        targetUrl: "https://pullrequest.$targetUrlSuffix$website_subdir"
                }
            }
        }
    }
}
