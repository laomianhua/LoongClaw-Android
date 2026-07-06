#Requires -Version 5.1

param(

    [ValidateSet("minimal", "standard")]

    [string]$InstallProfile = "standard",

    [switch]$WithPcPatch,

    [string]$OpenClawStateDir = "",

    [string]$OpenClawWorkspace = ""

)



$ErrorActionPreference = "Stop"

$BundleDir = $PSScriptRoot

$Py = if (Get-Command python -ErrorAction SilentlyContinue) { "python" } else { "python3" }



$PyArgs = @(

    (Join-Path $BundleDir "scripts\install_bundle.py"),

    "--bundle-dir", $BundleDir,

    "--profile", $InstallProfile

)

if ($WithPcPatch) {

    $PyArgs += "--with-pc-patch"

}

if ($OpenClawStateDir) {

    $PyArgs += "--openclaw-state-dir", $OpenClawStateDir

}

if ($OpenClawWorkspace) {

    $PyArgs += "--openclaw-workspace", $OpenClawWorkspace

}



& $Py @PyArgs

exit $LASTEXITCODE

