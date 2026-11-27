@Library('util') _l1

// Releases the JStarter CLI from flowlogix/apps: builds the native executable for the agent's platform,
// deploys its zip to FlowLogix Nexus, then runs JReleaser, which publishes the Homebrew formula.
// No GitHub release is created; Nexus hosts the download.
// JReleaser's config comes from the util library's jreleaser/native-homebrew.yaml template.

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
    }
    parameters {
        string(name: 'Version', description: 'Version number to release, e.g. 1.0.0', trim: true)
        booleanParam(name: 'publishHomebrew', defaultValue: true,
                description: 'Whether to run JReleaser, which updates the Homebrew formula')
        booleanParam(name: 'createTag', defaultValue: true,
                description: 'Whether to push the jstarter-v<Version> tag to GitHub')
    }

    stages {
        stage('Setup') {
            steps {
                script {
                    if (Version.empty) {
                        def msg = 'Version cannot be empty'
                        currentBuild.description = msg
                        error msg
                    }
                    // A native build only targets the agent's own platform, so the zip is named after it
                    env.PLATFORM = agentPlatform()
                    currentBuild.description = "jstarter $Version $PLATFORM"
                }
            }
        }
        stage('Build native') {
            steps {
                sh """
                ${java25('graalvm-version')}
                mvn -B -ntp -C -f starter-generator/pom.xml versions:set \
                    -DnewVersion=$Version -DprocessAllModules -DgenerateBackupPoms=false
                mvn -B -ntp -C -f starter-generator/pom.xml -pl cli -am verify -Dnative \
                    -Dquarkus.native.container-build=false
                """
            }
        }
        stage('Package') {
            steps {
                sh """
                D=jstarter-$Version
                rm -rf dist && mkdir -p dist/\$D/bin
                cp starter-generator/cli/target/*-runner dist/\$D/bin/jstarter
                (cd dist && zip -qr \$D-\$PLATFORM.zip \$D)
                """
            }
        }
        stage('Deploy to Nexus') {
            steps {
                mavenSettingsCredentials false, {
                    dir('dist') {
                        sh """
                        mvn -B -ntp -C deploy:deploy-file \
                            -DrepositoryId=${nexusRepositoryId()} -Durl=${nexusReleasesUrl()} \
                            -Dfile=jstarter-$Version-\$PLATFORM.zip -DgroupId=com.flowlogix \
                            -DartifactId=jstarter -Dversion=$Version -Dpackaging=zip \
                            -Dclassifier=\$PLATFORM -DgeneratePom=true
                        """
                    }
                }
            }
        }
        stage('Homebrew') {
            when {
                expression { publishHomebrew.toBoolean() }
            }
            steps {
                jreleaserConfig 'native-homebrew', [
                        name           : 'jstarter',
                        executable     : 'jstarter',
                        description    : 'Java and Jakarta EE project starter by Flow Logix',
                        longDescription: 'JStarter creates a complete, testable Java or Jakarta EE project ' +
                                'from the Flow Logix starter archetype, using the same generator as start.flowlogix.com.',
                        homepage       : 'https://start.flowlogix.com/',
                        license        : 'Apache-2.0',
                        inceptionYear  : '2024',
                        repository     : 'apps',
                ]
                githubAppCredentials 'JRELEASER_GITHUB_TOKEN', {
                    // JReleaser takes the version from the POM, which 'Build native' set to $Version,
                    // and publishes only the zip for the platform it is running on
                    sh """
                    ${java25('graalvm-version')}
                    DOWNLOAD_BASE=${nexusReleasesUrl()}/com/flowlogix/jstarter/$Version \
                    mvn -B -ntp -C -N org.jreleaser:jreleaser-maven-plugin:RELEASE:full-release \
                        -Djreleaser.config.file=jreleaser.yaml -Djreleaser.select.current.platform=true
                    """
                }
            }
        }
    }

    post {
        success {
            script {
                if (createTag.toBoolean()) {
                    sh "git tag jstarter-v$Version && git push origin jstarter-v$Version"
                }
            }
        }
    }
}
