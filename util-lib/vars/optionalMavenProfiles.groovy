// Convert to maven 4 optional profile syntax
def call(String profiles) {
    '?' + profiles.replaceAll(',', ',?')
}
