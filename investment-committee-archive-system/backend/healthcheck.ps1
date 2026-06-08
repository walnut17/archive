# healthcheck.ps1 - Archive system backend health check
# Usage: PowerShell .\healthcheck.ps1
# Tests: GET /api/health + POST /api/auth/login (admin/admin123)
# Note: use System.Net.HttpWebRequest directly (most reliable across PS 5.x
#       and Windows Server 2012 R2, bypasses Invoke-WebRequest bugs)
#
# Why not curl/Invoke-WebRequest/Invoke-RestMethod:
#   - curl.exe: not installed by default on Server 2012
#   - curl alias: returns objects, not strings
#   - Invoke-WebRequest: known IndexOutOfRangeException bug on PS 5.1 + Server 2012
#   - Invoke-RestMethod: same bug
#   - HttpWebRequest: works reliably, no surprises

$ErrorActionPreference = "Continue"

Write-Host "================================" -ForegroundColor Cyan
Write-Host " Archive System - Health Check" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Helper: do a simple HTTP request via HttpWebRequest, return body string or $null on error
function Get-HttpBody {
    param(
        [string]$Url,
        [string]$Method = "GET",
        [string]$Body = $null,
        [string]$ContentType = "application/json"
    )
    try {
        Add-Type -AssemblyName System.Net.Http
        $client = New-Object System.Net.Http.HttpClient
        $client.Timeout = [TimeSpan]::FromSeconds(10)
        $reqMsg = New-Object System.Net.Http.HttpRequestMessage(
            [System.Net.Http.HttpMethod]::new($Method), $Url)
        if ($null -ne $Body) {
            $content = New-Object System.Net.Http.StringContent(
                $Body, [System.Text.Encoding]::UTF8, $ContentType)
            $reqMsg.Content = $content
        }
        $respMsg = $client.SendAsync($reqMsg).GetAwaiter().GetResult()
        $body = $respMsg.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $client.Dispose()
        return $body
    } catch {
        return $null
    }
}

# Test 1: health
Write-Host "[1/2] GET /api/health" -ForegroundColor Yellow
$healthJson = Get-HttpBody -Url "http://localhost:8080/api/health"
if ($null -eq $healthJson) {
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
$loginJson = Get-HttpBody -Url "http://localhost:8080/api/auth/login" `
    -Method POST `
    -Body '{"username":"admin","password":"admin123"}' `
    -ContentType "application/json"
if ($null -eq $loginJson) {
    Write-Host "FAILED - login request error" -ForegroundColor Red
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
