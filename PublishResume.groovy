@Library('util') _

pipeline {
    agent any
    options {
        quietPeriod 0
        disableConcurrentBuilds()
    }
    
    stages {
        stage('Maven - Asciidoc - convert to html/pdf') {
            steps {
                withMaven {
                    sh """ \
                    set +x
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
        stage('wkhtmltopdf - html-to-PDF') {
            steps {
                sh """ \
                set +x
                for html_file in target/output/*.html
                do
                    echo "Converting \$html_file to PDF ..."
                    wkhtmltopdf --page-height 333mm --page-width 210mm \
                    https://apps.hope.nyc.ny.us/resume/`basename \$html_file` \
                    target/output/`basename \$html_file .html`.pdf
                done
                set -x
                """
            }
        }
        stage('Publish - Web Host') {
            steps {
                ftpCredentials {
                    sh """ \
                    lftp -u \$ftpcreds_USR,\$ftpcreds_PSW -e \
                    'mirror -R -P7 -x .git --overwrite --delete --delete-excluded \
                    target/output hope_website/resume; exit top' ${websiteHost()}
                    """
                }
            }
        }
        stage('Publish - App server') {
            steps {
                sh "rsync -aEH --delete-after target/output/ $HOME/var/website-content/resume/"
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: '**/output/*'
        }
    }
}
