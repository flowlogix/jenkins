pipeline {
    agent any
    environment {
        ftpcreds = credentials '8695b924-52bd-4fc2-9752-42041489b734'
    }
    options {
        quietPeriod 120
    }
    parameters {
        string(name: 'syncRoot', trim: true, description: 'Synchronization Root Directory', defaultValue: 'docs')
    }
    
    stages {
        stage('Publish Web Site') {
            steps {
                sh """ \
                lftp -u \$ftpcreds_USR,\$ftpcreds_PSW -e \
                'mirror -R -e -P7 -x .git --delete-excluded \
                $syncRoot test_website/$syncRoot; exit top' web173.dnchosting.com
                """
            }
        }
    }
}

