# Stop background qa-agent started by start-qa-agent.ps1
param(
    [string]$LogDir = "D:\archive\logs\qa-agent"
)

$ErrorActionPreference = "Stop"
$PidFile = Join-Path $LogDir "qa-agent.pid"

if (-not (Test-Path $PidFile)) {
    Write-Host "[INFO] No pid file at $PidFile (not running or started in foreground)." -ForegroundColor Yellow
    exit 0
}

$raw = (Get-Content $PidFile -Raw).Trim()
try {
    $processId = [int]$raw
} catch {
    Write-Host "[WARN] Invalid pid file; removing." -ForegroundColor Yellow
    Remove-Item $PidFile -Force
    exit 0
}

try {
    $proc = Get-Process -Id $processId -ErrorAction Stop
    Write-Host "Stopping qa-agent PID $processId ($($proc.ProcessName)) ..." -ForegroundColor Cyan
    Stop-Process -Id $processId -Force
    Start-Sleep -Milliseconds 500
    Write-Host "[OK] Stopped." -ForegroundColor Green
} catch {
    Write-Host "[INFO] Process $processId not found (already exited)." -ForegroundColor Yellow
}

Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
