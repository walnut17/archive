# v1.1 数据库迁移 — 在 125 生产机执行
# 用法: PowerShell -ExecutionPolicy Bypass -File D:\projects-online\deploy\sql\run-v11-migrations.ps1
#
# 前提: 已备份 archive_db；勿在生产库跑 init.sql

$ErrorActionPreference = "Continue"

$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if (-not (Test-Path $mysql)) {
    Write-Host "找不到 mysql.exe，请修改脚本里的 `$mysql 路径" -ForegroundColor Red
    exit 1
}

$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$migDir = Join-Path $repoRoot "backend\src\main\resources\db\migration"
$db = "archive_db"
$user = "archive_app"

$files = @(
    "I-RI-22-confidence-3level.sql",
    "I-RI-24-condition-status.sql",
    "I-RI-25-proposal-series.sql",
    "I-RI-26-network-dict-seed.sql",
    "I-RI-28-fact-event-fields.sql",
    "I-RI-31-soft-delete.sql",
    "I-RI-33-optimistic-lock.sql",
    "I-RI-34-rbac-5-roles.sql",
    "I-RI-35-audit-type.sql",
    "I-RI-37-failure-log.sql",
    "I-RI-39-notification.sql",
    "I-RI-43-english-name.sql",
    "I-RI-44-import-batch.sql",
    "I-RI-45-masking.sql"
)

Write-Host "迁移目录: $migDir" -ForegroundColor Gray
Write-Host "数据库:   $db" -ForegroundColor Gray
Write-Host ""

$pass = Read-Host "请输入 MySQL 用户 $user 的密码" -AsSecureString
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($pass))

$failCount = 0
foreach ($f in $files) {
    $path = Join-Path $migDir $f
    if (-not (Test-Path $path)) {
        Write-Host "SKIP (文件不存在): $f" -ForegroundColor Yellow
        $failCount++
        continue
    }
    Write-Host "========== $f ==========" -ForegroundColor Cyan
    $sqlPath = $path -replace '\\', '/'
    & $mysql -u $user "-p$plain" $db -e "source $sqlPath"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "WARN: $f 退出码 $LASTEXITCODE (Duplicate column / already exists 通常可忽略)" -ForegroundColor Yellow
        $failCount++
    }
}

Write-Host ""
Write-Host "========== 修复 notification.is_read ==========" -ForegroundColor Cyan
& $mysql -u $user "-p$plain" $db -e "ALTER TABLE notification CHANGE COLUMN ``read`` is_read TINYINT(1) NOT NULL DEFAULT 0;" 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "跳过列重命名（可能已是 is_read 或表不存在）" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========== 验证关键表 ==========" -ForegroundColor Cyan
& $mysql -u $user "-p$plain" $db -e "SHOW TABLES LIKE 'failure_log'; SHOW TABLES LIKE 'user_role'; SHOW TABLES LIKE 'notification';"

$plain = $null
[GC]::Collect()

Write-Host ""
if ($failCount -eq 0) {
    Write-Host "全部迁移执行完成。请浏览器 Ctrl+F5 刷新。" -ForegroundColor Green
} else {
    Write-Host "完成（有 $failCount 个文件报错，若 Duplicate column 可忽略）。请刷新浏览器验证。" -ForegroundColor Yellow
}
