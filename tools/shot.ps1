# Press optional keys, then capture the TV screen and pull it as PNG.
# Usage: .\tools\shot.ps1 shots\01.png [DPAD_RIGHT HOME ...]
param([Parameter(Mandatory, Position = 0)][string]$Out,
      [string]$Serial = $(if ($env:TV_SERIAL) { $env:TV_SERIAL } else { '192.168.2.5:5555' }),
      [Parameter(ValueFromRemainingArguments)][string[]]$Keys = @())
$adb = 'D:\AndroidSDK\platform-tools\adb.exe'
foreach ($k in $Keys) { & $adb -s $Serial shell input keyevent "KEYCODE_$k"; Start-Sleep -Milliseconds 400 }
Start-Sleep -Milliseconds 800
& $adb -s $Serial shell screencap -p /sdcard/_shot.png
New-Item -ItemType Directory -Force (Split-Path $Out) | Out-Null
& $adb -s $Serial pull /sdcard/_shot.png $Out | Out-Null
& $adb -s $Serial shell rm /sdcard/_shot.png
$len = if (Test-Path $Out) { (Get-Item $Out).Length } else { 0 }
if ($len -eq 0) { Write-Host "Empty screenshot (protected content such as HDMI/Netflix)"; exit 1 }
Write-Host "$Out ($len bytes)"
