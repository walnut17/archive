# startup.ps1 - 投委会档案系统后端一键启动脚本
# 用途:git pull 最新代码 + mvn clean package + 启动后端
# 使用:在 PowerShell 里 .\startup.ps1

# 切换到脚本所在目录
Set-Location -Path $PSScriptRoot

Write-Host "================================" -ForegroundColor Cyan
Write-Host " 投委会档案 - 后端一键启动" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# ============ 步骤 1:git pull 拉最新代码 ============
Write-Host "[1/4] 拉取最新代码..." -ForegroundColor Yellow
Set-Location ..
git pull origin minimax
if ($LASTEXITCODE -ne 0) {
    Write-Host "git pull 失败!请检查网络或 SSH key" -ForegroundColor Red
    Read-Host "按 Enter 退出"
    exit 1
}
Set-Location backend
Write-Host "✓ 拉取成功" -ForegroundColor Green
Write-Host ""

# ============ 步骤 2:mvn clean package ============
Write-Host "[2/4] 重新构建 JAR(首次 5-10 分钟,慢请耐心)..." -ForegroundColor Yellow
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "mvn 构建失败!请看上方错误" -ForegroundColor Red
    Read-Host "按 Enter 退出"
    exit 1
}
Write-Host "✓ 构建成功" -ForegroundColor Green
Write-Host ""

# ============ 步骤 3:确保日志目录存在 ============
Write-Host "[3/4] 准备日志目录..." -ForegroundColor Yellow
$logDir = "D:\archive\logs"
if (!(Test-Path $logDir)) {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    Write-Host "✓ 创建日志目录:$logDir" -ForegroundColor Green
} else {
    Write-Host "✓ 日志目录已存在" -ForegroundColor Green
}
Write-Host ""

# ============ 步骤 4:启动后端 ============
Write-Host "[4/4] 启动后端..." -ForegroundColor Yellow
Write-Host "  端口:8080" -ForegroundColor Gray
Write-Host "  日志:D:\archive\logs\backend.log" -ForegroundColor Gray
Write-Host "  访问:http://localhost:8080/api/health" -ForegroundColor Gray
Write-Host ""
Write-Host "提示:看到 'Tomcat started on port 8080' 和 'Started ArchiveApplication' 就是跑通了" -ForegroundColor Magenta
Write-Host "      按 Ctrl+C 退出" -ForegroundColor Magenta
Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# 启动
java "-Dfile.encoding=UTF-8" -jar ".\target\archive.jar"
