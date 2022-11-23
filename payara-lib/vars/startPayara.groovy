// Download, Create domain and Start Payara
// Requirement: domain_name=<your_domain>

import groovy.transform.Field

@Field
final def workspace_base = "$WORKSPACE/target/dependency"
@Field
final def tmpdir = "$workspace_base/tmpdir"
@Field
final def portbase = 4900 + (env.EXECUTOR_NUMBER as int) * 100

@Field
final def payara_default_config =
    [ domain_name : 'domain1', payara_version : 'current', asadmin : "$HOME/apps/payara/current/bin/asadmin",
      jacoco_profile : '' ]

def call(def payara_config) {
    boolean payara_version_overridden = false
    if (!payara_config.asadmin && payara_config.payara_version) {
        payara_default_config.asadmin = "$HOME/apps/payara/$payara_config.payara_version/bin/asadmin"
        payara_version_overridden = true
    }
    payara_config << payara_default_config + payara_config
    if (!payara_config.domain_name) {
        payara_config.asadmin = null
        error 'domain_name not specified'
        return 1
    } else if (!fileExists("$WORKSPACE/.jenkins_payara")) {
        payara_config.asadmin = null
        return 0
    } else {
        def overridden_version = readFile(file: "$WORKSPACE/.jenkins_payara").trim()
        if (overridden_version as boolean && !payara_version_overridden) {
            payara_config.payara_version = "payara-$overridden_version"
            payara_config.asadmin = "$HOME/apps/payara/$payara_config.payara_version/bin/asadmin"
        }
    }

    payara_config.domaindir_args = "--domaindir $workspace_base/payara_domaindir"
    payara_config.ssl_port = sh(
        script: """$payara_config.asadmin create-domain $payara_config.domaindir_args \
                --nopassword --portbase $portbase $payara_config.domain_name \
                | fgrep HTTP_SSL | awk '{print \$3}' || exit 0
                """,
        returnStdout: true).trim()
    payara_config.admin_port = sh(
        script: "$payara_config.asadmin list-domains $payara_config.domaindir_args --long --header=false \
        | fgrep $payara_config.domain_name | awk '{print \$3}'",
        returnStdout: true).trim()
    sh """
        $payara_config.asadmin start-domain $payara_config.domaindir_args $payara_config.domain_name
        mkdir -p $tmpdir
        $payara_config.asadmin -p $payara_config.admin_port create-system-properties java.io.tmpdir=$tmpdir
        """

    payara_config.jacoco_port = (payara_config.admin_port as int) + 10000
    def jacoco_profile_cmd = ''
    if (payara_config.jacoco_profile) {
        jacoco_profile_cmd = "-P$payara_config.jacoco_profile"
    }
    def jacoco_argline = sh(script: "mvn initialize help:evaluate $jacoco_profile_cmd \
        -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn \
        -Dexpression=jacocoAgent -q -DforceStdout -DjacocoPort=$payara_config.jacoco_port -N",
        returnStdout: true).trim()
    if (jacoco_argline?.startsWith('-javaagent')) {
        def escaped_argline = jacoco_argline.replaceAll(/[\/:=]/, /\\\\$0/)
        sh """
            $payara_config.asadmin -p $payara_config.admin_port create-jvm-options $escaped_argline,output=tcpserver
            $payara_config.asadmin -p $payara_config.admin_port restart-domain $payara_config.domaindir_args $payara_config.domain_name
        """
    }
}
