@echo off
REM ==========================================================
REM 投委会档案管理系统 - 注册 Windows 服务
REM 用法:右键"以管理员身份运行"
REM 假设你已经把 build-backend.bat 跑过,backend.jar 已经在 D:\archive\apps\backend\
REM ==========================================================

setlocal

set BACKEND_DIR=D:\archive\apps\backend
set CADDY_DIR=D:\archive\apps\caddy
set QA_AGENT_DIR=D:\projects-online\qa-agent
set WINSW_DIR=D:\archive\apps\backend
set WINSW_EXE=%WINSW_DIR%\archive-backend-service.exe

echo === 注册投委会档案 - Windows 服务 ===
echo.

REM 1. 检查 WinSW 可执行文件
if not exist "%WINSW_EXE%" (
    echo [提示] 找不到 %WINSW_EXE%
    echo 请从 https://github.com/winsw/winsw/releases 下载 WinSW-x64.exe
    echo 改名为 archive-backend-service.exe,放到 %WINSW_DIR%\
    echo 同时确保 backend.xml 在同目录
    echo.
    pause
    exit /b 1
)

REM 2. 注册后端服务
echo [1/3] 注册 archive-backend 服务 ...
cd /d "%WINSW_DIR%"
"%WINSW_EXE%" install
if %errorlevel% neq 0 (
    echo [错误] 后端服务注册失败
    pause
    exit /b 1
)

REM 3. 设置为自动启动
sc config archive-backend start= auto
net start archive-backend
echo.

REM 4. 注册 qa-agent 服务（Python 微服务）
if exist "%QA_AGENT_DIR%\.venv\Scripts\python.exe" (
    echo [2/3] 注册 qa-agent 服务 ...
    copy /Y "..\deploy\winsw\qa-agent.xml" "%QA_AGENT_DIR%\"
    cd /d "%QA_AGENT_DIR%"
    qa-agent-service.exe install
    sc config qa-agent start= auto
    net start qa-agent
) else (
    echo [2/3] 跳过 qa-agent 服务注册(未找到 Python .venv)
    echo 手动启动: cd qa-agent ^&^& .venv\Scripts\uvicorn app.main:app --host 127.0.0.1 --port 8001
)
echo.

REM 5. Caddy 服务(可选,内网 HTTP 阶段可暂时不开)
if exist "%CADDY_DIR%\caddy.exe" (
    echo [3/3] 注册 caddy 服务 ...
    cd /d "%CADDY_DIR%"
    caddy.exe service install --config Caddyfile
    sc config caddy start= auto
    net start caddy
) else (
    echo [3/3] 跳过 caddy 服务注册(未找到 caddy.exe)
    echo 直接运行:caddy.exe run --config Caddyfile
)

echo.
echo === 完成 ===
echo 服务列表:
sc query archive-backend
sc query qa-agent
if exist "%CADDY_DIR%\caddy.exe" sc query caddy
echo.
echo 卸载服务:
echo   sc stop archive-backend ^&^& sc delete archive-backend
echo   sc stop qa-agent ^&^& sc delete qa-agent
echo.
pause
