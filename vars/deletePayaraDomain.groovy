// Delete payara domain

def call() {
    createPayaraVariables()
    if (env.asadmin && env.domain_name) {
        sh "$env.asadmin delete-domain $env.domain_name"
    }
}

