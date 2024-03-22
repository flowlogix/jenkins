// Convert to maven 4 optional profile syntax
def call(int mavenVersion, String profiles) {
    if (mavenVersion < 4) {
        return profiles
    }
    (profiles.startsWith(',') ? '' : '?') + profiles.replaceAll(',', ',?')
}
