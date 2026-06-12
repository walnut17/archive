# Start qa-agent (background by default). WinSW later: RUNBOOK.md section 1.2
param(
    [string]$RepoRoot = "",
    [string]$ConfigJson = "D:\archive\config\config.json",
    [string]$LogDir = "D:\archive\logs\qa-agent",
    [switch]$Foreground,
    [switch]$Force,
    [switch]$Reload
)

$ErrorActionPreference = "Stop"

if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
}

$QaDir = Join-Path $RepoRoot "qa-agent"
$Python = Join-Path $QaDir ".venv\Scripts\python.exe"
$PidFile = Join-Path $LogDir "qa-agent.pid"
$LogFile = Join-Path $LogDir "uvicorn.log"
$ErrFile = Join-Path $LogDir "uvicorn.err.log"

function Test-QaAgentRunning {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return $null }
    $raw = (Get-Content $Path -Raw).Trim()
    if (-not $raw) { return $null }
    try {
        $proc = Get-Process -Id ([int]$raw) -ErrorAction Stop
        return $proc
    } catch {
        Remove-Item $Path -Force -ErrorAction SilentlyContinue
        return $null
    }
}

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

$running = Test-QaAgentRunning -Path $PidFile
if ($running) {
    if (-not $Force) {
        Write-Host "[INFO] qa-agent already running (PID $($running.Id)). Use -Force to restart." -ForegroundColor Yellow
        Write-Host "Health: http://127.0.0.1:8001/health (remote: http://182.168.1.125:8001/health)"
        Write-Host "Stop:   .\deploy\scripts\stop-qa-agent.ps1"
        exit 0
    }
    Write-Host "[INFO] Stopping PID $($running.Id) ..." -ForegroundColor Yellow
    Stop-Process -Id $running.Id -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

if ($Reload -and -not $Foreground) {
    Write-Host "[WARN] -Reload requires -Foreground; starting background without reload." -ForegroundColor Yellow
    $Reload = $false
}

$uvicornArgs = @("-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001")
if ($Reload) {
    $uvicornArgs += "--reload"
}

Write-Host "Starting qa-agent at $QaDir" -ForegroundColor Cyan
Write-Host "CONFIG_JSON_PATH=$($env:CONFIG_JSON_PATH)" -ForegroundColor Cyan

if ($Foreground) {
    Write-Host "Mode: foreground (Ctrl+C to stop)" -ForegroundColor Cyan
    Write-Host "Health: http://127.0.0.1:8001/health (remote: http://182.168.1.125:8001/health)" -ForegroundColor Cyan
    Set-Location $QaDir
    & $Python @uvicornArgs
    exit $LASTEXITCODE
}

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

$proc = Start-Process `
    -FilePath $Python `
    -ArgumentList $uvicornArgs `
    -WorkingDirectory $QaDir `
    -WindowStyle Hidden `
    -RedirectStandardOutput $LogFile `
    -RedirectStandardError $ErrFile `
    -PassThru

Set-Content -Path $PidFile -Value $proc.Id -Encoding ascii

Write-Host "Mode: background (PID $($proc.Id))" -ForegroundColor Green
Write-Host "Log:    $LogFile" -ForegroundColor Green
Write-Host "Err:    $ErrFile" -ForegroundColor Green
Write-Host "Health: http://127.0.0.1:8001/health (remote: http://182.168.1.125:8001/health)" -ForegroundColor Cyan
Write-Host "Stop:   .\deploy\scripts\stop-qa-agent.ps1" -ForegroundColor Cyan
