@echo off
REM v1.1 数据库迁移 — 双击或 cmd 运行
REM 用法: deploy\sql\run-v11-migrations.bat

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-v11-migrations.ps1"
pause
