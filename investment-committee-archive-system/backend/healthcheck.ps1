# healthcheck.ps1 - Archive system backend health check
# Usage: PowerShell .\healthcheck.ps1
# Tests: GET /api/health + POST /api/auth/login (admin/admin123)
# Note: use curl.exe (real curl) instead of PowerShell alias Invoke-WebRequest

$ErrorActionPreference = "Continue"

Write-Host "================================" -ForegroundColor Cyan
Write-Host " Archive System - Health Check" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Test 1: health
Write-Host "[1/2] GET /api/health" -ForegroundColor Yellow
$curlExe = (Get-Command curl.exe -ErrorAction SilentlyContinue).Source
if (-not $curlExe) {
    # Try to locate curl.exe in PATH
    $curlExe = "curl.exe"
}
$healthResp = & $curlExe -s http://localhost:8080/api/health
Write-Host $healthResp
Write-Host ""

if ($healthResp -notmatch '"status":"UP"') {
    Write-Host "FAILED - /api/health did not return UP" -ForegroundColor Red
    Write-Host "Please run .\startup.ps1 first" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Test 2: login
Write-Host "[2/2] POST /api/auth/login (admin/admin123)" -ForegroundColor Yellow
$body = '{"username":"admin","password":"admin123"}'
$loginResp = & $curlExe -s -X POST -Uri http://localhost:8080/api/auth/login -ContentType "application/json" -Body $body
Write-Host $loginResp
Write-Host ""

if ($loginResp -match '"token":"eyJ') {
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

Read-Host "Press Enter to exit"
