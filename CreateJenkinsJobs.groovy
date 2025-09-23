def org_credential = 'bacccb11-36f5-4f8f-b45e-26b123a438b0'
def personal_credential = '187c3f6e-6b13-4024-8df2-10e702f213f8'
def personal_credential_lprimak = '08fa0c41-d0d1-482a-86b1-96c2bdbd1483'
def private_repository_credential = '574af081-7c70-4e3f-8446-5f04a837a53f'

def githubMain = {
    ctx, String repoName, String credential = org_credential, boolean personal = false -> ctx.with {
        credentialsId credential
        repoOwner personal ? 'lprimak' : 'flowlogix'
        repository repoName
        repositoryUrl ''
        configuredByUrl false
    }
}

def githubParameters = {
    ctx, String ctxLabel, String excludeWildcard, String includeWildcard, boolean useTypeSuffix = true,
    boolean excludeForks = true, boolean trustPermissions = false, disableStatusChecks = false -> ctx.with {
        apiUri 'https://api.github.com'
        traits {
            if (excludeForks) {
                gitHubExcludeForkedRepositories()
            }
            if (excludeWildcard != null) {
                sourceWildcardFilter {
                    includes includeWildcard
                    excludes excludeWildcard
                }
            }
            gitHubBranchDiscovery { strategyId 1 }
            gitHubPullRequestDiscovery { strategyId 1 }
            gitHubForkDiscovery {
                strategyId 1
                trust {
                    if (trustPermissions) {
                        gitHubTrustPermissions()
                    } else {
                        gitHubTrustContributors()
                    }
                }
            }
            cleanBeforeCheckoutTrait { extension { deleteUntrackedNestedRepositories true } }
            notificationContextTrait {
                contextLabel "CI/$ctxLabel"
                typeSuffix useTypeSuffix
            }
            if (disableStatusChecks) {
                disableStatusUpdateTrait()
                gitHubStatusChecks {
                    skip true
                    skipProgressUpdates true
                    skipNotifications true
                }
            } else {
                gitHubStatusChecks { name "CI/$ctxLabel/status" }
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

folder ('release-jobs') {
    displayName('Software Release Jobs')
    description('All Release Jobs go here')
}

folder ('content') {
    displayName('Content Builders')
    description('Web Sites, Resume, other content')
}

organizationFolder('flowlogix-org-repo') {
    displayName 'FlowLogix Org Unit Tests and PR Builder'
    organizations {
        github {
            repoOwner 'flowlogix'
            credentialsId org_credential
            githubParameters delegate, 'unit-tests', 'jbake-maven', '*', true
        }
        github {
            repoOwner 'lprimak'
            credentialsId personal_credential
            githubParameters delegate, 'unit-tests', 'resume myonlinelogbook', '*', true
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
    triggerPullRequestBuild delegate, 'TriggerPRCommentBranchProperty', '.*jenkins.*test.*'
}

organizationFolder('lprimak-private-org-repo') {
    displayName 'Lenny Private Apps Unit Tests and PR Builder'
    organizations {
        github {
            repoOwner 'lprimak'
            credentialsId private_repository_credential
            githubParameters delegate, 'unit-tests', '', 'myonlinelogbook', true
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
    triggerPullRequestBuild delegate, 'TriggerPRCommentBranchProperty', '.*jenkins.*test.*'
}

organizationFolder('mark-primak-repo') {
    displayName 'Mark Primak Unit Tests and PR Builder'
    organizations {
        github {
            repoOwner 'markprimak'
            credentialsId personal_credential
            githubParameters delegate, 'unit-tests', '', '*', true
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
    triggerPullRequestBuild delegate, 'TriggerPRCommentBranchProperty', '.*jenkins.*test.*'
}

multibranchPipelineJob('flowlogix-ee-integration') {
    displayName 'FlowLogix JEE Integration Tests'
    description 'Flow Logix Components for Jakarta EE and PrimeFaces'
    branchSources {
        branchSource {
            source {
                github {
                    id '7234871'
                    githubMain delegate, 'flowlogix'
                    githubParameters delegate, 'integration-tests', null, null, false, false, true
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
}

multibranchPipelineJob('flowlogix-ee-docs') {
    displayName 'FlowLogix JEE - Deploy Documentation'
    description 'Documentation for Flow Logix Components for Jakarta EE and PrimeFaces'
    branchSources {
        branchSource {
            source {
                github {
                    id '7234882'
                    githubMain delegate, 'flowlogix'
                    githubParameters delegate, 'docs', null, null, false, false, true, true
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
            githubScriptSource delegate, 'pom.xml', 'DeployDocs.groovy'
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
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate
}

multibranchPipelineJob('release-jobs/flowlogix-ee-release') {
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

multibranchPipelineJob('content/flowlogix-website-builder') {
    displayName 'Flow Logix Web Site Publisher'
    description 'Hope and Flow Logix Web Site Publisher'
    branchSources {
        branchSource {
            source {
                github {
                    id '41435354'
                    githubMain delegate, 'website'
                    githubParameters delegate, 'PublishWebsite', null, null, false, false
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
}

multibranchPipelineJob('content/hope-website-builder') {
    displayName 'Hope Web Site Publisher'
    description 'Hope and Flow Logix Web Site Publisher'
    branchSources {
        branchSource {
            source {
                github {
                    id '3451246'
                    githubMain delegate, 'hope-website', personal_credential, true
                    githubParameters delegate, 'PublishWebsite', null, null, false, false
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
}

multibranchPipelineJob('content/resume-builder') {
    displayName 'Resume Builder'
    branchSources {
        branchSource {
            source {
                github {
                    id '15436536'
                    githubMain delegate, 'resume', personal_credential, true
                    githubParameters delegate, 'PublishResume', null, null, false, false
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
}

multibranchPipelineJob('apache-shiro-ci') {
    displayName 'Apache Shiro CI'
    description 'Apache Shiro Continuous Integration'
    branchSources {
        branchSource {
            source {
                github {
                    id '15413536'
                    githubMain delegate, 'shiro', personal_credential, true
                    githubParameters delegate, 'shiro-tests', null, null, false, false
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

multibranchPipelineJob('release-jobs/apache-shiro-fork-release') {
    displayName 'Apache Shiro (fork) - Release'
    branchSources {
        branchSource {
            source {
                git {
                    id '1948045'
                    remote 'git@github.com:lprimak/shiro.git'
                    credentialsId personal_credential_lprimak
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
            githubScriptSource delegate, 'pom.xml', 'Release-apache-shiro.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate, '1', '2'
}

multibranchPipelineJob('release-jobs/apache-shiro-release') {
    displayName 'Apache Shiro - Release'
    branchSources {
        branchSource {
            source {
                git {
                    id '1948073'
                    remote 'git@github.com:apache/shiro.git'
                    credentialsId personal_credential_lprimak
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
            githubScriptSource delegate, 'pom.xml', 'Release-apache-shiro.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate, '1', '2'
}

multibranchPipelineJob('release-jobs/jakartaee-api-release') {
    displayName 'Jakarta EE API - Release'
    branchSources {
        branchSource {
            source {
                git {
                    id '1918647'
                    remote 'git@github.com:flowlogix/jakartaee-api.git'
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
            githubScriptSource delegate, 'pom.xml', 'Release-jakartaee-api.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate, '1', '2'
}

multibranchPipelineJob('release-jobs/arquillian-drone-release') {
    displayName 'Arquillian Drone - Release'
    branchSources {
        branchSource {
            source {
                git {
                    id '1915412'
                    remote 'git@github.com:flowlogix/arquillian-extension-drone.git'
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
            githubScriptSource delegate, 'pom.xml', 'Release-arquillian-drone.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate, '1', '2'
}

multibranchPipelineJob('release-jobs/arquillian-graphene-release') {
    displayName 'Arquillian Graphene - Release'
    branchSources {
        branchSource {
            source {
                git {
                    id '1926723'
                    remote 'git@github.com:flowlogix/arquillian-graphene.git'
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
            githubScriptSource delegate, 'pom.xml', 'Release-arquillian-graphene.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate, '1', '2'
}

multibranchPipelineJob('release-jobs/weld-native-release') {
    displayName 'Weld Native - Release'
    branchSources {
        branchSource {
            source {
                git {
                    id '2944124'
                    remote 'git@github.com:flowlogix/weld-native.git'
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
            githubScriptSource delegate, 'pom.xml', 'Release-without-payara.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate, '1', '2'
}

multibranchPipelineJob('release-jobs/payara-arquillian-release') {
    displayName 'Payara Arquillian Connector - Release'
    branchSources {
        branchSource {
            source {
                git {
                    id '2944633'
                    remote 'git@github.com:flowlogix/ecosystem-arquillian-connectors.git'
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
            githubScriptSource delegate, 'pom.xml', 'Release-payara-arquillian.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate, '1', '2'
}

multibranchPipelineJob('release-jobs/base-pom-release') {
    displayName 'Base Maven POM - Release'
    branchSources {
        branchSource {
            source {
                git {
                    id '28441187'
                    remote 'git@github.com:flowlogix/base-pom.git'
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
            githubScriptSource delegate, 'pom.xml', 'Release-base-pom.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate, '1', '2'
}

multibranchPipelineJob('release-jobs/depchain-release') {
    displayName 'Dependency Chain - Release'
    branchSources {
        branchSource {
            source {
                git {
                    id '28441297'
                    remote 'git@github.com:flowlogix/depchain.git'
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
            githubScriptSource delegate, 'pom.xml', 'Release-without-payara.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate, '1', '2'
}

multibranchPipelineJob('release-jobs/faces-affinity-release') {
    displayName 'Jakarta Faces Affinity - Release'
    branchSources {
        branchSource {
            source {
                git {
                    id '28438712'
                    remote 'git@github.com:flowlogix/faces-affinity.git'
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
            githubScriptSource delegate, 'pom.xml', 'Release-without-payara.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate, '1', '2'
}

multibranchPipelineJob('release-jobs/shrinkwrap-resolver-release') {
    displayName 'ShrinkWrap Resolver - Release'
    branchSources {
        branchSource {
            source {
                git {
                    id '25464563'
                    remote 'git@github.com:flowlogix/resolver.git'
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
            githubScriptSource delegate, 'pom.xml', 'Release-shrinkwrap-resolver.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate, '1', '2'
}

multibranchPipelineJob('release-jobs/maven-war-plugin-release') {
    displayName 'Maven WAR Plugin - Release'
    branchSources {
        branchSource {
            source {
                git {
                    id '25465162'
                    remote 'git@github.com:lprimak/maven-war-plugin.git'
                    credentialsId personal_credential_lprimak
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
            githubScriptSource delegate, 'pom.xml', 'Release-maven-war-plugin.groovy'
        }
    }
    properties {
        folderLibraries {
            libraries {
                libraryDef delegate, 'util', 'util-lib'
            }
        }
    }
    defaultOrphanItemStrategy delegate, '1', '2'
}
