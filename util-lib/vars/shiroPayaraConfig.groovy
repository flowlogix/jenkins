String call(def payara_config) {
    echo 'Configuring Shiro Payara settings'
    payara_config.jacoco_expr_args = '-pl :jakarta-ee-support'
    payara_config.payara_version = 'payara-5'
    payara_config.force_start = true
}
