package org.qnamic.qnamicHelper

import java.nio.file.*
import java.util.zip.*
import groovy.xml.*
import static groovy.io.FileType.*

class qnamicHelper implements Serializable{
    private final def steps

    qnamicHelper(def steps){
        this.steps=steps
    }

    void createRunlogDirs(String app) {
        // 'mkdir' in Windows erstellt automatisch Zwischenverzeichnisse, 
        // wenn man den gesamten Pfad angibt.
        steps.bat """
            if not exist "_qftestRunLogs\\${app}\\qrz" mkdir "_qftestRunLogs\\${app}\\qrz"
            if not exist "_qftestRunLogs\\${app}\\html" mkdir "_qftestRunLogs\\${app}\\html"
            if not exist "_qftestRunLogs\\${app}\\junit" mkdir "_qftestRunLogs\\${app}\\junit"
            if not exist "_qftestRunLogs\\${app}\\xml" mkdir "_qftestRunLogs\\${app}\\xml"
        """
    }

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
        def fileContent = steps.readFile("${railOptDir}/install/serverx.prop")
        
        def jettyHttpPort=fileContent =~ /(?<=jetty\.http\.port=)\d+/
        jettyHttpPort[0]
    }

    @NonCPS
    void copyExportedDBsToArtefactsDirectory(File artefactsDir) {
        String path2DBDumpFiles="${steps.env.WORKSPACE}/RailOpt/DBExport"

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
    void copyRailOptLogsToArtefactsDirectory(File artefactsDir, String RailOptPath) {
        String qTaskHome=System.properties['user.home']
        String railOptLogsPath="${RailOptPath}/logs"
        println "qTaskHome: $qTaskHome"
        println "railOptLogsPath: $railOptLogsPath"
        def railOpt_logs=[]

        new File("${qTaskHome}/RailOpt_${steps.params.Umgebung}-Data").eachFileMatch(FILES,~/.+\D\.log/) {
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

    void checkIfRailOptCorrectlyInstalled(String railOptPath) {
        // 1. Existenz des Verzeichnisses und der version.txt prüfen
        boolean dirExists = steps.fileExists(railOptPath)
        boolean versionFileExists = steps.fileExists("${railOptPath}/version.txt")

        
        if (!dirExists || !versionFileExists) {
            String errorMsg = "RailOpt nicht, oder nicht vollständig installiert (Pfad: ${railOptPath}, Files: ${fileCount}) -> Testlauf wird abgebrochen"
            
            steps.currentBuild.description = errorMsg
            // Beachte: 'helpers' muss hier im Kontext der Library bekannt sein oder übergeben werden
            sendEmail("RailOpt QFTestlauf ${steps.params.Umgebung}", "${errorMsg}\n\rJenkins-Link: ${steps.env.BUILD_URL}")
            
            steps.error(errorMsg) // 'error' bricht den Build sofort mit einer Fehlermeldung ab
        } else {
            steps.echo 'RailOpt scheint korrekt installiert zu sein'
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
        String path2AccuFiles="${steps.env.WORKSPACE}/RailOpt/Testdaten"
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
        if(steps.params.emailEmpfaenger!=''){
            steps.emailext(attachLog: true, 
                           body: msg, 
                           subject: subject,
                           to: steps.params.emailEmpfaenger)
            // } else {
            //     steps.emailext(attachLog: true,
            //             body: 'QFTest Lauf wurde nicht ausgeführt -> Debugmode',
            //             subject: "QFTest-Lauf im Jenkinsjob simuliert",
            //             to: 'peter.fuess@qnamic.com')
            // }
        }
    }
}

