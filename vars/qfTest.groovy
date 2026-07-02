def call(Map config=[:]){
    try {
        bat """
            qftestc.exe -batch -allowkilling -engine swt,awt ^
            -runlog ${env.WORKSPACE}/_qftestRunLogs/${config.app}/qrz/${config.reportTitle}.qzp ^
            -report.html ${env.WORKSPACE}/_qftestRunLogs/${config.app}/html ^
            -report.junit ${env.WORKSPACE}/_qftestRunLogs/${config.app}/junit ^
            -report.xml ${env.WORKSPACE}/_qftestRunLogs/${config.app}/xml ^
            -report-thumbnails -report-scale-thumbnails 16 ^
            ${config.vars} ^
            -test \"${config.currentTest}\" ^
            -exitcode-ignore-exception -run ${config.currentSuite}
        """.replaceAll(/ {2,}/,' ')
    } finally {
        if(config.publishHtml){
            publishHTML([
                allowMissing          : false, 
                alwaysLinkToLastBuild : false, 
                keepAll               : true, 
                reportDir             : "_qftestRunLogs/${config.app}/html",
                reportFiles           : 'report.html', 
                reportName            : "${config.app == 'RO' ? 'RailOpt' : config.app} Report", 
                reportTitles          : "RailOpt ${config.reportTitle}", 
                useWrapperFileDirectly: true
            ])
        }
    }

}