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
                    maven_interceptor_opts="$JAVA_TOOL_OPTIONS"
                    export MAVEN_OPTS="\$MAVEN_OPTS \
                        --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
                        --add-opens java.base/java.io=ALL-UNNAMED"
                    unset JAVA_TOOL_OPTIONS
                    set -x
                    mvn -B -C -ntp generate-resources \$maven_interceptor_opts
                    """
                }
            }
        }
        stage('Intermediate - Publish Resume Locally') {
            steps {
                sh "rsync -aEH target/output/ $HOME/var/website-content/resume/"
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
                sh "ssh ${websiteHost()} mkdir -p /var/flowlogix/html/hope/resume"
                sh "rsync -aH --delete-after target/output/ ${websiteHost()}:/var/flowlogix/html/hope/resume/"
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
