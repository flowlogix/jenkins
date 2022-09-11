// call the warnings-ng plugin

def call() {
    recordIssues enabledForFailure: true, aggregatingResults: true, tool: java(),
        filters: [ excludeFile('.*/generated-sources/.*'), excludeMessage('cannot find symbol') ],
        qualityGates: [[ threshold: 1, type: 'TOTAL', unstable: true ]]
}
