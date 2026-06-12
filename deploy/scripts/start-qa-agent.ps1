# 125 / 本机 — 手工启动 qa-agent（前台，Ctrl+C 停止）
# 一体化 WinSW 部署留待后续，见 docs/operations/RUNBOOK.md §1.2
param(
    [string]$RepoRoot = "",
    [string]$ConfigJson = "D:\archive\config\config.json",
    [switch]$Reload
)

$ErrorActionPreference = "Stop"

if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
}

$QaDir = Join-Path $RepoRoot "qa-agent"
$Python = Join-Path $QaDir ".venv\Scripts\python.exe"

if (-not (Test-Path $Python)) {
    Write-Host "[错误] 未找到 $Python" -ForegroundColor Red
    Write-Host "请先执行:"
    Write-Host "  cd $QaDir"
    Write-Host "  python -m venv .venv"
    Write-Host "  .\.venv\Scripts\pip install -r requirements.txt"
    exit 1
}

if (-not (Test-Path $ConfigJson)) {
    Write-Host "[警告] 未找到 $ConfigJson — 将依赖 config 查找顺序中的下一项" -ForegroundColor Yellow
} else {
    $env:CONFIG_JSON_PATH = $ConfigJson
}

Set-Location $QaDir
Write-Host "启动 qa-agent @ $QaDir (CONFIG_JSON_PATH=$env:CONFIG_JSON_PATH)" -ForegroundColor Cyan
Write-Host "健康检查: http://127.0.0.1:8001/health" -ForegroundColor Cyan

$uvicornArgs = @("-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "8001")
if ($Reload) { $uvicornArgs += "--reload" }

& $Python @uvicornArgs
