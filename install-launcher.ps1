# Build S3 Home (debug APK) and install it on the box.
# Usage: .\install-launcher.ps1 [-Serial 192.168.2.5:5555] [-BuildOnly] [-SetHome]
#   -SetHome  also make it the default Home (first install / after restore.ps1)
param([string]$Serial = $(if ($env:TV_SERIAL) { $env:TV_SERIAL } else { '192.168.2.5:5555' }),
      [switch]$BuildOnly, [switch]$SetHome)
$ErrorActionPreference = 'Stop'
$adb = 'D:\AndroidSDK\platform-tools\adb.exe'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'   # JDK bundled with Android Studio (Java 25)
# Gradle wrapper (launcher\gradlew.bat): fetches Gradle 8.14.3 into ~\.gradle on first use.
$gradle = "$PSScriptRoot\launcher\gradlew.bat"

& $gradle -q -p "$PSScriptRoot\launcher" assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
$apk = "$PSScriptRoot\launcher\app\build\outputs\apk\debug\app-debug.apk"
Write-Host "Built $apk"
if ($BuildOnly) { return }

# adb install can hang on Android 14 waiting for an on-screen confirmation: push + pm install instead.
& $adb -s $Serial push $apk /data/local/tmp/s3home.apk | Out-Null
& $adb -s $Serial shell pm install -r /data/local/tmp/s3home.apk
& $adb -s $Serial shell rm /data/local/tmp/s3home.apk
# Wi-Fi name in the pill needs location permission (Android 10+) and location switched on.
& $adb -s $Serial shell pm grant com.launcher.mi.box.s3 android.permission.ACCESS_FINE_LOCATION
# Quick settings > Remotes & accessories needs it (TV Settings checks the caller).
& $adb -s $Serial shell pm grant com.launcher.mi.box.s3 android.permission.BLUETOOTH_CONNECT
# Optional, NOT done here: with WRITE_SECURE_SETTINGS the Remotes & accessories row opens the
# device list directly (TV Settings' slice page requires it); without it, main Settings. See README.
if ($SetHome) {
    & $adb -s $Serial shell cmd package set-home-activity com.launcher.mi.box.s3/.HomeActivity
}
& $adb -s $Serial shell am start -a android.intent.action.MAIN -c android.intent.category.HOME | Out-Null
