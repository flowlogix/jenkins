def org_credential = 'bacccb11-36f5-4f8f-b45e-26b123a438b0'
def personal_credential = '187c3f6e-6b13-4024-8df2-10e702f213f8'

def githubMain = {
    ctx, String repoName, boolean personal = false -> ctx.with {
        credentialsId personal ? personal_credential : org_credential
        repoOwner personal ? 'lprimak' : 'flowlogix'
        repository repoName
        repositoryUrl ''
        configuredByUrl false
    }
}

def githubParameters = {
    ctx, String ctxLabel, boolean useTypeSuffix = true, boolean excludeForks = true -> ctx.with {
        apiUri 'https://api.github.com'
        traits {
            if (excludeForks) {
                gitHubExcludeForkedRepositories()
            }
            gitHubBranchDiscovery { strategyId 1 }
            gitHubPullRequestDiscovery { strategyId 1 }
            gitHubStatusChecks { name "CI/$ctxLabel/status" }
            cleanBeforeCheckoutTrait { extension { deleteUntrackedNestedRepositories true } }
            notificationContextTrait {
                contextLabel "CI/$ctxLabel"
                typeSuffix useTypeSuffix
            }
            submoduleOptionTrait {
                extension {
                    recursiveSubmodules true
                    trackingSubmodules true
                    shallow true
                    disableSubmodules false
                    reference ''
                    timeout null
                    parentCredentials false
                }
            }
        }
    }
}

def githubScriptSource = {
    ctx, String marker, String jenkinsScript -> ctx.with {
        localMarker marker
        remoteJenkinsFile jenkinsScript
        fallbackBranch 'main'
        matchBranches false
        remoteJenkinsFileSCM {
            gitSCM {
                userRemoteConfigs {
                    userRemoteConfig {
                        url 'git@github.com:flowlogix/jenkins.git'
                        credentialsId org_credential
                        name ''
                        refspec ''
                        browser { }
                        gitTool ''
                    }
                }
                branches {
                    branchSpec {
                        name '*/main'
                    }
                }
                extensions {
                    gitSCMStatusChecksExtension {
                        skip true
                        skipProgressUpdates true
                    }
                }
            }
        }
    }
}

def libraryDef = {
    ctx, String libraryName, String libraryPathP -> ctx.with {
        libraryConfiguration {
            name libraryName
            defaultVersion 'main'
            allowVersionOverride true
            includeInChangesets true
            retriever {
                modernSCM {
                    scm {
                        github {
                            githubMain delegate, 'jenkins'
                            traits {
                                disableStatusUpdateTrait()
                                gitHubStatusChecks {
                                    skip true
                                    skipProgressUpdates true
                                    skipNotifications true
                                }
                            }
                        }
                    }
                    libraryPath libraryPathP + '/'
                }
            }
        }
    }
}

def defaultOrphanItemStrategy = {
    ctx, dtk = '-1', ntk = '-1' -> ctx.with {
        orphanedItemStrategy {
            defaultOrphanedItemStrategy {
                pruneDeadBranches true
                daysToKeepStr dtk
                numToKeepStr ntk
            }
        }

    }
}

def suppressBranchTriggers = {
    ctx, String branches = '' -> ctx.with {
        strategy {
            allBranchesSame {
                props {
                    suppressAutomaticTriggering {
                        strategy 'NONE'
                        triggeredBranchesRegex branches
                    }
                }
            }
        }
    }
}

def buildBranchesAndPullRequests = {
    ctx, boolean ignoreUntrusted = true, String branches = '' -> ctx.with {
        buildStrategies {
            buildChangeRequests {
                ignoreUntrustedChanges ignoreUntrusted
                ignoreTargetOnlyChanges false
            }
            if (branches) {
                buildNamedBranches {
                    filters {
                        wildcards {
                            includes branches
                            excludes ''
                            caseSensitive false
                        }
                    }
                }
            }
        }
    }
}

def discoverPullRequestFromForks = {
    ctx, boolean isBranchSource, String trustStr ->
    ctx.with {
        configure {
            def doTraits = {
                def traits = it / 'traits'
                traits << 'org.jenkinsci.plugins.github__branch__source.ForkPullRequestDiscoveryTrait' {
                    strategyId 1
                    trust(class: 'org.jenkinsci.plugins.github_branch_source.ForkPullRequestDiscoveryTrait$' + trustStr)
                }
            }
            if (isBranchSource) {
                (it / sources / data / 'jenkins.branch.BranchSource').findAll { it.name() == 'source' }.each {
                    doTraits it
                }
            } else {
                (it / navigators).findAll { it.name() == 'org.jenkinsci.plugins.github__branch__source.GitHubSCMNavigator' }.each {
                    doTraits it
                }
            }
        }
    }
}

def triggerPullRequestBuild = {
    ctx, String branchPropType, String commentBodyStr ->
    ctx.with {
        configure {
            it << strategy(class: 'jenkins.branch.DefaultBranchPropertyStrategy') {
                properties {
                "com.adobe.jenkins.github__pr__comment__build.$branchPropType" {
                        commentBody commentBodyStr
                        minimumPermissions 'WRITE'
                    }
                }
            }
        }
    }
}

