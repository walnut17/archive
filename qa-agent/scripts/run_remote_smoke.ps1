# 从开发机发起 AT-001 live 测试，目标：125 上的 qa-agent
# 前提：125 已部署 qa-agent 且 8001 对本机网络可达
#
# 用法（项目根或 qa-agent 目录均可）:
#   .\qa-agent\scripts\run_remote_smoke.ps1
#   .\qa-agent\scripts\run_remote_smoke.ps1 -WaitSeconds 300

param(
    [string]$BaseUrl = "http://182.168.1.125:8001",
    [int]$WaitSeconds = 0
)

$ErrorActionPreference = "Stop"
$qaAgentRoot = Split-Path $PSScriptRoot -Parent
Set-Location $qaAgentRoot

$env:QA_AGENT_BASE_URL = $BaseUrl

if ($WaitSeconds -gt 0) {
    Write-Host "等待 qa-agent 就绪: $BaseUrl (最多 ${WaitSeconds}s)..." -ForegroundColor Yellow
    & "$PSScriptRoot\wait_for_qa_agent.ps1" -BaseUrl $BaseUrl -TimeoutSeconds $WaitSeconds
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

& "$PSScriptRoot\run_http_tests.ps1" -LiveOnly -BaseUrl $BaseUrl
