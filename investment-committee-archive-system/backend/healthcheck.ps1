# healthcheck.ps1 - Archive system backend health check
# Usage: PowerShell .\healthcheck.ps1
# Tests: GET /api/health + POST /api/auth/login (admin/admin123)
# Tries to locate curl.exe in this order:
#   1. PATH (curl.exe)
#   2. Git for Windows bundled curl (C:\Program Files\Git\mingw64\bin\curl.exe)
#   3. Fallback: .NET HttpClient (always works, last resort)

$ErrorActionPreference = "Continue"

Write-Host "================================" -ForegroundColor Cyan
Write-Host " Archive System - Health Check" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Locate curl.exe
$curlPath = $null
$candidates = @(
    (Get-Command curl.exe -ErrorAction SilentlyContinue).Source,
    "C:\Program Files\Git\mingw64\bin\curl.exe",
    "C:\Program Files (x86)\Git\mingw64\bin\curl.exe",
    "C:\tools\curl\bin\curl.exe"
)
foreach ($c in $candidates) {
    if ($c -and (Test-Path $c)) {
        $curlPath = $c
        break
    }
}

if ($curlPath) {
    Write-Host "Using curl at: $curlPath" -ForegroundColor Gray
    Write-Host ""

    # Test 1: health
    Write-Host "[1/2] GET /api/health" -ForegroundColor Yellow
    $healthJson = & $curlPath -s -m 10 http://localhost:8080/api/health
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrEmpty($healthJson)) {
        Write-Host "FAILED - cannot reach backend" -ForegroundColor Red
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
    # Build body via file to avoid PowerShell {}-in-single-quote parsing issue
    $bodyFile = Join-Path $env:TEMP "archive-login-body.json"
    Set-Content -Path $bodyFile -Value '{"username":"admin","password":"admin123"}' -Encoding UTF8
    $loginJson = & $curlPath -s -m 10 -X POST -H "Content-Type: application/json" `
        --data-binary "@$bodyFile" `
        http://localhost:8080/api/auth/login
    Remove-Item $bodyFile -ErrorAction SilentlyContinue
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrEmpty($loginJson)) {
        Write-Host "FAILED - login request error" -ForegroundColor Red
        Read-Host "Press Enter to exit"
        exit 1
    }
    Write-Host $loginJson
    Write-Host ""
} else {
    # No curl -- fall back to .NET HttpClient
    Write-Host "INFO: curl.exe not found, using .NET HttpClient" -ForegroundColor Yellow
    Add-Type -AssemblyName System.Net.Http
    function Get-HealthJson {
        $c = New-Object System.Net.Http.HttpClient
        $c.Timeout = [TimeSpan]::FromSeconds(10)
        $r = $c.GetAsync("http://localhost:8080/api/health").GetAwaiter().GetResult()
        $b = $r.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $c.Dispose()
        return $b
    }
    function Post-Login {
        param([string]$body)
        $c = New-Object System.Net.Http.HttpClient
        $c.Timeout = [TimeSpan]::FromSeconds(10)
        $content = New-Object System.Net.Http.StringContent(
            $body, [System.Text.Encoding]::UTF8, "application/json")
        $r = $c.PostAsync("http://localhost:8080/api/auth/login", $content).GetAwaiter().GetResult()
        $b = $r.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $c.Dispose()
        return $b
    }

    Write-Host "[1/2] GET /api/health" -ForegroundColor Yellow
    try {
        $healthJson = Get-HealthJson
    } catch {
        Write-Host "FAILED - cannot reach backend: $_" -ForegroundColor Red
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

    Write-Host "[2/2] POST /api/auth/login (admin/admin123)" -ForegroundColor Yellow
    try {
        $loginBody = '{"username":"admin","password":"admin123"}'
        $loginJson = Post-Login $loginBody
    } catch {
        Write-Host "FAILED - login request error: $_" -ForegroundColor Red
        Read-Host "Press Enter to exit"
        exit 1
    }
    Write-Host $loginJson
    Write-Host ""
}

# Common: report
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
