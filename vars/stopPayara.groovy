// stop payara domain

def call() {
    createPayaraVariables()
    if (env.asadmin && env.domain_name) {
        sh "$env.asadmin stop-domain $env.domain_name"
    }
}
