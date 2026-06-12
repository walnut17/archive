# 轮询 125 qa-agent /health，部署完成后 Auto-test 可先 wait 再跑 smoke

param(
    [string]$BaseUrl = "http://182.168.1.125:8001",
    [int]$TimeoutSeconds = 300,
    [int]$IntervalSeconds = 5
)

$ErrorActionPreference = "Stop"
$healthUrl = "$($BaseUrl.TrimEnd('/'))/health"
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

while ((Get-Date) -lt $deadline) {
    try {
        $resp = Invoke-WebRequest -Uri $healthUrl -TimeoutSec 5 -UseBasicParsing
        if ($resp.StatusCode -eq 200) {
            Write-Host "[OK] qa-agent 已就绪: $healthUrl" -ForegroundColor Green
            exit 0
        }
    } catch {
        Write-Host "[..] 等待 $healthUrl ($($_.Exception.Message))" -ForegroundColor DarkYellow
    }
    Start-Sleep -Seconds $IntervalSeconds
}

Write-Error "超时: $healthUrl 在 ${TimeoutSeconds}s 内未返回 200"
exit 1
