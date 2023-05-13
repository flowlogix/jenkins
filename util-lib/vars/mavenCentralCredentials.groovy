import groovy.transform.Field

// sets up maven central credentials
@Field
final String credentialsId = '0e35c931-3275-426a-95bf-a0c21279d013'

void call(Closure cl) {
    withCredentials([usernamePassword(credentialsId: credentialsId,
            usernameVariable: 'nexus_user', passwordVariable: 'nexus_password')]) {
        cl.call()
    }
}
