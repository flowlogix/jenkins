@Library('util') _

def website_root = 'hope_website'
def website_subdir = ''
def targetUrlSuffix = 'hope.nyc.ny.us'
def rsyncSuffix = ''

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
                    currentBuild.description = "Working on git commit ${env.GIT_COMMIT[0..7]} branch $env.GIT_BRANCH Node $env.NODE_NAME"
                    if (!env.GIT_URL.contains('hope')) {
                        echo 'Deploying Flow Logix web site'
                        website_root = 'flowlogix_website'
                        targetUrlSuffix = 'flowlogix.com'
                        rsyncSuffix = '/flowlogix'
                    }
                    switch (env.GIT_BRANCH) {
                        case ["master", "main"]:
                            break
                        default:
                            website_root += '_pr'
                            website_subdir = "/$env.GIT_BRANCH"
                    }
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
                    mvn -B -C -ntp generate-resources
                    """
                }
            }
        }
        stage('Publish - Web Host') {
            steps {
                ftpCredentials {
                    sh """ \
                    lftp -u \$ftpcreds_USR,\$ftpcreds_PSW -e \
                    'mirror -R -P7 -x resume/ --overwrite --delete \
                    target/output $website_root$website_subdir; exit top' ${websiteHost()}
                    """
                }
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
                sh "rsync -aEH --exclude resume/ --delete-after target/output/ $HOME/var/website-content$rsyncSuffix"
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
