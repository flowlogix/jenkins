import groovy.transform.Field

// sets up hosting provider ftp credentials
@Field
final String credentialsId = '8695b924-52bd-4fc2-9752-42041489b734'

void call(Closure cl) {
    withCredentials([usernamePassword(credentialsId: credentialsId,
            usernameVariable: 'ftpcreds_USR', passwordVariable: 'LFTP_PASSWORD')]) {
        cl.call()
    }
}
