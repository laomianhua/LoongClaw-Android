#Requires -Version 5.1

param(

    [string]$GatewayHost = "127.0.0.1",

    [int]$GatewayPort = 18789,

    [ValidateSet("minimal", "standard")]

    [string]$InstallProfile = "standard",

    [string]$OpenClawStateDir = "",

    [string]$OpenClawWorkspace = ""

)



$ErrorActionPreference = "Stop"

$BundleDir = $PSScriptRoot

$Py = if (Get-Command python -ErrorAction SilentlyContinue) { "python" } else { "python3" }



$PyArgs = @(

    (Join-Path $BundleDir "scripts\doctor_bundle.py"),

    "--bundle-dir", $BundleDir,

    "--gateway-host", $GatewayHost,

    "--gateway-port", $GatewayPort,

    "--profile", $InstallProfile

)

if ($OpenClawStateDir) {

    $PyArgs += "--openclaw-state-dir", $OpenClawStateDir

}

if ($OpenClawWorkspace) {

    $PyArgs += "--openclaw-workspace", $OpenClawWorkspace

}



& $Py @PyArgs

exit $LASTEXITCODE

