// Download, Create domain and Start Payara
// Requirement: domain_name=<your_domain>

import groovy.transform.Field

@Field
final def workspace_base = "$WORKSPACE/target/dependency"
@Field
final def payara_base = "$workspace_base/payara5"
@Field
final def tmpdir = "$workspace_base/tmpdir"
@Field
final def portbase = 4900 + (env.EXECUTOR_NUMBER as int) * 100

@Field
final def payara_default_config =
    [domain_name : 'domain1', asadmin : "$payara_base/bin/asadmin", domaindir_args: '']

def call(def payara_config) {
    payara_config << payara_default_config + payara_config
    if (!payara_config.domain_name) {
        payara_config.asadmin = null
        error 'domain_name not specified'
        return 1;
    }

    int result = sh(script: "mvn -B -C dependency:unpack", returnStatus: true)
    if (result != 0) {
        echo 'Not able to extract Payara'
        payara_config.asadmin = null
    }

    result = sh(script: "$payara_config.asadmin -V", returnStatus: true)
    if (result != 0) {
        payara_config.asadmin = "$HOME/apps/payara/current/bin/asadmin"
        payara_config.domaindir_args = "--domaindir $workspace_base/payara_domaindir"
    }
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
    sh "$payara_config.asadmin start-domain $payara_config.domaindir_args $payara_config.domain_name"
    sh "mkdir -p $tmpdir"
    sh "$payara_config.asadmin -p $payara_config.admin_port create-system-properties java.io.tmpdir=$tmpdir"
}
