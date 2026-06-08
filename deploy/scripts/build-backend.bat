@echo off
REM ==========================================================
REM 投委会档案管理系统 - 后端构建脚本(Windows)
REM 用法:右键"以管理员身份运行" 或 cmd: build-backend.bat
REM 产物: backend\target\archive.jar
REM ==========================================================

setlocal

set SCRIPT_DIR=%~dp0
set BACKEND_DIR=%SCRIPT_DIR%..\..\backend
set OUTPUT_DIR=D:\archive\apps\backend

echo === 投委会档案管理系统 - 后端构建 ===
echo 后端源码: %BACKEND_DIR%
echo 输出目录: %OUTPUT_DIR%
echo.

if not exist "%BACKEND_DIR%\pom.xml" (
    echo [错误] 找不到 pom.xml,请确认仓库目录结构正确
    pause
    exit /b 1
)

REM 检查 Maven
where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 mvn,请先安装 Maven 3.8+,并配置到 PATH
    pause
    exit /b 1
)

REM 检查 Java
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 java,请先安装 JDK 17
    pause
    exit /b 1
)

REM 编译打包
cd /d "%BACKEND_DIR%"
echo [1/2] mvn clean package -DskipTests ...
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo [错误] Maven 编译失败
    pause
    exit /b 1
)

REM 拷贝到输出目录
echo [2/2] 拷贝到 %OUTPUT_DIR% ...
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
copy /Y "%BACKEND_DIR%\target\archive.jar" "%OUTPUT_DIR%\archive.jar" >nul

echo.
echo === 完成 ===
echo JAR 文件: %OUTPUT_DIR%\archive.jar
echo.
echo 接下来:
echo   1. 复制 deploy\winsw\backend.xml 和 WinSW 可执行文件到 D:\archive\apps\backend\
echo   2. 复制 deploy\caddy\Caddyfile 到 D:\archive\apps\caddy\
echo   3. 运行 deploy\scripts\register-services.bat 注册 Windows 服务
echo.
pause
