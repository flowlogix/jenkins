// Download, Create domain and Start Payara
// Requirement: domain_name=<your_domain>

import groovy.transform.Field

@Field
final def portbase = 4900 + (env.EXECUTOR_NUMBER as int) * 100
@Field
final def jenkinsPayaraFileName = "$WORKSPACE/.jenkins_payara"
@Field
final def jenkinsJacocoFileName = "$WORKSPACE/.jenkins_no_remote_jacoco"

@Field
final def payara_default_config =
    [ domain_name : 'domain1', payara_version : 'current',
      asadmin : "$HOME/apps/payara/current/bin/asadmin",
      force_start : false, workspace_base : "$WORKSPACE/target/dependency",
      jacoco_started: false,
      jacoco_profile : '', jacoco_tcp_server : true, jacoco_expr_args : '' ]

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
    } else if (!payara_config.force_start && !fileExists(jenkinsPayaraFileName)) {
        payara_config.asadmin = null
        return 0
    } else {
        def overridden_version
        if (fileExists(jenkinsPayaraFileName)) {
            overridden_version = readFile(file: jenkinsPayaraFileName).trim()
        }
        if (overridden_version as boolean && !payara_version_overridden) {
            payara_config.payara_version = "payara-$overridden_version"
            payara_config.asadmin = "$HOME/apps/payara/$payara_config.payara_version/bin/asadmin"
        }
    }

    payara_config.domaindir_args = "--domaindir $payara_config.workspace_base/payara_domaindir"
    sh """$payara_config.asadmin create-domain $payara_config.domaindir_args \
       --nopassword --portbase $portbase $payara_config.domain_name"""

    if (!payara_config.admin_port) {
        payara_config.admin_port = portbase + 48
    }
    if (!payara_config.ssl_port) {
        payara_config.ssl_port = portbase + 81
    }
    sh """
        $payara_config.asadmin start-domain $payara_config.domaindir_args $payara_config.domain_name
        mkdir -p $payara_config.workspace_base/tmpdir
        $payara_config.asadmin -p $payara_config.admin_port create-system-properties\
            java.io.tmpdir=$payara_config.workspace_base/tmpdir
        """

    if (fileExists(jenkinsJacocoFileName)) {
        return
    }

    payara_config.jacoco_port = (payara_config.admin_port as int) + 10000
    def jacoco_profile_cmd = ''
    if (payara_config.jacoco_profile) {
        jacoco_profile_cmd = "-P$payara_config.jacoco_profile"
    }
    def jacoco_argline = sh(script: "mvn -ntp initialize help:evaluate $jacoco_profile_cmd \
        -Dexpression=jacocoAgent -q -DforceStdout -DjacocoPort=$payara_config.jacoco_port \
        ${payara_config.jacoco_tcp_server ? '-N' : ''} $payara_config.jacoco_expr_args || exit 0",
        returnStdout: true).trim()
    if (jacoco_argline?.startsWith('-javaagent')) {
        def escaped_argline = jacoco_argline.replaceAll(/[\/:=]/, /\\\\$0/).replaceAll(/[$]/, /\\$0/)
        def tcp_server_output = payara_config.jacoco_tcp_server ? ',output=tcpserver' : ''
        sh """
            $payara_config.asadmin -p $payara_config.admin_port create-jvm-options $escaped_argline$tcp_server_output
            $payara_config.asadmin -p $payara_config.admin_port restart-domain $payara_config.domaindir_args $payara_config.domain_name
        """
        payara_config.jacoco_started = true
    }
}
