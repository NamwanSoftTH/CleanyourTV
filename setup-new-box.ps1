# Set up a new Android TV / Google TV box: backup, debloat (debloat\<model>.txt, if there is one for the
# connected model), S3 Home launcher, optional extras, Home takeover, checks.
# Safe to run again: finished steps are skipped or redone harmlessly.
#
# Usage:
#   First time (TV: Developer options > Wireless debugging > Pair device with pairing code):
#   .\setup-new-box.ps1 -Pair 192.168.x.x:<pairing port> -Code 123456
#       pairs, then finds the connection port by itself (mDNS) or asks for it; add -Serial ip:<port> to give it
#   Already paired:
#   .\setup-new-box.ps1 -Serial 192.168.x.x:<port>
#   .\setup-new-box.ps1 -Serial ... -PrefsFile backup\s3gen-home-prefs.xml   # copy an app order arranged on another box
#   .\setup-new-box.ps1 -Serial ... -SkipDebloat                             # launcher only
#   .\setup-new-box.ps1 -Serial ... -Yes                                     # don't ask for the optional extras (all on)
param([string]$Serial = $env:TV_SERIAL,
      [string]$Pair,      # ip:port shown under "Pair device with pairing code" (not the connection port)
      [string]$Code,      # the 6-digit pairing code; asked for when -Pair is given without it
      [string]$BackupDir,
      [string]$PrefsFile,
      [switch]$SkipDebloat,
      [switch]$Yes)
$ErrorActionPreference = 'Stop'
$adb = 'D:\AndroidSDK\platform-tools\adb.exe'
$pkg = 'com.launcher.clean'
# Google TV's stock Home and setup wizard: the takeover default when a model's list has no takeover section.
$defaultTakeover = @('com.google.android.apps.tv.launcherx', 'com.google.android.tungsten.setupwraith')
. "$PSScriptRoot\tools\DebloatList.ps1"

function Step($t) { Write-Host "`n=== $t" -ForegroundColor Cyan }
function Warn($t) { Write-Host "!! $t" -ForegroundColor Yellow }
function Sh { & $adb -s $Serial shell @args }
function Ask($q, [bool]$default) {
    if ($Yes) { return $default }
    $d = if ($default) { 'Y/n' } else { 'y/N' }
    $a = Read-Host "$q [$d]"
    if ([string]::IsNullOrWhiteSpace($a)) { return $default }
    return $a -match '^(y|yes|ใช่)$'
}

# ---------------------------------------------------------------- 0. connection
Step 'Connection'
if ($Pair) {
    if (-not $Code) { $Code = Read-Host "Pairing code shown on the TV for $Pair" }
    $out = (& $adb pair $Pair $Code 2>&1) -join ' '
    Write-Host $out
    if ($out -notmatch 'Successfully paired') { throw "Pairing with $Pair failed (code expired? the pairing port and code change each time you open the pairing screen)" }
    $hostIp = ($Pair -split ':')[0]
    if (-not $PSBoundParameters.ContainsKey('Serial')) {
        # The connection port differs from the pairing port; the TV announces it over mDNS.
        $Serial = $null
        for ($i = 0; $i -lt 5 -and -not $Serial; $i++) {
            $svc = & $adb mdns services 2>$null | Select-String '_adb-tls-connect' | Select-String ([regex]::Escape($hostIp))
            if ($svc) { $Serial = [regex]::Match($svc[0].Line, "$([regex]::Escape($hostIp)):\d+").Value }
            if (-not $Serial) { Start-Sleep 1 }
        }
        if ($Serial) { Write-Host "Connection port found: $Serial" }
        else { $Serial = "${hostIp}:$(Read-Host "Port under 'IP address & Port' on the Wireless debugging screen")" }
    }
}
if (-not $Serial) { throw 'No device: pass -Serial 192.168.x.x:<port> (or -Pair/-Code for the first time) or set $env:TV_SERIAL' }
$env:TV_SERIAL = $Serial
for ($i = 0; $i -lt 3; $i++) {
    & $adb connect $Serial | Out-Null
    if ((& $adb -s $Serial get-state 2>$null) -eq 'device') { break }
    Start-Sleep 2
}
if ((& $adb -s $Serial get-state 2>$null) -ne 'device') { throw "Cannot reach $Serial (wrong port? Wireless debugging off? First time: use -Pair and -Code)" }
$model = (Sh getprop ro.product.model).Trim()
$sdk = [int](Sh getprop ro.build.version.sdk).Trim()
Write-Host "Device: $model, SDK $sdk"
if ($sdk -lt 26) { throw "SDK ${sdk}: the launcher needs Android 8.0 (SDK 26) or newer" }
$listPath = Get-DebloatListPath $model
$list = if (Test-Path $listPath) { Read-DebloatList $listPath } else { $null }
if ($list) {
    Write-Host "Debloat list: debloat\$(Split-Path $listPath -Leaf)"
} else {
    Warn "No debloat list for '$model' (debloat\$(Split-Path $listPath -Leaf)). Package names differ per model,"
    Warn 'so nothing is disabled. For the first unit of a model: .\tools\new-debloat-list.ps1 writes a draft to review.'
    if (-not (Ask 'Continue with the launcher only?' $true)) { return }
    $SkipDebloat = $true
}
# Takeover: the list's own takeover packages plus Google TV's stock Home and setup wizard (skipped when not
# installed). setupwraith goes first: its RecoveryActivity grabs Home the moment launcherx is off.
$takeover = @('com.google.android.tungsten.setupwraith') + @(if ($list) { $list.Takeover }) + $defaultTakeover |
    Select-Object -Unique

