// call the warnings-ng plugin

def call() {
    recordIssues enabledForFailure: true, aggregatingResults: true, tool: java()
}
