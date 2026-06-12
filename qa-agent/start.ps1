# Start qa-agent from this directory (wraps deploy/scripts/start-qa-agent.ps1).
param(
    [string]$ConfigJson = "D:\archive\config\config.json",
    [switch]$Reload
)

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$DeployScript = Join-Path $RepoRoot "deploy\scripts\start-qa-agent.ps1"

if (-not (Test-Path $DeployScript)) {
    Write-Error "Missing: $DeployScript"
}

if ($Reload) {
    & $DeployScript -RepoRoot $RepoRoot -ConfigJson $ConfigJson -Reload
} else {
    & $DeployScript -RepoRoot $RepoRoot -ConfigJson $ConfigJson
}
