// Creates Payara Global Variables

def call() {
    env.payara_base = "$WORKSPACE/target/dependency/payara5"
    env.asadmin = "$payara_base/bin/asadmin"
    env.portbase = 4900 + (env.EXECUTOR_NUMBER as int) * 100
}
