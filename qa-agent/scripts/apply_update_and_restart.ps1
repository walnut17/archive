# Wait for HTTP response, then restart qa-agent (called from /v1/deploy/update).
param(
    [string]$RepoRoot = "",
    [string]$ConfigJson = "D:\archive\config\config.json",
    [string]$LogDir = "D:\archive\logs\qa-agent",
    [int]$DelaySeconds = 2
)

$ErrorActionPreference = "Stop"

if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
}

Start-Sleep -Seconds $DelaySeconds

$StartScript = Join-Path $RepoRoot "deploy\scripts\start-qa-agent.ps1"
if (-not (Test-Path $StartScript)) {
    Write-Error "Missing start script: $StartScript"
}

& $StartScript -RepoRoot $RepoRoot -ConfigJson $ConfigJson -LogDir $LogDir -Force
