// Jenkins has a bug that triggers multiple builds via a single push randomly
// this code aborts the duplicate build

import groovy.transform.Field

@Field
def guardDupBuildsParamsDefault =
    [neverRan : true, context : 'CI/unit-tests/pr-merge-duplicate',
    description : 'Please ignore temporary failure - another build started']

def call(def parameters, Closure cl) {
    parameters << guardDupBuildsParamsDefault + parameters
    if (!parameters.resourceName) {
        parameters.resourceName = "${env.GIT_COMMIT}_$env.JOB_NAME"
    }
    if (parameters.neverRan || parameters.lockSuccess) {
        parameters.neverRan = false
        lock(resource: parameters.resourceName, skipIfLocked: true) {
            cl()
            parameters.lockSuccess = true
        }
    }
    if (!parameters.lockSuccess) {
        currentBuild.description = 'Duplicate Build'
        githubNotify description: parameters.description,
        context: parameters.context, status: 'SUCCESS'
        currentBuild.result = 'not_built'
        error 'Duplicate job - not built'
    }
}
