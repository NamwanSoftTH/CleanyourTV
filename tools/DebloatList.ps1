# Per-model debloat lists: debloat\<model>.txt, where <model> is the device's ro.product.model
# (characters other than letters, digits, '.', '-' and '_' become '_').
# Format: "# Batch <name>" starts a batch, "# Launcher takeover" starts the packages disabled only
# after the new launcher is Home, a line starting "# ----" ends both (reference notes below it are
# never read); other lines starting with # are comments; one package per line.
# Dot-source it: . "$PSScriptRoot\tools\DebloatList.ps1"

# $PSScriptRoot is this file's folder (tools\) even when dot-sourced.
$DebloatDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'debloat'

# Android core packages: never disable (an original Mi TV Stick never booted again without them).
$CorePackages = '^(android|com\.android\.(systemui|shell|phone|settings|tv\.settings)|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|com\.google\.android\.overlay\.modules.*|com\.android\.providers\.(?!calendar|contacts|userdictionary|blockednumber).*)$'

function Get-DebloatListPath([string]$Model) {
    Join-Path $DebloatDir (($Model -replace '[^\w.-]', '_') + '.txt')
}

# Returns @{ Batches = [ordered]@{ name = @(pkgs) }; Takeover = @(pkgs) }
function Read-DebloatList([string]$Path) {
    $batches = [ordered]@{}; $takeover = @(); $section = $null
    foreach ($line in Get-Content $Path) {
        if ($line -match '^#\s*Batch\s+(.+)') { $section = $Matches[1].Trim(); $batches[$section] = @(); continue }
        if ($line -match '^#\s*Launcher takeover') { $section = '__takeover'; continue }
        if ($line -match '^#\s*----') { $section = $null; continue }   # reference section: never read
        if ($line -match '^\s*([a-zA-Z][\w.]+)\s*$') {
            if ($section -eq '__takeover') { $takeover += $Matches[1] }
            elseif ($section) { $batches[$section] += $Matches[1] }
        }
    }
    @{ Batches = $batches; Takeover = $takeover }
}
