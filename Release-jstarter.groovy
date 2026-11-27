@Library('util') _l1

// Releases the JStarter CLI from flowlogix/apps: builds its native executables on GitHub's runners
// with this repository's native-build.yml workflow, uploads their zips to dl.flowlogix.com, then runs
// JReleaser, which publishes them through its configured channels. No GitHub release is created;
// dl.flowlogix.com hosts the downloads. JReleaser's config comes from the util library's jreleaser/native.yaml template.
// The Linux executables are also published as APT and DNF repositories by the publishLinuxRepositories step,
// and the Windows one as a Chocolatey package by chocolatey-package.yml and the publishChocolatey step.

def name = 'jstarter'                // the tool, its executable, zips, packages and tag
def repository = 'apps'              // under flowlogix on GitHub
def project = 'starter-generator'    // Maven project that holds the CLI module

// Shared by the Linux packages and JReleaser
def about = [
        description: 'Java and Jakarta EE project starter by Flow Logix',
        homepage   : 'https://start.flowlogix.com/',
        license    : 'Apache-2.0',
]
def longDescription = 'JStarter creates a complete, testable Java or Jakarta EE project ' +
        'from the Flow Logix starter archetype, using the same generator as start.flowlogix.com.'

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
    }
    parameters {
        string(name: 'Version', description: 'Version number to release, e.g. 1.0.0', trim: true)
        booleanParam(name: 'createTag', defaultValue: true,
                description: "Whether to push the $name-v<Version> tag to GitHub")
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
                    currentBuild.description = "$name $Version"
                }
            }
        }
        stage('Build on GitHub') {
            steps {
                script {
                    // Reuses a successful build of this commit and version, so a retry doesn't rebuild
                    env.BUILD_RUN = runGithubWorkflow('native-build.yml', [
                            repository: "flowlogix/$repository",
                            commit    : env.GIT_COMMIT,
                            version   : Version,
                            project   : project,
                            module    : 'cli',
                            executable: name,
                            name      : name,
                    ], "$name-$Version-${env.GIT_COMMIT}")
                    runGithubWorkflow 'linux-packages.yml', about + [
                            'build-run': env.BUILD_RUN,
                            name       : name,
                            version    : Version,
                            executable : name,
                            maintainer : 'Flow Logix, Inc. <noreply@flowlogix.com>',
                    ]
                }
            }
        }
        stage('Upload') {
            steps {
                sh "ssh ${websiteHost()} mkdir -p ${downloadsDir()}/releases/$name/$Version"
                sh "scp dist/$name-$Version-*.zip ${websiteHost()}:${downloadsDir()}/releases/$name/$Version/"
            }
        }
        stage('Linux repositories') {
            steps {
                publishLinuxRepositories name
            }
        }
        stage('Chocolatey') {
            steps {
                script {
                    // Packs and test-installs the package on GitHub, from the zip uploaded above
                    runGithubWorkflow 'chocolatey-package.yml', [
                            'build-run': env.BUILD_RUN,
                            name       : name,
                            version    : Version,
                            metadata   : writeJSON(returnText: true, json: [
                                    title      : 'JStarter',
                                    authors    : 'Flow Logix, Inc.',
                                    owners     : 'lprimak',
                                    summary    : about.description,
                                    description: longDescription,
                                    projectUrl : about.homepage,
                                    iconUrl    : "${downloadsUrl()}/releases/$name/icon.png",
                                    licenseUrl : 'https://www.apache.org/licenses/LICENSE-2.0',
                                    copyright  : 'Copyright (C) 2011-2026 Flow Logix, Inc. All Rights Reserved.',
                                    tags       : 'java jakarta-ee maven starter cli project-generator',
                                    url        : "${downloadsUrl()}/releases/$name/$Version/$name-$Version-windows-x86_64.zip",
                                    executable : name,
                            ]),
                    ]
                }
                publishChocolatey()
            }
        }
        stage('Publish') {
            steps {
                jreleaserConfig 'native', about + [
                        name           : name,
                        executable     : name,
                        longDescription: longDescription,
                        inceptionYear  : '2011',
                        repository     : repository,
                ]
                githubAppCredentials 'JRELEASER_GITHUB_TOKEN', {
                    // JReleaser takes the version from the POM
                    sh """
                    mvn -B -ntp -C -f $project/pom.xml versions:set \
                        -DnewVersion=$Version -DprocessAllModules -DgenerateBackupPoms=false
                    DOWNLOAD_BASE=${downloadsUrl()}/releases/$name/$Version \
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
                    sh "git tag $name-v$Version && git push origin $name-v$Version"
                }
            }
        }
    }
}
