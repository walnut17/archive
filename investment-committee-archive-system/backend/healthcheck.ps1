# healthcheck.ps1 - 投委会档案系统后端健康检查
# 用途:验证后端是否跑通(2 个 curl)
# 使用:在 PowerShell 里 .\healthcheck.ps1

Write-Host "================================" -ForegroundColor Cyan
Write-Host " 投委会档案 - 后端健康检查" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# 1. 健康检查
Write-Host "[1/2] GET /api/health" -ForegroundColor Yellow
try {
    $healthResp = curl -s -w "`nHTTP %{http_code}" http://localhost:8080/api/health
    Write-Host $healthResp
    Write-Host ""
} catch {
    Write-Host "✗ /api/health 调用失败 - 后端可能没启动" -ForegroundColor Red
    Write-Host "  请先跑 .\startup.ps1" -ForegroundColor Red
    Read-Host "按 Enter 退出"
    exit 1
}

# 2. 登录测试
Write-Host "[2/2] POST /api/auth/login (admin/admin123)" -ForegroundColor Yellow
$body = @{ username = "admin"; password = "admin123" } | ConvertTo-Json
try {
    $loginResp = curl -s -X POST -Uri http://localhost:8080/api/auth/login -ContentType "application/json" -Body $body
    Write-Host $loginResp
    Write-Host ""

    # 简单判断
    if ($loginResp -match '"token":"eyJ') {
        Write-Host "================================" -ForegroundColor Green
        Write-Host " ✓✓✓ M0 后端跑通!" -ForegroundColor Green
        Write-Host "================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "下一步:" -ForegroundColor Yellow
        Write-Host "  1. 浏览器打开 http://localhost:5173(先启动前端)" -ForegroundColor Gray
        Write-Host "  2. admin / admin123 登录" -ForegroundColor Gray
        Write-Host "  3. 工作台显示"后端健康: UP" = M0 端到端跑通" -ForegroundColor Gray
    } else {
        Write-Host "✗ 登录没拿到 token,响应有问题" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ /api/auth/login 调用失败" -ForegroundColor Red
}

Read-Host "按 Enter 退出"
