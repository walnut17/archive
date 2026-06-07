# healthcheck.ps1 - Archive system backend health check
# Usage: PowerShell .\healthcheck.ps1
# Tests: GET /api/health + POST /api/auth/login (admin/admin123)

$ErrorActionPreference = "Continue"

Write-Host "================================" -ForegroundColor Cyan
Write-Host " Archive System - Health Check" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Test 1: health
Write-Host "[1/2] GET /api/health" -ForegroundColor Yellow
try {
    $healthResp = curl -s -w "`nHTTP %{http_code}" http://localhost:8080/api/health 2>&1
    Write-Host $healthResp
    Write-Host ""
} catch {
    Write-Host "FAILED - backend may not be running" -ForegroundColor Red
    Write-Host "Please run .\startup.ps1 first" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Test 2: login
Write-Host "[2/2] POST /api/auth/login (admin/admin123)" -ForegroundColor Yellow
$body = @{ username = "admin"; password = "admin123" } | ConvertTo-Json
try {
    $loginResp = curl -s -X POST -Uri http://localhost:8080/api/auth/login -ContentType "application/json" -Body $body 2>&1
    Write-Host $loginResp
    Write-Host ""

    if ($loginResp -match '"token":"eyJ') {
        Write-Host "================================" -ForegroundColor Green
        Write-Host " M0 Backend PASSED!" -ForegroundColor Green
        Write-Host "================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "Next steps:" -ForegroundColor Yellow
        Write-Host "  1. Start frontend: cd ../frontend && npm install && npm run dev" -ForegroundColor Gray
        Write-Host "  2. Open browser: http://localhost:5173" -ForegroundColor Gray
        Write-Host "  3. Login admin / admin123" -ForegroundColor Gray
    } else {
        Write-Host "Login did NOT return token - check response" -ForegroundColor Red
    }
} catch {
    Write-Host "FAILED - login request error" -ForegroundColor Red
}

Read-Host "Press Enter to exit"
