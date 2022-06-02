final def website_host = 'web154.dnchosting.com'
def syncRoot = 'website'
def website_root = 'hope_website'
def website_subdir = ''

pipeline {
    agent any
    environment {
        ftpcreds = credentials '8695b924-52bd-4fc2-9752-42041489b734'
    }
    options {
        quietPeriod 0
        disableConcurrentBuilds()
    }
    
    stages {
        stage('Prep') {
            steps {
                script {
                    currentBuild.description = "Working on git commit ${env.GIT_COMMIT[0..7]} branch $env.GIT_BRANCH Node $env.NODE_NAME"
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
        stage('JBake') {
            steps {
                sh """ \
                set +x; . "$HOME/.sdkman/bin/sdkman-init.sh"
                export JBAKE_OPTS='--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
                --add-opens java.base/java.io=ALL-UNNAMED'
                set -x
                jbake -b $syncRoot
                """
            }
        }
        stage('Publish') {
            steps {
                sh """ \
                lftp -u \$ftpcreds_USR,\$ftpcreds_PSW -e \
                'mirror -R -P7 -x .git --overwrite --delete --delete-excluded \
                $syncRoot/output $website_root$website_subdir; exit top' $website_host
                """
            }
        }
    }

    post {
        success {
            script {
                if (website_subdir as boolean) {
                    githubNotify description: 'Preview Changes Link', context: 'CI/deploy-preview', status: 'SUCCESS',
                        targetUrl: "https://pullrequest.hope.nyc.ny.us$website_subdir"
                }
            }
        }
    }
}
