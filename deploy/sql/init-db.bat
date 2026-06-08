@echo off
REM ==========================================================
REM 投委会档案管理系统 - 数据库初始化脚本(Windows)
REM 用法:右键"以管理员身份运行" 或 PowerShell 管理员: ./init-db.bat
REM ==========================================================

setlocal

set MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.0\bin
set MYSQL_USER=root
set MYSQL_PASS=
set DB_NAME=archive_db
set SQL_FILE=%~dp0..\..\backend\src\main\resources\db\init.sql

echo === 投委会档案管理系统 - 数据库初始化 ===
echo.
echo MySQL 路径: %MYSQL_BIN%
echo SQL 文件:  %SQL_FILE%
echo.

if not exist "%MYSQL_BIN%\mysql.exe" (
    echo [错误] 找不到 mysql.exe,请修改 MYSQL_BIN 路径
    pause
    exit /b 1
)

if not exist "%SQL_FILE%" (
    echo [错误] 找不到 init.sql,请确认路径
    pause
    exit /b 1
)

if "%MYSQL_PASS%"=="" (
    "%MYSQL_BIN%\mysql.exe" -u %MYSQL_USER% -p < "%SQL_FILE%"
) else (
    "%MYSQL_BIN%\mysql.exe" -u %MYSQL_USER% -p%MYSQL_PASS% < "%SQL_FILE%"
)

if %errorlevel% neq 0 (
    echo.
    echo [错误] 数据库初始化失败
    pause
    exit /b 1
)

echo.
echo === 完成 ===
echo 默认 admin 账号:admin / admin123
echo 请尽快修改默认密码
echo.
pause
