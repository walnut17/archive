# startup.ps1 - Archive system backend one-click startup
# Usage: PowerShell .\startup.ps1
# This script: git pull + mvn clean package + start backend

$ErrorActionPreference = "Continue"

Set-Location -Path $PSScriptRoot

Write-Host "================================" -ForegroundColor Cyan
Write-Host " Archive System - Backend Startup" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: git pull
# Note: redirect stderr to file then read it, because PowerShell 5.x treats
# git's stderr (e.g. "From gitee.com...") as RemoteException even on success.
Write-Host "[1/4] Pulling latest code from Gitee..." -ForegroundColor Yellow
Set-Location ..
& git pull origin minimax *>&1 | Out-Null
$gitLog = & git pull origin minimax 2>&1 | Out-String
Write-Host $gitLog
if ($LASTEXITCODE -ne 0) {
    Write-Host "git pull FAILED! Check network or SSH key" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host "OK" -ForegroundColor Green
Write-Host ""

Set-Location backend

# Step 2: mvn clean package
# Note: redirect to build.log file, then read tail on failure
Write-Host "[2/4] Building JAR (first run 5-10 min, please wait)..." -ForegroundColor Yellow
& mvn clean package -DskipTests *> build.log
if ($LASTEXITCODE -ne 0) {
    Write-Host "mvn build FAILED! Check build.log for details" -ForegroundColor Red
    Get-Content build.log -Tail 30
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host "OK" -ForegroundColor Green
Write-Host ""

# Step 3: prepare log dir and verify config.json exists
Write-Host "[3/4] Preparing log directory + verifying config.json..." -ForegroundColor Yellow
$logDir = "D:\archive\logs"
if (!(Test-Path $logDir)) {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    Write-Host "Created log dir: $logDir" -ForegroundColor Green
} else {
    Write-Host "Log dir exists" -ForegroundColor Green
}

# Verify config.json location
$cfgPath = "D:\archive\config\config.json"
if (!(Test-Path $cfgPath)) {
    Write-Host "WARNING: config.json not found at $cfgPath" -ForegroundColor Red
    Write-Host "         Backend will use application.yml defaults (empty password!)" -ForegroundColor Red
    Write-Host "         Copy from config\config.example.json first" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host "config.json found: $cfgPath" -ForegroundColor Green
Write-Host ""

# Step 4: start backend
Write-Host "[4/4] Starting backend..." -ForegroundColor Yellow
Write-Host "  Port: 8080" -ForegroundColor Gray
Write-Host "  Log:  D:\archive\logs\backend.log" -ForegroundColor Gray
Write-Host "  URL:  http://localhost:8080/api/health" -ForegroundColor Gray
Write-Host "  Config: D:\archive\config\config.json (via CONFIG_JSON_PATH env)" -ForegroundColor Gray
Write-Host ""
Write-Host "Wait for: 'Tomcat started on port 8080' + 'Started ArchiveApplication'" -ForegroundColor Magenta
Write-Host "Press Ctrl+C to stop" -ForegroundColor Magenta
Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# KEY FIX: set CONFIG_JSON_PATH env var so ConfigJsonLoader finds it
$env:CONFIG_JSON_PATH = $cfgPath
java "-Dfile.encoding=UTF-8" -jar ".\target\archive.jar"
