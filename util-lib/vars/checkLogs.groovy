// call the warnings-ng plugin

def call(String log_pattern, boolean checkConsole = true, qualityThreshold = 1) {
    def maximalQualityGates = [[ threshold: qualityThreshold, type: 'TOTAL', unstable: true ]]
    def checkTools = [java()]

    if (checkConsole) {
        checkTools << mavenConsole()
    }
    recordIssues enabledForFailure: true, aggregatingResults: true, tools: checkTools,
        filters: [ excludeFile('.*/generated-sources/.*'),
            excludeMessage('Unsupported element'),
            excludeMessage(/Can.+t extract module name from .*pom:.*/),
            excludeMessage(/The requested profile .* could not be activated because it does not exist\./),
            excludeMessage(/JAR will be empty - no content was marked for inclusion!/),
            excludeMessage('No profiles detected!'), excludeMessage('Javadoc Warnings') ],
        name: 'Java Compiler', qualityGates: maximalQualityGates

    def jacocoExecFiles = findFiles glob: '**/jacoco*.exec'
    if (jacocoExecFiles.length > 0) {
        jacoco execPattern: '**/target/jacoco*.exec', classPattern: '**/target/classes-jacoco',
            sourcePattern: '**/src/main/java', exclusionPattern: '**/src/test/java',
            changeBuildStatus: true,
            minimumLineCoverage: '60', maximumLineCoverage: '70',
            minimumInstructionCoverage: '60', maximumInstructionCoverage: '70'
    }

    if (log_pattern) {
        configParser()
        recordIssues enabledForFailure: true, aggregatingResults: true,
        tool: groovyScript(parserId: 'payara-logs', pattern: log_pattern),
            qualityGates: maximalQualityGates,
            filters: [ excludeMessage(/Local Exception Stack:[\s\S]*Exception \[EclipseLink-4002\][\s\S]*: / +
            /org.eclipse.persistence.exceptions.DatabaseException[\s\S]*Internal Exception: java.sql.SQLException: / + 
            /java.lang.reflect.Undeclared[\s\S]*/),
            excludeMessage(/A system exception occurred during an invocation on EJB ProtectedStatelessBean, / + 
            /method: public java.lang.String .*ProtectedStatelessBean.hello()/),
            excludeMessage(/(javax|jakarta).ejb.EJBException: Attempting to perform a user-only operation.[\s\S]*/ +
            /The current Subject is not a user \(they haven't been authenticated or remembered from a previous login\)[\s\S]*/),
            excludeMessage(/#\{exceptionBean.throwExceptionFromMethod\(\)}: .*java.sql.SQLException: sql-from-method[\s\S]*/ +
            /(javax|jakarta).faces.FacesException: #\{exceptionBean.throwExceptionFromMethod\(\)}:[\s\S]*/),
            excludeMessage(/java.io.IOException: Connection is closed/),
            excludeMessage(/JSF1064: Unable to find or serve resource.*/),
            excludeMessage(/SLF4J\(I\): Connected with provider of type.*/),
            excludeMessage(/The web application.*created a ThreadLocal.*value.*org.testng.internal.TestResult.*TestR.*/),
            excludeMessage(/Setting .* is unknown and will be ignored/),
            excludeMessage(/Unprocessed event : UnprocessedChangeEvent.*/) ]
    }
}

void configParser() {
    def config = io.jenkins.plugins.analysis.warnings.groovy.ParserConfiguration.getInstance()
    def payaraParser = new io.jenkins.plugins.analysis.warnings.groovy.GroovyParser(
        'payara-logs',
        'Payara Log Parser',
       getRegex(),
       // line number is not available (currently) in multi-line parsers
        'return builder.setFileName(fileName).setLineStart(lineNumber)' +
        '.setCategory(matcher.group(3)).setMessage(matcher.group(9).take(256)).buildOptional()',
        "[2022-09-11T16:53:01.405-0500] [Payara 5.2022.3] [ERROR]"
        )
    def parsers = config.getParsers().findAll { it.getId() != 'payara-logs' }
    config.setParsers(parsers.plus(payaraParser))
}

String getRegex() {
    String bracketsPattern = /\[((?:\[??[^\[]*?))\]/
    String warningPattern = /\[(WARNING|ERROR|FATAL|SEVERE)\]/
    String spacePattern = /\s+/
    String bracketsPlusSpace = bracketsPattern + spacePattern
    String twoBracketsPatterns = bracketsPlusSpace * 2

    String minusMessage = twoBracketsPatterns + warningPattern + spacePattern + bracketsPlusSpace * 5
    return minusMessage + /\[\[\n\s+([\s\S]*?)(\]\])/
}
