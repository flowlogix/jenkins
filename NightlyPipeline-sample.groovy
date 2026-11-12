pipeline {
    agent any

    stages {
        stage('Start') {
            steps {
                notify 'PENDING'
            }
        }
        stage('Checkout') {
            steps {
                deleteDir()
                checkout([$class: 'GitSCM', branches: [[name: '*/master']],
                userRemoteConfigs: [[credentialsId: 'dd067490-e408-45cb-b911-d825906a05ee',
                    url: 'https://github.com/flowlogix/flowlogix']]])
            }
        }
        stage('Maven Verify - Regular + Stress Tests') {
            steps {
                sh """#!/bin/bash -pl
                sdk current
                mvn -V -B -C verify -Dprofile.display -DforkCount=0 -Dsurefire.useSystemClassLoader=false \
                -Dmaven.install.skip=true -Pall-tests -fae
                """
            }
        }
    }

    post {
        success {
            notify 'SUCCESS'
        }
        failure {
            notify 'FAILURE'
        }
        unstable {
            notify 'FAILURE'
        }
        always {
            junit testResults: '**/target/surefire-reports/*.xml,**/target/failsafe-reports/*.xml',
                    allowEmptyResults: true
            archiveArtifacts artifacts: '**/target/*.jar,**/target/*.war,**/target/*.ear,**/payara5/**/server.log*',
                    allowEmptyArchive: true
        }
    }
}

void notify(String newStatus) {
    githubNotify description: 'Nightly Build',
        credentialsId: 'dd067490-e408-45cb-b911-d825906a05ee', account: 'flowlogix',
        repo: 'flowlogix', sha: 'master',
        context: 'CI/Nightly',  status: newStatus
}
