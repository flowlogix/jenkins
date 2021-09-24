// stop payara domain

def call(def payara_config) {
    if (payara_config.asadmin && payara_config.domain_name) {
        sh "$payara_config.asadmin stop-domain $payara_config.domain_name"
    }
}
