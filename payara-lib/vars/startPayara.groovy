// Download, Create domain and Start Payara
// Requirement: domain_name=<your_domain>

import groovy.transform.Field

@Field
final def portbase = 4900 + (env.EXECUTOR_NUMBER as int) * 100
@Field
final def jenkinsPayaraFileName = "$WORKSPACE/.jenkins_payara"
@Field
final def jenkinsIgnoreJacocoFileName = "$WORKSPACE/.jenkins_no_remote_jacoco"

@Field
final def payara_default_config =
    [ domain_name : 'domain1', payara_version : 'current',
      asadmin : "$HOME/apps/payara/current/bin/asadmin",
      force_start : false, workspace_base : "$WORKSPACE/target/dependency",
      create_domain : true, domaindir_args : '', jacoco_started : false,
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

    def cache_dir = "$HOME/.cache/jenkins-payara-domains/${payara_config.payara_version}-$portbase"
    def using_cache = false
    if (payara_config.create_domain) {
        payara_config.domaindir_args = "--domaindir $payara_config.workspace_base/payara_domaindir"

        if (fileExists(cache_dir) && cacheValid(payara_config, cache_dir)) {
            using_cache = true
            echo "Using cached Payara domain from $cache_dir"
            sh """mkdir -p $payara_config.workspace_base/payara_domaindir
                cp -R $cache_dir $payara_config.workspace_base/payara_domaindir/$payara_config.domain_name
            """
        } else {
            sh """$payara_config.asadmin create-domain $payara_config.domaindir_args \
                  --nopassword --portbase $portbase $payara_config.domain_name"""
        }
    } else {
        payara_config.domain_name = ''
    }

    if (!payara_config.admin_port) {
        payara_config.admin_port = payara_config.create_domain ? portbase + 48 : 4848
    }
    if (!payara_config.ssl_port) {
        payara_config.ssl_port = payara_config.create_domain ? portbase + 81 : 8181
    }

    String tmp_dir_option = '-D_empty_tmpdir=empty'
    if (payara_config.create_domain) {
        sh "mkdir -p $payara_config.workspace_base/tmpdir"
        tmp_dir_option = "-Djava.io.tmpdir=$payara_config.workspace_base/tmpdir"
    }

    sh "PAYARA_JACOCO_OPTIONS=\"${jacocoCommandLine(payara_config)}\" \
        PAYARA_TMPDIR_OPTIONS=$tmp_dir_option \
        $payara_config.asadmin start-domain \
        $payara_config.domaindir_args $payara_config.domain_name"

    if (payara_config.create_domain) {
        if (!using_cache) {
            sh "$payara_config.asadmin -p $payara_config.admin_port \
                create-jvm-options \\\${ENV=PAYARA_JACOCO_OPTIONS}:\\\${ENV=PAYARA_TMPDIR_OPTIONS}"
            sh "rm -f $cache_dir/logs/server.log; \
                $payara_config.asadmin -p $payara_config.admin_port restart-domain \
                $payara_config.domaindir_args $payara_config.domain_name"
        }

        if (!fileExists(cache_dir)) {
            echo "Caching Payara domain to $cache_dir"
            sh """mkdir -p $HOME/.cache/jenkins-payara-domains
                cp -R $payara_config.workspace_base/payara_domaindir/$payara_config.domain_name $cache_dir
                rm -f $cache_dir/logs/server.log
                touch -r $payara_config.asadmin $cache_dir
            """
        } else {
            echo "Not caching Payara domain as cache is valid: $cache_dir"
        }
    }
}

boolean cacheValid(def payara_config, String cache_dir) {
    def sourceMTime = sh(script: "stat -f %m ${payara_config.asadmin}", returnStdout: true).trim()
    def targetMTime = sh(script: "stat -f %m ${cache_dir}", returnStdout: true).trim()

    if (sourceMTime != targetMTime) {
        echo "Cache invalid for $cache_dir, deleting it"
        sh "rm -rf $cache_dir"
        return false
    }
    return true
}

String jacocoCommandLine(def payara_config) {
    if (fileExists(jenkinsIgnoreJacocoFileName)) {
        return '-D_empty_jacoco=empty'
    }

    payara_config.jacoco_port = (payara_config.admin_port as int) + 10000
    def jacoco_profile_cmd = ''
    if (payara_config.jacoco_profile) {
        jacoco_profile_cmd = "-P$payara_config.jacoco_profile"
    }

    def jacoco_argline = ''
    def jacocoConfigOutput = "$WORKSPACE/target/jacoco-agent-config.txt"

    def maven_options_override = ''
    if (payara_config.create_domain && !fileExists("$HOME/.cache/maven.aot")) {
        echo "Creating Maven AOT cache"
        maven_options_override = "MAVEN_OPTS='-XX:+IgnoreUnrecognizedVMOptions -XX:AOTCacheOutput=$HOME/.cache/maven.aot'"
    }
    sh "$maven_options_override mvn -ntp initialize help:evaluate $jacoco_profile_cmd \
        -Dexpression=jacocoAgent -q -DjacocoPort=$payara_config.jacoco_port \
        -Djacoco.destFile=$WORKSPACE/target/jacoco-it.exec -Doutput=$jacocoConfigOutput \
        ${payara_config.jacoco_tcp_server ? '-N' : ''} $payara_config.jacoco_expr_args || exit 0"
    if (fileExists(jacocoConfigOutput)) {
        jacoco_argline = readFile(file: jacocoConfigOutput).trim()
    }

    if (jacoco_argline?.startsWith('-javaagent')) {
        def tcp_server_output = payara_config.jacoco_tcp_server ? ',output=tcpserver' : ''
        payara_config.jacoco_started = true
        def jacoco_argline_shell = jacoco_argline.replaceAll(/[$]/, /\\$0/)
        return "$jacoco_argline_shell$tcp_server_output"
    }
    return '-D_empty_jacoco=empty'
}
