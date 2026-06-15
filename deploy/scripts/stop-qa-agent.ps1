# Stop background qa-agent started by start-qa-agent.ps1
param(
    [string]$LogDir = "D:\archive\logs\qa-agent",
    [int]$Port = 8001
)

$ErrorActionPreference = "Continue"
$PidFile = Join-Path $LogDir "qa-agent.pid"
$stopped = $false

function Write-StopLog {
    param([string]$Message)
    $log = Join-Path $LogDir "stop-qa-agent.log"
    try {
        New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
        "$(Get-Date -Format o) $Message" | Out-File -FilePath $log -Append -Encoding utf8
    } catch { }
}

function Stop-ListenerOnPort {
    param([int]$ListenPort)
    $killed = @()
    try {
        $conns = Get-NetTCPConnection -LocalPort $ListenPort -State Listen -ErrorAction SilentlyContinue
        foreach ($c in $conns) {
            $procId = $c.OwningProcess
            if ($procId -and $procId -notin $killed) {
                Write-StopLog "port $ListenPort listener PID $procId"
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                $killed += $procId
            }
        }
    } catch {
        Write-StopLog "Get-NetTCPConnection failed: $_"
    }
    if ($killed.Count -gt 0) { return $true }

    # netstat fallback (older Windows / permission limits)
    try {
        $lines = netstat -ano | Select-String ":$ListenPort\s"
        foreach ($line in $lines) {
            if ($line -match '\s+(\d+)\s*$') {
                $procId = [int]$Matches[1]
                if ($procId -gt 0 -and $procId -notin $killed) {
                    Write-StopLog "netstat port $ListenPort PID $procId"
                    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                    $killed += $procId
                }
            }
        }
    } catch {
        Write-StopLog "netstat fallback failed: $_"
    }
    return ($killed.Count -gt 0)
}

if (Test-Path $PidFile) {
    $raw = (Get-Content $PidFile -Raw).Trim()
    try {
        $processId = [int]$raw
        $proc = Get-Process -Id $processId -ErrorAction Stop
        Write-Host "Stopping qa-agent PID $processId ($($proc.ProcessName)) ..." -ForegroundColor Cyan
        Write-StopLog "stop pid file PID $processId"
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 800
        $stopped = $true
    } catch {
        Write-Host "[INFO] Process $raw not found (already exited)." -ForegroundColor Yellow
        Write-StopLog "pid file stale: $raw"
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "[INFO] No pid file at $PidFile" -ForegroundColor Yellow
    Write-StopLog "no pid file"
}

if (Stop-ListenerOnPort -ListenPort $Port) {
    $stopped = $true
    Start-Sleep -Milliseconds 500
}

if ($stopped) {
    Write-Host "[OK] Stopped." -ForegroundColor Green
    Write-StopLog "stopped ok"
} else {
    Write-Host "[INFO] No qa-agent listener found on port $Port." -ForegroundColor Yellow
    Write-StopLog "nothing to stop"
}
