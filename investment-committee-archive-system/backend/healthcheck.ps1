# healthcheck.ps1 - Archive system backend health check
# Usage: PowerShell .\healthcheck.ps1
# Tests: GET /api/health + POST /api/auth/login (admin/admin123)
# Note: use Invoke-WebRequest (built-in, PowerShell 5.x compatible)
#       not curl.exe (often missing on Windows Server)

$ErrorActionPreference = "Continue"

Write-Host "================================" -ForegroundColor Cyan
Write-Host " Archive System - Health Check" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Test 1: health
Write-Host "[1/2] GET /api/health" -ForegroundColor Yellow
try {
    $healthResp = Invoke-WebRequest -Uri http://localhost:8080/api/health -UseBasicParsing -TimeoutSec 10
    $healthJson = $healthResp.Content
    Write-Host $healthJson
    Write-Host ""
    if ($healthJson -notmatch '"status":"UP"') {
        Write-Host "FAILED - /api/health did not return UP" -ForegroundColor Red
        Read-Host "Press Enter to exit"
        exit 1
    }
} catch {
    Write-Host "FAILED - cannot reach backend: $_" -ForegroundColor Red
    Write-Host "Please run .\startup.ps1 first" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Test 2: login
Write-Host "[2/2] POST /api/auth/login (admin/admin123)" -ForegroundColor Yellow
try {
    $loginBody = '{"username":"admin","password":"admin123"}'
    $loginResp = Invoke-WebRequest -Uri http://localhost:8080/api/auth/login `
        -Method POST -ContentType "application/json" -Body $loginBody `
        -UseBasicParsing -TimeoutSec 10
    $loginJson = $loginResp.Content
    Write-Host $loginJson
    Write-Host ""

    if ($loginJson -match '"token":"eyJ') {
        Write-Host "================================" -ForegroundColor Green
        Write-Host " M0 Backend PASSED!" -ForegroundColor Green
        Write-Host "================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "Next steps:" -ForegroundColor Yellow
        Write-Host "  1. Browser: http://localhost:5173 (frontend already running)" -ForegroundColor Gray
        Write-Host "  2. Login admin / admin123" -ForegroundColor Gray
        Write-Host "  3. Dashboard should show: Backend health: UP" -ForegroundColor Gray
    } else {
        Write-Host "Login did NOT return token - check response above" -ForegroundColor Red
    }
} catch {
    Write-Host "FAILED - login request error: $_" -ForegroundColor Red
}

Read-Host "Press Enter to exit"
