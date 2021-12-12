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
    parameters {
        string(name: 'syncRoot', trim: true, description: 'Synchronization Root Directory', defaultValue: 'docs')
    }
    
    stages {
        stage('Publish Web Site') {
            steps {
                script {
                    currentBuild.description = "Working on git commit $env.GIT_COMMIT"
                }
                sh """ \
                set +x; . "$HOME/.sdkman/bin/sdkman-init.sh"; set -x
                jbake -b $syncRoot
                lftp -u \$ftpcreds_USR,\$ftpcreds_PSW -e \
                'mirror -R -e -P7 -x .git --delete-excluded \
                $syncRoot/output test_website; exit top' $website_host
                """
            }
        }
    }
}
