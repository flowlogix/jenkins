final def website_host = 'web154.dnchosting.com'

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
        stage('Maven - Asciidoc - convert to html/pdf') {
            steps {
                withMaven {
                    sh """ \
                    set +x; . "$HOME/.sdkman/bin/sdkman-init.sh"
                    export MAVEN_OPTS="\$MAVEN_OPTS $JAVA_TOOL_OPTIONS \
                        --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
                        --add-opens java.base/java.io=ALL-UNNAMED"
                    unset JAVA_TOOL_OPTIONS
                    set -x
                    mvn -B -C generate-resources
                    """
                }
            }
        }
        stage('Publish - Web Host') {
            steps {
                sh """ \
                lftp -u \$ftpcreds_USR,\$ftpcreds_PSW -e \
                'mirror -R -P7 -x .git --overwrite --delete --delete-excluded \
                target/output hope_website/resume; exit top' $website_host
                """
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: '**/output/*'
        }
    }
}
