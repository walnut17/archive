# Wait for HTTP response, then restart qa-agent (called from /v1/deploy/update).
param(
    [string]$RepoRoot = "",
    [string]$ConfigJson = "D:\archive\config\config.json",
    [string]$LogDir = "D:\archive\logs\qa-agent",
    [int]$DelaySeconds = 2,
    [int]$Port = 8001
)

$ErrorActionPreference = "Continue"

if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
}

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$RestartLog = Join-Path $LogDir "apply_update_restart.log"

function Write-RestartLog {
    param([string]$Message)
    "$(Get-Date -Format o) $Message" | Out-File -FilePath $RestartLog -Append -Encoding utf8
}

function Stop-ListenerOnPort {
    param([int]$ListenPort)
    $killed = @()
    try {
        $conns = Get-NetTCPConnection -LocalPort $ListenPort -State Listen -ErrorAction SilentlyContinue
        foreach ($c in $conns) {
            $procId = $c.OwningProcess
            if ($procId -and $procId -notin $killed) {
                Write-RestartLog "kill port $ListenPort PID $procId"
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                $killed += $procId
            }
        }
    } catch {
        Write-RestartLog "Get-NetTCPConnection failed: $_"
    }
    if ($killed.Count -eq 0) {
        try {
            $lines = netstat -ano | Select-String ":$ListenPort\s"
            foreach ($line in $lines) {
                if ($line -match '\s+(\d+)\s*$') {
                    $procId = [int]$Matches[1]
                    if ($procId -gt 0 -and $procId -notin $killed) {
                        Write-RestartLog "netstat kill port $ListenPort PID $procId"
                        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                        $killed += $procId
                    }
                }
            }
        } catch {
            Write-RestartLog "netstat fallback failed: $_"
        }
    }
    return ($killed.Count -gt 0)
}

Write-RestartLog "=== restart begin RepoRoot=$RepoRoot LogDir=$LogDir ==="

Start-Sleep -Seconds $DelaySeconds

$StartScript = Join-Path $RepoRoot "deploy\scripts\start-qa-agent.ps1"
$StopScript = Join-Path $RepoRoot "deploy\scripts\stop-qa-agent.ps1"
if (-not (Test-Path $StartScript)) {
    Write-RestartLog "FATAL missing start script: $StartScript"
    exit 1
}

$beforeStartedAt = $null
try {
    $before = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 5
    $beforeStartedAt = $before.process_started_at
    if (-not $beforeStartedAt) { $beforeStartedAt = $before.version }
    Write-RestartLog "before health process_started_at=$beforeStartedAt"
} catch {
    Write-RestartLog "before health unavailable: $_"
}

if (Test-Path $StopScript) {
    for ($i = 1; $i -le 3; $i++) {
        Write-RestartLog "stop attempt $i via $StopScript"
        & $StopScript -LogDir $LogDir -Port $Port 2>&1 | ForEach-Object { Write-RestartLog "stop> $_" }
        Stop-ListenerOnPort -ListenPort $Port | Out-Null
        Start-Sleep -Seconds 2
    }
} else {
    Write-RestartLog "WARN missing stop script: $StopScript; port-kill only"
    for ($i = 1; $i -le 3; $i++) {
        Stop-ListenerOnPort -ListenPort $Port | Out-Null
        Start-Sleep -Seconds 2
    }
}

try {
    & $StartScript -RepoRoot $RepoRoot -ConfigJson $ConfigJson -LogDir $LogDir -Force 2>&1 |
        ForEach-Object { Write-RestartLog "start> $_" }
} catch {
    Write-RestartLog "start failed: $_"
    exit 1
}

Start-Sleep -Seconds 5

$restarted = $false
for ($i = 1; $i -le 6; $i++) {
    try {
        $after = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 10
        $afterStartedAt = $after.process_started_at
        if (-not $afterStartedAt) { $afterStartedAt = $after.version }
        Write-RestartLog "health poll $i process_started_at=$afterStartedAt"
        if ($beforeStartedAt -and $afterStartedAt -and $afterStartedAt -ne $beforeStartedAt) {
            $restarted = $true
            break
        }
        if (-not $beforeStartedAt -and $after.status -eq "ok") {
            $restarted = $true
            break
        }
    } catch {
        Write-RestartLog "health poll $i failed: $_"
    }
    Start-Sleep -Seconds 2
}

if ($restarted) {
    Write-RestartLog "=== restart OK ==="
    exit 0
}

Write-RestartLog "=== restart FAILED (process_started_at unchanged) ==="
exit 1
