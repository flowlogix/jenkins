// Delete payara domain

def call(def payara_config) {
    if (payara_config.asadmin && payara_config.domain_name) {
        sh "$payara_config.asadmin delete-domain $payara_config.domaindir_args $payara_config.domain_name"
    }
}
