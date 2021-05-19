pipeline {
    agent any

    stages {
        stage('Maven Info') {
            steps {
                sh 'mvn -V -N help:all-profiles'
            }
        }
        stage('Maven Verify - Tests') {
            steps {
                withMaven {
                    sh """\
                    MAVEN_OPTS="$MAVEN_OPTS $JAVA_TOOL_OPTIONS" \
                    env -u JAVA_TOOL_OPTIONS \
                    mvn verify -DforkCount=0 -Dsurefire.useSystemClassLoader=false \
                    -Dmaven.install.skip=true \
                    -fae \
                    """
                }
            }
        }
    }
}

