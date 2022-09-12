// call the warnings-ng plugin

def call(String log_pattern) {
    recordIssues enabledForFailure: true, aggregatingResults: true, tool: java(),
        filters: [ excludeFile('.*/generated-sources/.*'), excludeMessage('cannot find symbol') ],
        qualityGates: [[ threshold: 1, type: 'TOTAL', unstable: true ]]

    configParser()
    recordIssues enabledForFailure: true, aggregatingResults: true,
    tool: groovyScript(parserId: 'payara-logs', pattern: log_pattern),
        filters: [ excludeMessage(/Local Exception Stack:[\s\S]*Exception \[EclipseLink-4002\][\s\S]*: / +
        /org.eclipse.persistence.exceptions.DatabaseException[\s\S]*Internal Exception: java.sql.SQLException: / + 
        /java.lang.reflect.UndeclaredThrowableException[\s\S]*Error C[\s\S]*/),
        excludeMessage(/A system exception occurred during an invocation on EJB ProtectedStatelessBean, / + 
        /method: public java.lang.String com.flowlogix.examples.shiro.ProtectedStatelessBean.hello()/),
        excludeMessage(/javax.ejb.EJBException: Attempting to perform a user-only operation.[\s\S]*/ +
        /The current Subject is not a user \(they haven't been authenticated or remembered from a previous login\)[\s\S]*/),
        excludeMessage(/#\{exceptionBean.throwExceptionFromMethod\(\)}: java.sql.SQLException: sql-from-method[\s\S]*/ +
        /javax.faces.FacesException: #\{exceptionBean.throwExceptionFromMethod\(\)}:[\s\S]*/) ],
        qualityGates: [[ threshold: 1, type: 'TOTAL', unstable: true ]]
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
