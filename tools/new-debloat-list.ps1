# Write a draft debloat list for the connected TV's model: debloat\<model>.txt.
# Every system package is listed COMMENTED OUT; Android core packages are flagged "never".
# Review it, move the packages you want off under a "# Batch ..." line (uncommented), check each
# unknown one first with `adb shell dumpsys package <name>` (never open factory/fac menu apps),
# then run setup-new-box.ps1: from now on every unit of this model is debloated automatically.
# Usage: .\tools\new-debloat-list.ps1 -Serial 192.168.x.x:<port> [-Force]
param([string]$Serial = $env:TV_SERIAL,
      [switch]$Force)
$ErrorActionPreference = 'Stop'
$adb = 'D:\AndroidSDK\platform-tools\adb.exe'
. "$PSScriptRoot\DebloatList.ps1"
if (-not $Serial) { throw 'No device: pass -Serial 192.168.x.x:<port> or set $env:TV_SERIAL' }
function Sh { & $adb -s $Serial shell @args }

$model = (Sh getprop ro.product.model).Trim()
$path = Get-DebloatListPath $model
if ((Test-Path $path) -and -not $Force) { throw "$path already exists (use -Force to overwrite)" }

$sdk = (Sh getprop ro.build.version.sdk).Trim()
$fw = (Sh getprop ro.build.version.incremental).Trim()
$brand = (Sh getprop ro.product.brand).Trim()
$system = (Sh pm list packages -s) -replace '^package:', '' | Sort-Object
$disabled = (Sh pm list packages -d) -replace '^package:', ''
$homes = (Sh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME) |
    Where-Object { $_ -match '/' } | ForEach-Object { ($_.Trim() -split '/')[0] } | Sort-Object -Unique

$out = [System.Collections.Generic.List[string]]::new()
$out.Add("# Debloat list for $model ($brand), SDK $sdk, firmware $fw - DRAFT written $((Get-Date).ToString('yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture))")
$out.Add('# Move packages to turn off under a "# Batch" line and remove the leading "# " (one package per line).')
$out.Add('# Keep batches small; check unknown packages with: adb shell dumpsys package <name>')
$out.Add('# NEVER: TV app / HDMI / tuner / input (source) menu, remote & Bluetooth pairing, keyboard, Play Store/Services.')
$out.Add('')
$out.Add('# Batch A - telemetry')
$out.Add('')
$out.Add('# Batch B - manufacturer content / ads')
$out.Add('')
$out.Add('# Batch C - unused preloads')
$out.Add('')
$out.Add('# Launcher takeover - disabled only after the new launcher is Home (stock Home, setup wizard).')
$out.Add('# Apps that claim HOME on this TV (CVTE boards: do NOT disable their com.cvte.tv.launcher.*):')
foreach ($h in $homes) { if ($h -ne 'com.launcher.clean') { $out.Add("#   $h") } }
$out.Add('')
$out.Add('# ---- All system packages on this TV (reference; do not uncomment here) ----')
foreach ($p in $system) {
    $tag = if ($p -match $CorePackages) { '   <- never (Android core)' }
           elseif ($disabled -contains $p) { '   <- already disabled' }
           else { '' }
    $out.Add("# $p$tag")
}
New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
$out | Set-Content -Encoding utf8 $path
Write-Host "Draft written: $path ($($system.Count) system packages)"
