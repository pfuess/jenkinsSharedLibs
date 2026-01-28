def call(Map config=[:]){
    
    bat """
        qftestc.exe -batch -allowkilling -engine swt,awt ^
        -runlog ${env.WORKSPACE}/_qftestRunLogs/${config.app}/qrz/Regression.qzp ^
        -report.html ${env.WORKSPACE}/_qftestRunLogs/${config.app}/html ^
        -report.junit ${env.WORKSPACE}/_qftestRunLogs/${config.app}/junit ^
        -report.xml ${env.WORKSPACE}/_qftestRunLogs/${config.app}/xml ^
        -report-thumbnails -report-scale-thumbnails 16 ^
        ${config.vars} ^
        -test \"${config.currentTest}\" ^
        -exitcode-ignore-exception -run ${config.currentSuite}
    """
}