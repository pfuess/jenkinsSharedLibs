import java.nio.file.*
import java.util.zip.*
import groovy.xml.*
import static groovy.io.FileType.*

/*** getSummary()
oeffnet das xmlreport file und liesst dort die
entsprechenden Testergebnisse heraus und berechnet die Erfolgsquote
@param xmlContent der Inhalt der report.xml Datei als String
@return Map mit den ausgelesenen Informationen 
*/
@NonCPS
def getSummary(String xmlContent) {
     def content = new XmlSlurper().parseText(xmlContent)
     
     //benoetigte Werte aus xml einlesen
     int tests=content.summary.'@executedtests'.toInteger()
     int passed=content.summary.'@passedtests'.toInteger()
     int duration=content.summary.'@realtime'.toInteger() 

     def summary=[:]
     //Erfolgsquote
     summary['success']= ((passed/tests)*1000).toInteger()/10
     //Laufzeit
     int sum_s=duration/1000
     String m=(sum_s/60).toInteger().toString()
     String s=(sum_s%60).toString().padLeft(2,'0')
     summary['duration']="$m:$s min"
     
     summary
}

/***
öffnet version.txt im RailOpt Verzeichnis und liest dort versions informationen aus
und gibt diese als map zurück
*/
def getRailOptVersionInfo(String railOptDir) {
    def versionFile=new File("$railOptDir\\version.txt")
    
    def bNr=versionFile.text =~ /(?<=build\.number=)\d+/
    def jv=versionFile.text =~ /(?<=jre\.version=)[\d.]+/
    def branch=versionFile.text =~ /(?<=scmBranch=)[\w.\/]+/
    def ret=['build':bNr[0],
             'javaVersion':jv[0],
             'railOptBranch':branch[0].replaceAll(/build|branches|\//,'')]
    ret
}

String getInstalledTomcatVersion(String environement){
    try {
        def releasenotes=new File("C:/RailOpt_${environement}_Web/RELEASE-NOTES")
        def m = releasenotes.text =~ /Apache Tomcat Version ([\d.]+)/
        m[0][1]
    }catch(exception){
        println exception.message
    }
}

String getInstalledTomcatJavaVersion(Object steps, String environement){
    try{
        String tomcatJava=steps.bat(script: "C:/RailOpt_${environement}_Web/jre/bin/java.exe -version 2>&1", returnStdout: true)
        extractJavaVersion(tomcatJava)
    }catch(exception){
        println exception.message
    }
}

String getInstalledKeycloakVersion(String environement){
    try{
        def version=new File("C:/RailOpt_${environement}_Keycloak/version.txt")
        def m = version.text =~ /Version ([\d.]+)/
        m[0][1]
    }catch(exception){
        println exception.message
    }
}

String getInstalledKeycloakJavaVersion(Object steps, String environement){
    try{
        String keycloakJava=steps.bat(script: "C:/RailOpt_${environement}_Keycloak/jre/bin/java.exe -version 2>&1", returnStdout: true)
        extractJavaVersion(keycloakJava)
    }catch(exception){
        println exception.message
    }
}

String getInstalledDisJavaVersion(Object steps, String environement){
    try {
        String disJavaVersion=steps.bat(script: "C:/RailOpt_${environement}_DIS/jre/bin/java.exe -version 2>&1", returnStdout: true)
        extractJavaVersion(disJavaVersion)
    }catch(exception){
        println exception.message
    }
}

@NonCPS
String extractJavaVersion(String text){        
    def m = text =~ /build\s*([\d.+]+)/
    m[0][1].replace('+','.')
}

/***getVersionsMapFromJenkinsDescription
liesst die erwarteten Versionen in der Titelbeschreibung des jenkinsjobs aus
und gibt die einzelnen Versionen als map zurück.
falls in der Titelbeschreibung keine Versionen drin stehen gibt es null zurück
*/
@NonCPS
def getVersionsMapFromJenkinsDescription(){
    def job=Jenkins.instance.getItemByFullName(env.JOB_NAME)
    String s=job.getDescription()
    def m = s =~ /([\w-]+):[\s|]*([\d.]+)/
    m.collect {  mtch, g1, g2 -> [g1,g2] }.collectEntries()
}

/*** getPrevRailOptBuildNr
@param jobName voller Name des jenkins jobs (z.B. QFTest_RailOpt)
@param RailOptEnv RailOpt Umgebung -> (PGTRUNK,PRBRANCH,ORATRUNK,ORABRANCH)
@return int Buildnr. des gestrigen Builds, oder null wenn nichts gefunden wurde
*/
int getPrevRailOptBuildNr(String jobName, String RailOptEnv){
  String yesterday=(new Date() - 1).format('dd.MM.yyyy')
  def job = Jenkins.instance.getAllItems().find { it.fullName==jobName }
  if(job==null)
  	return null
  
  String envJustBranch=(RailOptEnv.toLowerCase()=~/(trunk|branch)/)[0][1]
  def curBuildName
  def prevBuild=job.builds.find {
    curBuildName=it.displayName.toLowerCase()=~/(trunk|branch)/
    if(curBuildName.size()>0)
        curBuildName[0][1] == envJustBranch && it.timestamp.format('dd.MM.yyyy') == yesterday
  }
  if(prevBuild==null)
  	return null
  def m = prevBuild.description =~ /build[=:](\d+)/
  m.size()>0?m[0][1].toInteger():null
}

/** getRailOptJettyPort
liesst die Portnummer des Webservices aus dem serverx.prop files, welcher
Soap Stammdaten importiert 
@param railOptDir Der Pfad zum Installationsverzeichnis
@return portnummer
*/
def getRailOptJettyPort(String railOptDir) {
    def versionFile=new File("$railOptDir/install/serverx.prop")
    
    def jettyHttpPort=versionFile.text =~ /(?<=jetty\.http\.port=)\d+/
    jettyHttpPort[0]
}


/***erstellt rekursiv ein zip-file von allen Dateien in srcDir 
*/
@NonCPS
void compress(File srcDir, File zipFile) {
    def output = new ZipOutputStream(new FileOutputStream(zipFile))
    output.setLevel(Deflater.BEST_COMPRESSION) //Use what you need

    srcDir.eachFileRecurse(groovy.io.FileType.FILES) {
        def name = (it.path - srcDir).substring(1)
        output.putNextEntry(new ZipEntry(name))
        output.write(it.bytes)
        output.closeEntry()
    }

    output.close()
}

@NonCPS
void copyExportedDBsToArtefactsDirectory(File artefactsDir) {
    String path2DBDumpFiles="${env.WORKSPACE}/RailOpt/DBExport"

    def DBDumpFiles=[]

    new File(path2DBDumpFiles).eachFileMatch(FILES,~/.+.dump/) {
        DBDumpFiles.add(it)
    }

    println 'railOpt_dump_files:'
    DBDumpFiles.each {
        if(it.exists()) {
            println it.name
            Files.copy(it.toPath(),
                       artefactsDir.toPath().resolve(it.name), 
                       StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

@NonCPS
void copyRailOptLogsToArtefactsDirectory(File artefactsDir) {
    String qTaskHome=System.properties['user.home']
    String railOptLogsPath="${RailOptPath}/logs"
    println "qTaskHome: $qTaskHome"
    println "railOptLogsPath: $railOptLogsPath"
    def railOpt_logs=[]

    new File("${qTaskHome}/RailOpt_${params.Umgebung}-Data").eachFileMatch(FILES,~/.+\D\.log/) {
        railOpt_logs.add(it)
    }
    
    new File(railOptLogsPath).eachFileMatch(FILES,~/.+\D\.log/) {
        railOpt_logs.add(it)
    }

    //qnamic logs aus RailOpt und userhome Verzeichnis nach workspace kopieren
    println 'railOpt_logs:'
    railOpt_logs.each {
        if(it.exists()) {
            println it.name
            Files.copy(it.toPath(),
                       artefactsDir.toPath().resolve(it.name), 
                       StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

boolean checkIfRailOptCorrectlyInstalled(String RailOptPath){
    def railOptDir=new File(RailOptPath)
    if(railOptDir.exists()==false ||
       railOptDir.list().size()<20 ||    
       railOptDir.list().find { it=='version.txt' }==null ){
        currentBuild.description="RailOpt nicht, oder nicht vollständig installiert -> Testlauf wird abgebrochen"
        helpers.sendEmail("RailOpt QFTestlauf ${params.Umgebung}","${currentBuild.description}\n\rJenkins-Link: ${BUILD_URL}")
        currentBuild.result = 'ABORTED'
        return false
    }else{
        println 'RailOpt scheint korrekt installiert zu sein'
        return true
    }
}

@NonCPS
void copyDisLogsToArtefactsDirectory(File artefactsDir) {
    String disLogsPath="${disServerPath}/server/logs"
    println "disLogsPath: $disLogsPath"
    def dis_logs=[]

    new File(disLogsPath).eachFileMatch(FILES,~/.+\D\.log/) {
        dis_logs.add(it)
    }

    println 'dis_logs:'
    dis_logs.each {
        if(it.exists()) {
            println it.name
            Files.copy(it.toPath(),
                       artefactsDir.toPath().resolve(it.name), 
                       StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

@NonCPS
void copyAccumulatorsToArtefactsDirectory(File artefactsDir) {
    String path2AccuFiles="${env.WORKSPACE}/RailOpt/Testdaten"
    println "path2AccuFiles: $path2AccuFiles"
    def accuFiles=[]

    new File(path2AccuFiles).eachFileMatch(FILES,~/.*Akku.*\.json/) {
        accuFiles.add(it)
    }

    println 'accuFiles:'
    accuFiles.each {
        if(it.exists()) {
            println it.name
            Files.copy(it.toPath(),
                       artefactsDir.toPath().resolve(it.name), 
                       StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

void sendEmail(String subject, String msg) {
    if(params.emailEmpfaenger!=''){
        if(params.executeQFTest) {
            emailext(attachLog: true, 
                    body: msg, 
                    subject: subject,
                    to: params.emailEmpfaenger)
        } else {
            emailext(attachLog: true,
                    body: 'QFTest Lauf wurde nicht ausgeführt -> Debugmode',
                    subject: "QFTest-Lauf im Jenkinsjob simuliert",
                    to: 'peter.fuess@qnamic.com')
        }
    }
}


return this

