// Download, Create domain and Start Payara
// Requirement: env.domain_name=<your_domain>

def call() {
    createPayaraVariables()
    if (!env.domain_name) {
        error 'env.domain_name not specified'
    }

    sh """cd ..
       mvn dependency:unpack -Dartifact=fish.payara.distributions:payara:5.2021.4:zip \
           -Dproject.basedir=$WORKSPACE -DoverWrite=false
       """
    sh "$env.asadmin create-domain --nopassword --portbase $env.portbase $env.domain_name || exit 0"
    env.admin_port = sh(
        script: "$env.asadmin list-domains --long --header=false | fgrep $env.domain_name | awk '{print \$3}'",
        returnStdout: true).trim()
    sh "$env.asadmin start-domain $env.domain_name"
}
