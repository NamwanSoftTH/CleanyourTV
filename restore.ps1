# Undo the debloat: re-enable every package in the connected model's debloat\<model>.txt (batches and
# takeover), give Home back to Google TV and (unless -KeepLauncher) uninstall the custom launcher.
# Private DNS (AdGuard) was already set before this project and is left as it is.
#   -List debloat\Other.txt   use another list (e.g. the model name changed after an update)
param([string]$Serial = $(if ($env:TV_SERIAL) { $env:TV_SERIAL } else { '192.168.2.5:5555' }),
      [string]$List,
      [switch]$KeepLauncher)
$adb = 'D:\AndroidSDK\platform-tools\adb.exe'
. "$PSScriptRoot\tools\DebloatList.ps1"
if (-not $List) {
    $model = (& $adb -s $Serial shell getprop ro.product.model).Trim()
    $List = Get-DebloatListPath $model
}
if (Test-Path $List) {
    $l = Read-DebloatList $List
    $pkgs = @($l.Batches.Values | ForEach-Object { $_ }) + $l.Takeover
    Write-Host "Re-enabling from $List"
} else {
    # No list for this model: re-enable everything any list names (pm enable on a missing package is harmless).
    Write-Warning "No list $List; re-enabling every package named in debloat\*.txt"
    $pkgs = Get-ChildItem $DebloatDir -Filter *.txt | ForEach-Object {
        $l = Read-DebloatList $_.FullName; @($l.Batches.Values | ForEach-Object { $_ }) + $l.Takeover
    } | Sort-Object -Unique
}
# Google TV's stock Home and setup wizard are disabled by setup-new-box.ps1 even when a list leaves them out;
# also packages disabled by older versions of the lists (partnercustomizer: breaks the remote button setup).
$pkgs = @($pkgs) + $TakeoverPackages + 'com.xiaomi.android.tvsetup.partnercustomizer' | Select-Object -Unique
foreach ($p in $pkgs) { Write-Host "$p -> $(& $adb -s $Serial shell pm enable --user 0 $p)" }
# Remote-button helper off and location off again, as they were before
# (backup\MiTV-AFMU0-30468-20261025-0854\settings-secure.txt).
& $adb -s $Serial shell settings put secure enabled_accessibility_services '""'
& $adb -s $Serial shell settings put secure accessibility_enabled 0
& $adb -s $Serial shell cmd location set-location-enabled false
& $adb -s $Serial shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
if (-not $KeepLauncher) { & $adb -s $Serial shell pm uninstall com.launcher.clean }
Write-Host 'Done. Reboot the box: adb reboot'