organizationFolder('flowlogix-org-repo') {
    displayName 'FlowLogix Org Unit Tests and PR Builder'
    organizations {
        github {
            repoOwner 'flowlogix'
            credentialsId org_credential
            githubParameters delegate, 'unit-tests', true
        }
        github {
            repoOwner 'lprimak'
            credentialsId personal_credential
            githubParameters delegate, 'unit-tests', true
        }
        triggers {
            periodicFolderTrigger {
                interval '1d'
            }
        }
        projectFactories {
            remoteJenkinsFileWorkflowMultiBranchProjectFactory {
                githubScriptSource delegate, 'pom.xml', 'UnitTests.groovy'
            }
        }
        properties {
            folderLibraries {
                libraries {
                    libraryDef delegate, 'payara', 'payara-lib'
                    libraryDef delegate, 'util', 'util-lib'
                }
            }
        }
    }

    buildBranchesAndPullRequests delegate
    discoverPullRequestFromForks delegate, false, 'TrustContributors'
    triggerPullRequestBuild delegate, 'TriggerPRCommentBranchProperty', '.*jenkins.*test.*'
}

multibranchPipelineJob('flowlogix-ee-integration') {
    displayName 'FlowLogix JEE Integration Tests'
    description 'Flow Logix JavaEE Utility Libraries for Shiro and PrimeFaces'
    branchSources {
        branchSource {
            source {
                github {
                    id '7234871'
                    githubMain delegate, 'flowlogix'
                    githubParameters delegate, 'integration-tests', false, false
                }
            }
            suppressBranchTriggers delegate
            buildStrategies {
                skipInitialBuildOnFirstBranchIndexing()
            }
        }
    }
    factory {
        remoteJenkinsFileWorkflowBranchProjectFactory {
            githubScriptSource delegate, 'pom.xml', 'IntegrationTests.groovy'
        }
    }
    triggers {
        periodicFolderTrigger {
            interval '1d'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'payara', 'payara-lib'
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate
    discoverPullRequestFromForks delegate, true, 'TrustPermission'
}

multibranchPipelineJob('flowlogix-ee-release') {
    displayName 'Release FlowLogix JEE'
    branchSources {
        branchSource {
            source {
                git {
                    id '1948134'
                    remote 'git@github.com:flowlogix/flowlogix.git'
                    credentialsId org_credential
                    traits {
                        gitBranchDiscovery()
                        wipeWorkspaceTrait()
                    }
                }
            }
            suppressBranchTriggers delegate
        }
    }
    factory {
        remoteJenkinsFileWorkflowBranchProjectFactory {
            githubScriptSource delegate, 'pom.xml', 'Release-JEE.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'payara', 'payara-lib'
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate, '1', '2'
}

multibranchPipelineJob('flowlogix-website-builder') {
    displayName 'Flow Logix Web Site Publisher'
    description 'Hope and Flow Logix Web Site Publisher'
    branchSources {
        branchSource {
            source {
                github {
                    id '41435354'
                    githubMain delegate, 'website'
                    githubParameters delegate, 'PublishWebsite', false, false
                }
            }
            buildBranchesAndPullRequests delegate, false, 'main master'
        }
    }
    factory {
        remoteJenkinsFileWorkflowBranchProjectFactory {
            githubScriptSource delegate, '', 'PublishWebsite.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate
    discoverPullRequestFromForks delegate, true, 'TrustContributors'
}

multibranchPipelineJob('hope-website-builder') {
    displayName 'Hope Web Site Publisher'
    description 'Hope and Flow Logix Web Site Publisher'
    branchSources {
        branchSource {
            source {
                github {
                    id '3451246'
                    githubMain delegate, 'hope-website', true
                    githubParameters delegate, 'PublishWebsite', false, false
                }
            }
            buildBranchesAndPullRequests delegate, false, 'main master'
        }
    }
    factory {
        remoteJenkinsFileWorkflowBranchProjectFactory {
            githubScriptSource delegate, '', 'PublishWebsite.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate
    discoverPullRequestFromForks delegate, true, 'TrustContributors'
}

multibranchPipelineJob('resume-builder') {
    displayName 'Resume Builder'
    branchSources {
        branchSource {
            source {
                github {
                    id '15436536'
                    githubMain delegate, 'resume', true
                    githubParameters delegate, 'PublishResume', false, false
                }
            }
            buildBranchesAndPullRequests delegate, false, 'main master'
        }
    }
    factory {
        remoteJenkinsFileWorkflowBranchProjectFactory {
            githubScriptSource delegate, '', 'PublishResume.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate
    discoverPullRequestFromForks delegate, true, 'TrustContributors'
}

multibranchPipelineJob('shiro') {
    displayName 'Apache Shiro CI'
    description 'Apache Shiro Continuous Integration'
    branchSources {
        branchSource {
            source {
                github {
                    id '15413536'
                    githubMain delegate, 'shiro', true
                    githubParameters delegate, 'shiro-tests', false, false
                }
            }
            suppressBranchTriggers delegate
            buildStrategies {
                skipInitialBuildOnFirstBranchIndexing()
            }
        }
    }
    factory {
        remoteJenkinsFileWorkflowBranchProjectFactory {
            githubScriptSource delegate, 'pom.xml', 'UnitTests.groovy'
        }
    }
    triggers {
        periodicFolderTrigger {
            interval '1d'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'payara', 'payara-lib'
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate
}
