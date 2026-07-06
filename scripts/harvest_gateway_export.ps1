# Harvest ~/.openclaw assets into gateway-export/ for bundle review.
param(
    [string]$OpenClawHome = "$env:USERPROFILE\.openclaw",
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"
$ExportRoot = Join-Path $RepoRoot "gateway-export"
$SkillsAllowlist = @(
    "gallery-display",
    "file-viewer-display",
    "file-manager",
    "notepad",
    "weather-display"
)

function Copy-IfExists($Src, $Dst) {
    if (Test-Path $Src) {
        New-Item -ItemType Directory -Force -Path (Split-Path $Dst) | Out-Null
        Copy-Item -Recurse -Force $Src $Dst
        Write-Host "  + $Dst"
    }
}

Write-Host "Harvest from: $OpenClawHome"
Write-Host "Export to:    $ExportRoot"

New-Item -ItemType Directory -Force -Path $ExportRoot | Out-Null

Write-Host "`n[skills]"
$wsSkills = Join-Path $OpenClawHome "workspace\skills"
foreach ($name in $SkillsAllowlist) {
    Copy-IfExists (Join-Path $wsSkills $name) (Join-Path $ExportRoot "skills\$name")
}

Write-Host "`n[scripts]"
$scriptsSrc = Join-Path $OpenClawHome "workspace\scripts"
$scriptsDst = Join-Path $ExportRoot "scripts"
if (Test-Path $scriptsSrc) {
    New-Item -ItemType Directory -Force -Path $scriptsDst | Out-Null
    Get-ChildItem $scriptsSrc -Filter "*.py" | ForEach-Object {
        Copy-Item $_.FullName (Join-Path $scriptsDst $_.Name) -Force
        Write-Host "  + $($_.Name)"
    }
}

Write-Host "`n[canvas]"
$canvasAllow = @(
    "map.littlehelper.html",
    "view_stored_img.html",
    "gallery.html"
)
$canvasSrc = Join-Path $OpenClawHome "canvas"
$canvasDst = Join-Path $ExportRoot "canvas"
New-Item -ItemType Directory -Force -Path $canvasDst | Out-Null
foreach ($file in $canvasAllow) {
    $src = Join-Path $canvasSrc $file
    if (Test-Path $src) {
        Copy-Item $src (Join-Path $canvasDst $file) -Force
        Write-Host "  + $file"
    }
}

Write-Host "`nDone. Update gateway-export/EXPORT_NOTES.md then run: python scripts/build_gateway_bundle.py"
