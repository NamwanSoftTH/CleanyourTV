# Per-model debloat lists: debloat\<model>.txt, where <model> is the device's ro.product.model
# (characters other than letters, digits, '.', '-' and '_' become '_').
#
# Two formats are read:
#  - CleanYourTV style: comment headings ("# Telemetry and feedback") with one package per line under
#    them; text after "#" on a package line is a comment; "# com.example.app" is a package kept ON.
#    Each heading that has packages under it becomes one batch.
#  - This project's style: "# Batch <name>" starts a batch, "# Launcher takeover" starts the packages
#    disabled only after the new launcher is Home.
# In both, "# ----" ends reading (reference notes below it are ignored), and the stock Home and setup
# wizard (launcherx, setupwraith) always go to Takeover wherever they are listed: they may only be
# disabled once the new launcher is Home.
# A file whose first line is "# Same as: <other model>" uses that model's list.
# Dot-source it: . "$PSScriptRoot\tools\DebloatList.ps1"

# $PSScriptRoot is this file's folder (tools\) even when dot-sourced.
$DebloatDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'debloat'

# Android core packages: never disable (an original Mi TV Stick never booted again without them).
$CorePackages = '^(android|com\.android\.(systemui|shell|phone|settings|tv\.settings)|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|com\.google\.android\.overlay\.modules.*|com\.android\.providers\.(?!calendar|contacts|userdictionary|blockednumber).*)$'

# Always disabled last, after Home has moved to the new launcher.
$TakeoverPackages = @('com.google.android.apps.tv.launcherx', 'com.google.android.tungsten.setupwraith')

$PackagePattern = '^[a-zA-Z][\w-]*(\.[\w-]+)+$'

function Get-DebloatListPath([string]$Model) {
    Join-Path $DebloatDir (($Model -replace '[^\w.-]', '_') + '.txt')
}

# Returns @{ Batches = [ordered]@{ name = @(pkgs) }; Takeover = @(pkgs) }
function Read-DebloatList([string]$Path, [int]$Depth = 0) {
    $first = Get-Content $Path -TotalCount 1
    if ($first -match '^#\s*Same as:\s*(\S+)') {
        if ($Depth -ge 3) { throw "Too many 'Same as' hops at $Path" }
        $target = Get-DebloatListPath $Matches[1]
        if (-not (Test-Path $target)) { throw "$Path says 'Same as: $($Matches[1])', but $target does not exist" }
        return Read-DebloatList $target ($Depth + 1)
    }
    $batches = [ordered]@{}; $takeover = [System.Collections.Generic.List[string]]::new()
    $heading = 'Batch'; $inTakeover = $false
    foreach ($raw in Get-Content $Path) {
        $line = $raw.Trim()
        if (-not $line) { continue }
        if ($line -match '^#\s*----') { break }
        if ($line.StartsWith('#')) {
            $text = $line.TrimStart('#').Trim()
            $word = ($text -split '\s+')[0]
            if ($word -match $PackagePattern) { continue }          # "# com.example.app": kept ON
            if ($text -match '^Launcher takeover') { $inTakeover = $true; continue }
            if ($text -match '^Batch\s+(.+)') { $heading = $Matches[1].Trim(); $inTakeover = $false; continue }
            if ($text -and $text -notmatch '^[-(]') {               # a heading (not a "- note" line)
                $heading = if ($text.Length -gt 60) { $text.Substring(0, 57) + '...' } else { $text }
                $inTakeover = $false
            }
            continue
        }
        $pkg = ($line -split '#')[0].Trim()
        if ($pkg -notmatch $PackagePattern) { continue }
        if ($inTakeover -or $TakeoverPackages -contains $pkg) {
            if (-not $takeover.Contains($pkg)) { $takeover.Add($pkg) }
            continue
        }
        if (-not $batches.Contains($heading)) { $batches[$heading] = [System.Collections.Generic.List[string]]::new() }
        $batches[$heading].Add($pkg)
    }
    @{ Batches = $batches; Takeover = @($takeover) }
}
