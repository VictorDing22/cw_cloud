@echo off
REM 高速滤波服务启动脚本 (Windows)
REM 替代 backend.jar

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set PROJECT_DIR=%SCRIPT_DIR%..
set SERVICE_FILE=%PROJECT_DIR%\services\high-speed-filter-service.py
set LOG_DIR=%PROJECT_DIR%\logs
set LOG_FILE=%LOG_DIR%\filter-service.log

REM 创建日志目录
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

REM 默认参数
set ALGORITHM=lms
set WORKERS=4
set MULTITHREAD=
set BROKERS=localhost:9092

REM 解析参数
:parse_args
if "%~1"=="" goto start_service
if "%~1"=="--algorithm" (
    set ALGORITHM=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="-a" (
    set ALGORITHM=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="--workers" (
    set WORKERS=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="-w" (
    set WORKERS=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="--multithread" (
    set MULTITHREAD=--multithread
    shift
    goto parse_args
)
if "%~1"=="-m" (
    set MULTITHREAD=--multithread
    shift
    goto parse_args
)
if "%~1"=="--brokers" (
    set BROKERS=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="-b" (
    set BROKERS=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="--help" goto show_help
if "%~1"=="-h" goto show_help
shift
goto parse_args

:show_help
echo 用法: %~nx0 [选项]
echo.
echo 选项:
echo   -a, --algorithm   滤波算法 (lms/kalman/lowpass/bandpass) [默认: lms]
echo   -w, --workers     工作线程数 [默认: 4]
echo   -m, --multithread 启用多线程模式
echo   -b, --brokers     Kafka brokers [默认: localhost:9092]
echo   -h, --help        显示帮助
exit /b 0

:start_service
echo ============================================================
echo   启动高速滤波服务
echo ============================================================
echo   算法: %ALGORITHM%
echo   线程: %WORKERS%
echo   多线程: %MULTITHREAD%
echo   Brokers: %BROKERS%
echo   日志: %LOG_FILE%
echo ============================================================

REM 检查Python
where python >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 python
    exit /b 1
)

REM 检查依赖
echo 检查依赖...
python -c "import numpy; import kafka; from scipy import signal" 2>nul
if errorlevel 1 (
    echo [错误] 缺少依赖，请运行:
    echo   pip install numpy kafka-python scipy
    exit /b 1
)

REM 启动服务
echo.
echo 启动服务...
start /b python "%SERVICE_FILE%" --algorithm %ALGORITHM% --workers %WORKERS% --brokers %BROKERS% %MULTITHREAD%

echo.
echo [OK] 服务已启动
echo.
echo 查看日志: type %LOG_FILE%
echo 停止服务: 按 Ctrl+C 或关闭窗口

REM 保持窗口打开
pause
