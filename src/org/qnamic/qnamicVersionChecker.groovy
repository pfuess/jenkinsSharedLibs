package org.qnamic

class qnamicVersionChecker implements Serializable {
    private final def steps
    
    qnamicVersionChecker(def steps){
        this.steps=steps
    }

    void checkVersions() {
        String jobDesc = steps.currentBuild.rawBuild.project.description ?: ""
        def expectedVersions=getVersionsMapFromJenkinsDescription(jobDesc)
        
        def installedVersions=[
            'RailOpt-Java': getRailOptVersionInfo(steps.env.RailOptPath)['javaVersion'],
            'Tomcat': getInstalledTomcatVersion(steps.env.Umgebung),
            'Tomcat-Java': getInstalledTomcatJavaVersion(steps.env.Umgebung),
            'Keycloak': getInstalledKeycloakVersion(steps.env.Umgebung),
            'Keycloak-Java': getInstalledKeycloakJavaVersion(steps.env.Umgebung),
            'DIS-Java': getInstalledDisJavaVersion(steps.env.Umgebung)
        ]
        
        expectedVersions.any { key, value ->
            println "Vergleiche ${key}: erwartet=${value} mit installiert=${installedVersions[key]}"
            if (installedVersions[key] != value) {
                String errMsg="erwartete ${key} Version nicht vorhanden\nerwartet: ${value}\ninstalliert:${installedVersions[key]}"
                steps.currentBuild.description=errMsg
                steps.error(errMsg)
            }
         }
    }

    /***
    öffnet version.txt im RailOpt Verzeichnis und liest dort versions informationen aus
    und gibt diese als map zurück
    */
    def getRailOptVersionInfo(String railOptDir) {
        def fileContent = steps.readFile("${railOptDir}/version.txt")
        
        def bNr = fileContent =~ /(?<=build\.number=)\d+/
        def jv = fileContent =~ /(?<=jre\.version=)[\d.]+/
        def branch = fileContent =~ /(?<=scmBranch=)[\w.\/]+/
        
        // Kleiner Check, ob die RegEx Treffer gefunden haben, um Fehler zu vermeiden
        def ret = [
            'build': bNr.find() ? bNr[0] : 'unknown',
            'javaVersion': jv.find() ? jv[0] : 'unknown',
            'railOptBranch': branch.find() ? branch[0].replaceAll(/build|branches|\//, '') : 'unknown'
        ]
        return ret
    }

    /* getInstalledTomcatVersion
    liest die installierte tomcat version aus der RELEASE-NOTES datei aus
    */
    String getInstalledTomcatVersion(String environement){
        try {
            def fileContent = steps.readFile("C:/RailOpt_${environement}_Web/RELEASE-NOTES")
            def m = fileContent =~ /Apache Tomcat Version ([\d.]+)/
            m[0][1]
        }catch(exception){
            println exception.message
        }
    }

    String getInstalledTomcatJavaVersion(String environement){
        try{
            String tomcatJava=steps.bat(script: "C:/RailOpt_${environement}_Web/jre/bin/java.exe -version 2>&1", returnStdout: true)
            extractJavaVersion(tomcatJava)
        }catch(exception){
            println exception.message
        }
    }

    /* getInstalledKeycloakVersion
    liest die installierte keycloak version aus der version.txt datei aus
    */
    String getInstalledKeycloakVersion(String environement){
        try{
            String fileContent=steps.readFile("C:/RailOpt_${environement}_Keycloak/version.txt")
            def m = fileContent =~ /Version ([\d.]+)/
            m[0][1]
        }catch(exception){
            println exception.message
        }
    }

    String getInstalledKeycloakJavaVersion(String environement){
        try{
            String keycloakJava=steps.bat(script: "C:/RailOpt_${environement}_Keycloak/jre/bin/java.exe -version 2>&1", returnStdout: true)
            extractJavaVersion(keycloakJava)
        }catch(exception){
            println exception.message
        }
    }

    String getInstalledDisJavaVersion(String environement){
        try {
            String disJavaVersion=steps.bat(script: "C:/RailOpt_${environement}_DIS/jre/bin/java.exe -version 2>&1", returnStdout: true)
            extractJavaVersion(disJavaVersion)
        }catch(exception){
            println exception.message
        }
    }

    
    String extractJavaVersion(String text){        
        def m = text =~ /build\s*([\d.+]+)/
        if(m)
            m[0][1].replace('+','.')
        else
            "unknown"
    }

    /***getVersionsMapFromJenkinsDescription
    liesst die erwarteten Versionen in der Titelbeschreibung des jenkinsjobs aus
    und gibt die einzelnen Versionen als map zurück.
    falls in der Titelbeschreibung keine Versionen drin stehen gibt es null zurück
    */
    def getVersionsMapFromJenkinsDescription(String jobDescription){
        if (!jobDescription) return [:]
    
        def m = jobDescription =~ /([\w-]+):[\s|]*([\d.]+)/
        def result = [:]
        
        // Klassische Schleife ist innerhalb von Jenkins-Pipelines am sichersten
        while (m.find()) {
            result[m.group(1)] = m.group(2)
        }
        return result
    }
} 