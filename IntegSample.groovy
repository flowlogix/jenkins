pipeline {
    agent any

    triggers {
        // five minutes
//        pollSCM('H/5 * * * *')
        pollSCM('@daily')
    }
    options {
        disableConcurrentBuilds()
    }
    stages {
        stage('Maven Info') {
            steps {
                echo 'Intration Step Ran'
            }
        }
    }
}
