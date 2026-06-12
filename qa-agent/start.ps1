# Start qa-agent from this directory (wraps deploy/scripts/start-qa-agent.ps1).
param(
    [string]$ConfigJson = "D:\archive\config\config.json",
    [string]$LogDir = "D:\archive\logs\qa-agent",
    [switch]$Foreground,
    [switch]$Force,
    [switch]$Reload
)

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$DeployScript = Join-Path $RepoRoot "deploy\scripts\start-qa-agent.ps1"

if (-not (Test-Path $DeployScript)) {
    Write-Error "Missing: $DeployScript"
}

$params = @{
    RepoRoot   = $RepoRoot
    ConfigJson = $ConfigJson
    LogDir     = $LogDir
}
if ($Foreground) { $params.Foreground = $true }
if ($Force) { $params.Force = $true }
if ($Reload) { $params.Reload = $true }

& $DeployScript @params
