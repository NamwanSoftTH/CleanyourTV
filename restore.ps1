# Undo the debloat: re-enable every package in disabled-packages.txt, give Home back to
# Google TV and (unless -KeepLauncher) uninstall the custom launcher.
# Private DNS (AdGuard) was already set before this project and is left as it is.
param([string]$Serial = $(if ($env:TV_SERIAL) { $env:TV_SERIAL } else { '192.168.2.5:5555' }),
      [switch]$KeepLauncher)
$adb = 'D:\AndroidSDK\platform-tools\adb.exe'
$pkgs = Get-Content "$PSScriptRoot\disabled-packages.txt" | Where-Object { $_ -and $_ -notmatch '^\s*#' } | ForEach-Object { $_.Trim() }
foreach ($p in $pkgs) { Write-Host "$p -> $(& $adb -s $Serial shell pm enable --user 0 $p)" }
# Remote-button helper off and location off again, as they were before (backup\settings-secure.txt).
& $adb -s $Serial shell settings put secure enabled_accessibility_services '""'
& $adb -s $Serial shell settings put secure accessibility_enabled 0
& $adb -s $Serial shell cmd location set-location-enabled false
& $adb -s $Serial shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
if (-not $KeepLauncher) { & $adb -s $Serial shell pm uninstall com.launcher.clean }
Write-Host 'Done. Reboot the box: adb reboot'
