# Manual start for qa-agent (foreground; Ctrl+C to stop).
# WinSW unified deploy: docs/operations/RUNBOOK.md section 1.2
param(
    [string]$RepoRoot = "",
    [string]$ConfigJson = "D:\archive\config\config.json",
    [switch]$Reload
)

$ErrorActionPreference = "Stop"

if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
}

$QaDir = Join-Path $RepoRoot "qa-agent"
$Python = Join-Path $QaDir ".venv\Scripts\python.exe"

if (-not (Test-Path $Python)) {
    Write-Host "[ERROR] Missing: $Python" -ForegroundColor Red
    Write-Host "Run first:"
    Write-Host "  cd $QaDir"
    Write-Host "  python -m venv .venv"
    Write-Host "  .\.venv\Scripts\pip install -r requirements.txt"
    exit 1
}

if (-not (Test-Path $ConfigJson)) {
    Write-Host "[WARN] Missing: $ConfigJson (will use config search fallback)" -ForegroundColor Yellow
} else {
    $env:CONFIG_JSON_PATH = $ConfigJson
}

Set-Location $QaDir
Write-Host "Starting qa-agent at $QaDir" -ForegroundColor Cyan
Write-Host "CONFIG_JSON_PATH=$($env:CONFIG_JSON_PATH)" -ForegroundColor Cyan
Write-Host "Health: http://127.0.0.1:8001/health" -ForegroundColor Cyan

$uvicornArgs = @("-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "8001")
if ($Reload) {
    $uvicornArgs += "--reload"
}

& $Python @uvicornArgs