# ---------------------------------------------------------------- 1. backup
Step 'Backup'
# backup\<model>-<last 5 of the serial number, to tell boxes of one model apart>-<date>
$serialNo = (Sh getprop ro.serialno).Trim()
$tail = if ($serialNo.Length -ge 5) { $serialNo.Substring($serialNo.Length - 5) } else { 'noserial' }
$stamp = (Get-Date).ToString('yyyyMMdd-HHmm', [Globalization.CultureInfo]::InvariantCulture)
if (-not $BackupDir) { $BackupDir = Join-Path 'backup' ("$model-$tail-$stamp" -replace '[^\w.-]', '_') }
$b = Join-Path $PSScriptRoot $BackupDir
New-Item -ItemType Directory -Force $b | Out-Null
Sh pm list packages -f > "$b\packages-all.txt"
Sh pm list packages -d > "$b\packages-disabled.txt"
Sh getprop > "$b\getprop.txt"
foreach ($n in 'global', 'secure', 'system') { Sh settings list $n > "$b\settings-$n.txt" }
Sh cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME > "$b\home-activities.txt"
Write-Host "Saved to $BackupDir"

# ---------------------------------------------------------------- 2. debloat
$installed = (Sh pm list packages) -replace '^package:', ''
if (-not $SkipDebloat -and $list) {
    Step "Debloat (debloat\$(Split-Path $listPath -Leaf), one batch at a time)"
    $batches = $list.Batches   # the takeover section is done in step 5
    foreach ($k in $batches.Keys) {
        $todo = @($batches[$k] | Where-Object { $installed -contains $_ })
        if (-not $todo) { Write-Host "$k : nothing installed"; continue }
        Write-Host "$k : $($todo -join ', ')"
        & "$PSScriptRoot\tools\disable.ps1" -Serial $Serial @todo
        $onScreen = Sh "dumpsys activity activities | grep mFocusedApp"
        Write-Host "  on screen now: $($onScreen.Trim())"
    }
}

# ---------------------------------------------------------------- 3. launcher
Step "Launcher ($pkg)"
& "$PSScriptRoot\install-launcher.ps1" -Serial $Serial -SetHome
if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { throw 'Install failed' }
if ($PrefsFile) {
    Write-Host "App order from $PrefsFile"
    Sh am force-stop $pkg
    & $adb -s $Serial push $PrefsFile /data/local/tmp/home.xml | Out-Null
    Sh "run-as $pkg mkdir -p shared_prefs && run-as $pkg cp /data/local/tmp/home.xml shared_prefs/home.xml && rm /data/local/tmp/home.xml"
}

# ---------------------------------------------------------------- 4. optional extras
Step 'Optional extras'
if (Ask 'Location on, so the Wi-Fi pill shows the network name?' $true) {
    Sh cmd location set-location-enabled true
}
if (Ask 'Remote-button helper (YouTube button, hold Home = quick settings)? Held buttons then stop repeating (volume one step per press).' $true) {
    Sh settings put secure enabled_accessibility_services "$pkg/$pkg.KeyService"
    Sh settings put secure accessibility_enabled 1
}
if (Ask 'Grant WRITE_SECURE_SETTINGS (lets the launcher change system settings; used only to open the Remotes & accessories device list)?' $false) {
    Sh pm grant $pkg android.permission.WRITE_SECURE_SETTINGS
}

# ---------------------------------------------------------------- 5. Home takeover
Step 'Home takeover'
$owners = @(Sh "dumpsys wifi | grep -o 'cname=[^ ]*' | sort -u")
Write-Host "Wi-Fi network belongs to: $($owners -join ', ')"
if ($owners -match 'setupwraith') {
    Warn 'The saved Wi-Fi network belongs to the setup wizard (setupwraith). Disabling it DELETES that network,'
    Warn 'which takes the box off Wi-Fi and ADB. On the TV: Settings > Network > forget the network, join it again,'
    Warn 'reconnect ADB (new port) and run this script again. Stopping before the takeover.'
    return
}
$todo = @($takeover | Where-Object { $installed -contains $_ })
if ($todo) { & "$PSScriptRoot\tools\disable.ps1" -Serial $Serial @todo }
Sh cmd package set-home-activity "$pkg/.HomeActivity" | Out-Null
Sh input keyevent KEYCODE_HOME
Start-Sleep 3
$focus = Sh "dumpsys activity activities | grep mFocusedApp"
if ($focus -notmatch [regex]::Escape("$pkg/.HomeActivity")) {
    Warn "Home is not the new launcher ($($focus.Trim())). Giving Home back to the stock launcher."
    foreach ($p in $todo) { Sh pm enable --user 0 $p | Out-Null }
    throw 'Takeover failed; see HOW_TO.md step 5'
}

# ---------------------------------------------------------------- 6. summary
Step 'Done'
Write-Host "Home:        $(Sh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | Select-Object -Last 1)"
Write-Host "Disabled:    $(@(Sh pm list packages -d).Count) packages"
Write-Host "Helper:      $(Sh settings get secure enabled_accessibility_services)"
Write-Host "Backup:      $BackupDir"
Write-Host @"

Next:
  1. Restart once (adb -s $Serial reboot) and check Home comes back by itself.
  2. Try the real remote: Home, YouTube button, hold Home, hold OK on a tile (Move).
  3. Developer options: Wireless debugging off, USB debugging off, revoke USB debugging authorisations, restart.
Undo everything: .\restore.ps1 -Serial <ip:port>
"@
