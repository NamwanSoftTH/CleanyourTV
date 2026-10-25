# Disable packages one per shell call, refusing Android core packages.
# Usage: .\tools\disable.ps1 pkg1 pkg2 ...
[CmdletBinding(PositionalBinding = $false)]
param([string]$Serial = $(if ($env:TV_SERIAL) { $env:TV_SERIAL } else { '192.168.2.5:5555' }),
      [Parameter(ValueFromRemainingArguments)][string[]]$Packages = @())
$adb = 'D:\AndroidSDK\platform-tools\adb.exe'
$core = '^(android|com\.android\.(systemui|shell|phone|settings|tv\.settings)|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|com\.google\.android\.overlay\.modules.*|com\.android\.providers\.(?!calendar|contacts|userdictionary|blockednumber).*)$'
$bad = $Packages | Where-Object { $_ -match $core }
if ($bad) { Write-Error "Refusing core packages: $($bad -join ', ')"; exit 1 }
foreach ($p in $Packages) { Write-Host "$p -> $(& $adb -s $Serial shell pm disable-user --user 0 $p)" }
$off = & $adb -s $Serial shell pm list packages -d
foreach ($p in $Packages) { if ($off -notcontains "package:$p") { Write-Warning "$p is NOT disabled" } }
