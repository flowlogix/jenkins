import groovy.transform.Field

// sets GPG Signing Passphrase credentials
@Field
final String flowlogixCredentialsId = '8faf3947-efe5-4733-86d9-b3e08faa91d1'
@Field
final String apacheCredentialsId = 'ddb50232-4f82-4f93-90cc-3e1a6b2a28da'
@Field
final String mavenSettingsCredentialsId = 'a2decb6f-0847-4fa4-8cd1-57acee932efc'

void call(boolean isApache, Closure cl) {
    withCredentials(
            [string(credentialsId: isApache ? apacheCredentialsId : flowlogixCredentialsId,
                    variable: 'MAVEN_GPG_PASSPHRASE')]) {
        withCredentials(
                [string(credentialsId: mavenSettingsCredentialsId,
                        variable: 'MAVEN_SETTINGS_MASTER_PASSWORD')]) {
            cl.call()
        }
    }
}
