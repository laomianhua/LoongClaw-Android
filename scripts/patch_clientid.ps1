#Requires -Version 5.1
# Dev convenience wrapper — canonical script lives in gateway-bundle/scripts/
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$BundleScript = Join-Path $RepoRoot "gateway-bundle\scripts\patch_clientid.ps1"
if (-not (Test-Path $BundleScript)) {
    Write-Error "Missing $BundleScript"
    exit 1
}
& $BundleScript @args
exit $LASTEXITCODE
