// Writes a JReleaser config from the jreleaser/<template>.yaml library resource,
// replacing each @key@ in it with the job's value for key

void call(String template, Map values, String file = 'jreleaser.yaml') {
    String text = libraryResource("jreleaser/${template}.yaml")
    values.each { key, value ->
        // every placeholder sits in a double-quoted YAML string
        text = text.replace("@${key}@", value.toString().replace('\\', '\\\\').replace('"', '\\"'))
    }
    writeFile file: file, text: text
}
