// Download, Create domain and Start Payara
// Requirement: domain_name=<your_domain>

import groovy.transform.Field

@Field
final def payara_base = "$WORKSPACE/target/dependency/payara5"
@Field
final def tmpdir = "$WORKSPACE/target/dependency/tmpdir"
@Field
final def portbase = 4900 + (env.EXECUTOR_NUMBER as int) * 100

@Field
final def payara_default_config =
    [domain_name : 'domain1', asadmin : "$payara_base/bin/asadmin"]

def call(def payara_config) {
    payara_config << payara_default_config + payara_config
    if (!payara_config.domain_name) {
        payara_config.asadmin = null
        error 'domain_name not specified'
    }

    int result = sh(script: "mvn -B -C dependency:unpack", returnStatus: true)
    if (result != 0) {
        echo 'Not able to extract Payara'
        payara_config.asadmin = null
        return result
    }
    payara_config.ssl_port = sh(
        script: """$payara_config.asadmin create-domain --nopassword --portbase $portbase $payara_config.domain_name \
                | fgrep HTTP_SSL | awk '{print \$3}' || exit 0
                """,
        returnStdout: true).trim()
    payara_config.admin_port = sh(
        script: "$payara_config.asadmin list-domains --long --header=false | fgrep $payara_config.domain_name | awk '{print \$3}'",
        returnStdout: true).trim()
    sh "$payara_config.asadmin start-domain $payara_config.domain_name"
    sh "mkdir -p $tmpdir"
    sh "$payara_config.asadmin -p $payara_config.admin_port create-system-properties java.io.tmpdir=$tmpdir"
}
