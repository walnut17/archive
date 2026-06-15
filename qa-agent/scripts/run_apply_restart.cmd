@echo off
REM Wrapper for apply_update_and_restart.ps1 — cmd 比 detached PowerShell 更可靠
setlocal
set "LOGDIR=%~1"
set "REPROOT=%~2"
set "CONFIG=%~3"
set "SCRIPT=%~dp0apply_update_and_restart.ps1"

if not exist "%LOGDIR%" mkdir "%LOGDIR%" 2>nul
echo %DATE% %TIME% [cmd] wrapper start LOGDIR=%LOGDIR%>>"%LOGDIR%\apply_update_restart.log"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -RepoRoot "%REPROOT%" -LogDir "%LOGDIR%" -ConfigJson "%CONFIG%"
set "EC=%ERRORLEVEL%"

echo %DATE% %TIME% [cmd] wrapper end exit=%EC%>>"%LOGDIR%\apply_update_restart.log"
exit /b %EC%
