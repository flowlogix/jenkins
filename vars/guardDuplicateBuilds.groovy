import groovy.transform.Field

@Field
def guardDupBuildsParamsDefault = 
    [neverRan : true, context : 'CI/unit-tests/pr-merge-alternate', 
    description : 'Please ignore failure - Another build started']

def call(def parameters, Closure cl) {
    parameters << parameters.withDefault guardDupBuildsParamsDefault.&get
    echo "parameters: $parameters"
    if (!parameters.resourceName) {
        parameters.resourceName = "${env.GIT_COMMIT}_$env.JOB_NAME"
    }
    if (guardDupBuildsParams.neverRan || guardDupBuildsParams.lockSuccess) {
        guardDupBuildsParams.neverRan = false
        lock(resource: guardDupBuildsParams.resourceName, skipIfLocked: true) {
            cl()
            guardDupBuildsParams.lockSuccess = true
        }
    }
    if (!guardDupBuildsParams.lockSuccess) {
        githubNotify description: parameters.description,
        context: parameters.context, status: 'SUCCESS'
        currentBuild.result = 'not_built'
        error 'Duplicate job - not built'
    }
}
