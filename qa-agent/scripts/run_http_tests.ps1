# 直连 qa-agent 后台 HTTP 测试
# 用法:
#   cd qa-agent
#   .\.venv\Scripts\uvicorn app.main:app --host 127.0.0.1 --port 8001
#   .\scripts\run_http_tests.ps1

param(
    [string]$BaseUrl = $env:QA_AGENT_BASE_URL,
    [switch]$UnitOnly,
    [switch]$LiveOnly
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

if (-not $BaseUrl) {
    $BaseUrl = "http://127.0.0.1:8001"
}
$env:QA_AGENT_BASE_URL = $BaseUrl

$pytest = ".\.venv\Scripts\pytest.exe"
if (-not (Test-Path $pytest)) {
    Write-Error "未找到 $pytest，请先创建 venv 并 pip install -r requirements.txt"
}

Write-Host "== qa-agent 测试 ==" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl"

if (-not $LiveOnly) {
    Write-Host "`n[1/2] 单元 + 契约 (TestClient/mock)..." -ForegroundColor Yellow
    & $pytest tests/ -q -m "not live"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (-not $UnitOnly) {
    Write-Host "`n[2/2] 直连 HTTP (live)..." -ForegroundColor Yellow
    & $pytest tests/test_api_http_live.py -q
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host "`n[smoke] scripts/smoke_http.py ..." -ForegroundColor Yellow
    & ".\.venv\Scripts\python.exe" ".\scripts\smoke_http.py" --base-url $BaseUrl
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "`n全部通过。" -ForegroundColor Green
