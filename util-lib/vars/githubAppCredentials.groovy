import groovy.transform.Field

// sets up an installation token of the ci-flowlogix GitHub App (flowlogix org) in tokenVariable
@Field
final String credentialsId = 'bacccb11-36f5-4f8f-b45e-26b123a438b0'

void call(String tokenVariable, Closure cl) {
    withCredentials([usernamePassword(credentialsId: credentialsId,
            usernameVariable: 'GITHUB_APP', passwordVariable: tokenVariable)]) {
        cl.call()
    }
}
