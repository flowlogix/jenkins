@Library('util') _l1

// Releases the JStarter CLI from flowlogix/apps: builds its native executables on GitHub's runners
// with this repository's native-build.yml workflow, uploads their zips to dl.flowlogix.com, then runs
// JReleaser, which publishes them through its configured channels. No GitHub release is created;
// dl.flowlogix.com hosts the downloads. JReleaser's config comes from the util library's jreleaser/native.yaml template.

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
    }
    parameters {
        string(name: 'Version', description: 'Version number to release, e.g. 1.0.0', trim: true)
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
                    currentBuild.description = "jstarter $Version"
                }
            }
        }
        stage('Build native on GitHub') {
            steps {
                runGithubWorkflow 'native-build.yml', [
                        repository: 'flowlogix/apps',
                        commit    : env.GIT_COMMIT,
                        version   : Version,
                        project   : 'starter-generator',
                        module    : 'cli',
                        executable: 'jstarter',
                        name      : 'jstarter',
                ]
            }
        }
        stage('Upload') {
            steps {
                sh "ssh ${websiteHost()} mkdir -p ${downloadsDir()}/releases/jstarter/$Version"
                sh "scp dist/jstarter-$Version-*.zip ${websiteHost()}:${downloadsDir()}/releases/jstarter/$Version/"
            }
        }
        stage('Publish') {
            steps {
                jreleaserConfig 'native', [
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
                    // JReleaser takes the version from the POM
                    sh """
                    mvn -B -ntp -C -f starter-generator/pom.xml versions:set \
                        -DnewVersion=$Version -DprocessAllModules -DgenerateBackupPoms=false
                    DOWNLOAD_BASE=${downloadsUrl()}/releases/jstarter/$Version \
                    mvn -B -ntp -C -N org.jreleaser:jreleaser-maven-plugin:RELEASE:full-release \
                        -Djreleaser.config.file=jreleaser.yaml
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
