// Runs a workflow of flowlogix/jenkins on GitHub with the given inputs, waits for it to finish and
// unpacks each of its artifacts into dir. The workflow's run-name must end in "(<request>)", where
// request is the input this step adds to find the run it started.

void call(String workflow, Map inputs, String dir = 'dist') {
    String api = 'https://api.github.com/repos/flowlogix/jenkins/actions'
    String request = "${env.JOB_NAME}-${env.BUILD_NUMBER}".replaceAll(/[^A-Za-z0-9._-]/, '-')
    Map dispatchInputs = [request: request]
    inputs.each { key, value -> dispatchInputs[key] = value.toString() }

    githubAppCredentials 'GITHUB_TOKEN', {
        writeJSON file: 'workflow-dispatch.json', json: [ref: 'main', inputs: dispatchInputs]
        sh "curl -fsS -X POST ${headers()} $api/workflows/$workflow/dispatches -d @workflow-dispatch.json"

        def runId = null
        timeout(time: 5, unit: 'MINUTES') {
            waitUntil(initialRecurrencePeriod: 5000, quiet: true) {
                for (run in github("$api/workflows/$workflow/runs?event=workflow_dispatch&per_page=30").workflow_runs) {
                    if (run.name.endsWith("($request)")) {
                        runId = run.id
                    }
                }
                return runId != null
            }
        }
        echo "GitHub workflow run: https://github.com/flowlogix/jenkins/actions/runs/$runId"

        def run = null
        timeout(time: 90, unit: 'MINUTES') {
            waitUntil(initialRecurrencePeriod: 30000, quiet: true) {
                run = github("$api/runs/$runId")
                return run.status == 'completed'
            }
        }
        if (run.conclusion != 'success') {
            error "GitHub workflow run finished with '${run.conclusion}': ${run.html_url}"
        }

        sh "mkdir -p '$dir'"
        for (artifact in github("$api/runs/$runId/artifacts?per_page=100").artifacts) {
            sh """
            curl -fsSL ${headers()} -o github-artifact.zip ${artifact.archive_download_url}
            unzip -o -q github-artifact.zip -d '$dir' && rm github-artifact.zip
            """
        }
    }
}

// GITHUB_TOKEN is expanded by the shell, never by Groovy
private String headers() {
    '-H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json"'
}

private def github(String url) {
    readJSON text: sh(returnStdout: true, script: "curl -fsS ${headers()} '$url'")
}
