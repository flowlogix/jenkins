// Download, Create domain and Start Payara
// Requirement: domain_name=<your_domain>

import groovy.transform.Field

@Field
final def payara_base = "$WORKSPACE/target/dependency/payara5"
@Field
final def portbase = 4900 + (env.EXECUTOR_NUMBER as int) * 100

@Field
final def payara_default_config =
    [domain_name : 'domain1', payara_version : '5.2022.1', asadmin : "$payara_base/bin/asadmin"]

def call(def payara_config) {
    payara_config << payara_default_config + payara_config
    if (!payara_config.domain_name) {
        payara_config.asadmin = null
        error 'domain_name not specified'
    }

    sh """mvn dependency:unpack \
          -Dartifact=fish.payara.distributions:payara:${payara_config.payara_version}:zip -DoverWrite=false
       """
    sh "$payara_config.asadmin create-domain --nopassword --portbase $portbase $payara_config.domain_name || exit 0"
    payara_config.admin_port = sh(
        script: "$payara_config.asadmin list-domains --long --header=false | fgrep $payara_config.domain_name | awk '{print \$3}'",
        returnStdout: true).trim()
    sh "$payara_config.asadmin start-domain $payara_config.domain_name"
}
