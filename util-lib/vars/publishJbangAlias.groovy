// Points an alias of the flowlogix/jbang-catalog JBang catalog at a jar and pushes the change, so that
// `jbang <alias>@flowlogix` runs that jar. Other aliases in the catalog are kept.

void call(String alias, String jarUrl, String description, String java) {
    githubAppCredentials 'GITHUB_TOKEN', {
        dir('jbang-catalog') {
            deleteDir()
            withEnv(["JBANG_ALIAS=$alias", "JAR_URL=$jarUrl"]) {
                // git reads the token through a credential helper, never from its command line or .git/config
                String git = '''git -c credential.helper='!f() { echo username=x-access-token; echo "password=$GITHUB_TOKEN"; }; f' '''
                sh "$git clone -q --depth 1 https://github.com/flowlogix/jbang-catalog.git ."

                Map catalog = fileExists('jbang-catalog.json') ? readJSON(file: 'jbang-catalog.json', returnPojo: true) : [:]
                if (!catalog.aliases) {
                    catalog.aliases = [:]
                }
                catalog.aliases[alias] = ['script-ref': jarUrl, description: description, java: java]
                writeJSON file: 'jbang-catalog.json', json: catalog, pretty: 2

                sh """
                git add jbang-catalog.json
                git diff --cached --quiet || git -c user.name=flowlogix-bot -c user.email=noreply@flowlogix.com \
                    commit -q -m "\$JBANG_ALIAS: \$JAR_URL"
                $git push -q origin HEAD
                """
            }
        }
    }
}
