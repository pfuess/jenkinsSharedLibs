def call(Map config=[:]){
    stage('Preparation'){
        cleanWs()
        println "gitBranch=${params.gitBranch}"
        git branch: params.gitBranch,
            credentialsId: 'github-token',
            url: "https://github.com/qnamic/test-automation.git"
            
        currentBuild.displayName=params.Umgebung
        //JettyHttpPort=helpers.getRailOptJettyPort(RailOptPath)
        String jobDesc = currentBuild.rawBuild.project.description ?: ""
        expectedVersions=versionChecker.getVersionsMapFromJenkinsDescription(jobDesc)
        println "erwartete Versionen: $expectedVersions"
    }
}