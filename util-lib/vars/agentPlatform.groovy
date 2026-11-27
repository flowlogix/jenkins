// returns the agent's platform as <os>-<arch>, e.g. osx-aarch64 or linux-x86_64

String call() {
    def os = [Darwin: 'osx', Linux: 'linux'][sh(returnStdout: true, script: 'uname -s').trim()]
    def arch = [arm64: 'aarch64', aarch64: 'aarch64', x86_64: 'x86_64'][sh(returnStdout: true, script: 'uname -m').trim()]
    "$os-$arch"
}
