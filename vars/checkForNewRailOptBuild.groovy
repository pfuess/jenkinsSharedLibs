// vars/checkForNewRailOptBuild.groovy
def call(Map config) {
    def checker = config.checker
    def helper = config.helper
    
    echo '*** prüfen ob RailOpt build aktuell ist ***'
    int prevBuildNr=helper.getPrevRailOptBuildNr(env.JOB_NAME,params.Umgebung)
    int currBuildNr=checker.getRailOptVersionInfo(env.RailOptPath)['build'].toInteger()
    echo "prevBuildNr=$prevBuildNr currBuildNr=${currBuildNr}"
    if(currBuildNr==prevBuildNr)
        currentBuild.description="KEIN NEUER RAILOPTBUILD\n\r " + currentBuild.description
}
