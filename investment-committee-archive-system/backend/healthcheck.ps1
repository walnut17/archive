# healthcheck.ps1 - Archive system backend health check
# Usage: PowerShell .\healthcheck.ps1
# Tests: GET /api/health + POST /api/auth/login (admin/admin123)
# Requires: curl.exe in PATH (curl.se/windows, choco install curl, or winget)

$ErrorActionPreference = "Continue"

Write-Host "================================" -ForegroundColor Cyan
Write-Host " Archive System - Health Check" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Verify curl.exe is available
$curlPath = (Get-Command curl.exe -ErrorAction SilentlyContinue).Source
if (-not $curlPath) {
    Write-Host "ERROR: curl.exe not found in PATH" -ForegroundColor Red
    Write-Host "" -ForegroundColor Red
    Write-Host "Install one of:" -ForegroundColor Yellow
    Write-Host "  - choco install curl -y" -ForegroundColor Gray
    Write-Host "  - winget install cURL.cURL" -ForegroundColor Gray
    Write-Host "  - manual: download from https://curl.se/windows/" -ForegroundColor Gray
    Write-Host "" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Test 1: health
Write-Host "[1/2] GET /api/health" -ForegroundColor Yellow
$healthJson = & curl.exe -s -m 10 http://localhost:8080/api/health
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0 -or [string]::IsNullOrEmpty($healthJson)) {
    Write-Host "FAILED - cannot reach backend (curl exit=$exitCode)" -ForegroundColor Red
    Write-Host "  Please run .\startup.ps1 first" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host $healthJson
Write-Host ""
if ($healthJson -notmatch '"status":"UP"') {
    Write-Host "FAILED - /api/health did not return UP" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Test 2: login
Write-Host "[2/2] POST /api/auth/login (admin/admin123)" -ForegroundColor Yellow
$loginJson = & curl.exe -s -m 10 -X POST -H "Content-Type: application/json" `
    -d '{"username":"admin","password":"admin123"}' `
    http://localhost:8080/api/auth/login
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0 -or [string]::IsNullOrEmpty($loginJson)) {
    Write-Host "FAILED - login request error (curl exit=$exitCode)" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
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

Read-Host "Press Enter to exit"
