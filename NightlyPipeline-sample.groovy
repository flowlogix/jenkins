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
                checkout([$class: 'GitSCM', branches: [[name: '*/master']],
                extensions: [[$class: 'WipeWorkspace']],
                userRemoteConfigs: [[credentialsId: 'dd067490-e408-45cb-b911-d825906a05ee',
                    url: 'https://github.com/flowlogix/flowlogix']]])
            }
        }
        stage('Maven Info') {
            steps {
                sh 'mvn -V -N help:all-profiles'
            }
        }
        stage('Maven Verify - Regular + Stress Tests') {
            steps {
                withMaven {
                    sh """#!/bin/bash -pl
                    export MAVEN_OPTS="$MAVEN_OPTS $JAVA_TOOL_OPTIONS"
                    unset JAVA_TOOL_OPTIONS
                    mvn -B verify -DforkCount=0 -Dsurefire.useSystemClassLoader=false \
                    -Dmaven.install.skip=true -Pall-tests -fae
                    """
                }
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
            archiveArtifacts artifacts: '**/payara5/**/server.log*'
        }
    }
}

void notify(String newStatus) {
    githubNotify description: 'Nightly Build',
        credentialsId: 'dd067490-e408-45cb-b911-d825906a05ee', account: 'flowlogix',
        repo: 'flowlogix', sha: 'master',
        context: 'CI/Nightly',  status: newStatus
}
