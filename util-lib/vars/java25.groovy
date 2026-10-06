// returns Java 25 shell construct

String call(String fileName = 'java-25-version') {
    """
    java_version_file=\${HOME}/var/${fileName}
    if [ -e \$java_version_file ]; then
        export JAVA_HOME=\$HOME/.sdkman/candidates/java/\$(cat \$HOME/var/${fileName})
        PATH=\$JAVA_HOME/bin:\$PATH
    fi
    """
}
