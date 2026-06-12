# Stop background qa-agent (wraps deploy/scripts/stop-qa-agent.ps1).
param(
    [string]$LogDir = "D:\archive\logs\qa-agent"
)

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$StopScript = Join-Path $RepoRoot "deploy\scripts\stop-qa-agent.ps1"

if (-not (Test-Path $StopScript)) {
    Write-Error "Missing: $StopScript"
}

& $StopScript -LogDir $LogDir
