import groovy.transform.Field

// Pushes the Chocolatey packages that chocolatey-package.yml built into <dir> to the Chocolatey Community
// Repository, where they go through its moderation before users can install them.
@Field
final String credentialsId = '95830c2b-0b8f-493e-b309-eb21515ca3e9'

void call(String dir = 'dist') {
    withCredentials([string(credentialsId: credentialsId, variable: 'CHOCOLATEY_API_KEY')]) {
        withEnv(["DIR=$dir"]) {
            sh '''
            set -o pipefail
            for nupkg in "$DIR"/*.nupkg; do
                # The API key goes to curl as a header on stdin, never on its command line
                printf 'X-NuGet-ApiKey: %s\\n' "$CHOCOLATEY_API_KEY" | curl -fsS -X PUT -H @- \
                    -F "package=@$nupkg" https://push.chocolatey.org/api/v2/package/
            done
            '''
        }
    }
}
