pipeline {
    agent any
    environment {
        ftpcreds = credentials '8695b924-52bd-4fc2-9752-42041489b734'
    }
    options {
        quietPeriod 120
    }
    parameters {
        choice(choices: ['push', 'init', 'catchup'], name: 'publishType', description: 'git ftp command to execute')
        string(name: 'syncRoot', trim: true, description: 'Synchronization Root Directory', defaultValue: 'docs')
    }
    
//                git ftp $publishType -u \$ftpcreds_USR -p \$ftpcreds_PSW \
//                --syncroot $syncRoot ftpes://web173.dnchosting.com/test_website/$syncRoot \
    stages {
        stage('Publish Web Site') {
            steps {
                sh """ \
                lftp -u \$ftpcreds_USR,\$ftpcreds_PSW -e \
                'mirror -R -e -P --ignore-time $syncRoot test_website/$syncRoot; exit top' web173.dnchosting.com \
                """
            }
        }
    }
}

