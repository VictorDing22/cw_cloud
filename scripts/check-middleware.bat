@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

set "PROJECT_ROOT=%~dp0.."
set "DOCKER_COMPOSE_SIMPLE=%PROJECT_ROOT%\yudao-cloud\docker-compose-simple.yml"
set "KAFKA_DOC=%PROJECT_ROOT%\docs\Kafka安装指南.md"

title 工业健康监测平台 - 中间件检查

:MENU
echo.
echo ========================================
echo   中间件检查 / 可选安装（需确认）
echo ========================================
echo   工程路径: %PROJECT_ROOT%
echo.
echo   [1] 仅检查状态（推荐先执行）
echo   [2] 用 Docker 仅启动 Nacos（微服务模式需要；会提示确认）
echo   [3] 打开 Kafka 安装说明文档
echo   [0] 退出
echo ========================================
set /p "CHOICE=请选择 [0-3]: "

if "%CHOICE%"=="1" goto CHECK_ONLY
if "%CHOICE%"=="2" goto INSTALL_NACOS
if "%CHOICE%"=="3" goto OPEN_KAFKA_DOC
if "%CHOICE%"=="0" goto END
echo 无效选择，请重试。
goto MENU

:CHECK_ONLY
call :DO_CHECK
echo.
pause
goto MENU

:DO_CHECK
echo.
echo ---------- 本工程常见中间件与端口 ----------
echo   MySQL        3306   业务库（默认库名 ruoyi-vue-pro）
echo   Redis        6379   缓存 / 会话 / 验证码等
echo   Nacos        8848   注册中心（仅「Gateway+多微服务」启动方式需要）
echo   Zookeeper    2181   Kafka 旧版内置依赖（项目内 kafka 目录脚本使用）
echo   Kafka        9092   实时数据流、backend.jar、TDMS 生产者等
echo ------------------------------------------
echo.

call :CHK_PORT 3306 "MySQL"
call :CHK_PORT 6379 "Redis"
call :CHK_PORT 8848 "Nacos"
call :CHK_PORT 2181 "Zookeeper"
call :CHK_PORT 9092 "Kafka"

echo.
echo ---------- 与启动方式的对应关系 ----------
echo   方式 A - yudao-server 单体（mvn / IDEA 运行，Nacos 在配置里关闭）:
echo            一般需要: MySQL + Redis
echo   方式 B - scripts\start.bat 多 jar（Gateway+System+Infra）:
echo            一般需要: MySQL + Redis + Nacos
echo   方式 C - 实时监控 / Kafka 数据流 / backend.jar:
echo            在 A 或 B 基础上还需要: Zookeeper + Kafka（或 KRaft 单节点，视你的安装方式）
echo ------------------------------------------
echo.

if exist "%PROJECT_ROOT%\kafka\bin\windows\kafka-server-start.bat" (
    echo [OK] 项目下已存在 kafka 目录，可用 start.bat 或手动启动 ZK+Kafka。
) else (
    echo [提示] 未找到 %PROJECT_ROOT%\kafka\bin\windows\kafka-server-start.bat
    echo        可按 docs\Kafka安装指南.md 解压 Kafka 到工程根目录的 kafka 文件夹。
)

if exist "%PROJECT_ROOT%\yudao-ui-admin-vue3\nacos\bin\startup.cmd" (
    echo [OK] 前端目录下存在 Nacos 解压包，start.bat 会尝试从此处启动。
) else (
    echo [提示] 未找到 yudao-ui-admin-vue3\nacos\ — start.bat 无法自动拉起 Nacos。
    echo        可选: 菜单 [2] 用 Docker 启动 Nacos，或自行下载 Nacos 并放到该路径。
)
exit /b 0

:CHK_PORT
set "P=%~1"
set "NAME=%~2"
netstat -ano 2>nul | findstr ":%P% " | findstr "LISTENING" >nul
if !errorlevel!==0 (
    echo   [OK] !NAME!  端口 !P!  正在监听
) else (
    echo   [--] !NAME!  端口 !P!  未监听
)
exit /b 0

:INSTALL_NACOS
echo.
echo 【确认】将尝试用 Docker 启动独立 Nacos（standalone），映射端口 8848 / 9848。
echo          请确保本机已安装 Docker Desktop 且引擎已运行。
echo          若 8848 已被占用，请先停止占用程序或其它 Nacos 容器。
echo.
set /p "YN=确认执行？(Y/N): "
if /I not "%YN%"=="Y" (
    echo 已取消。
    pause
    goto MENU
)

where docker >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 docker 命令，请先安装 Docker Desktop 并加入 PATH。
    pause
    goto MENU
)

docker info >nul 2>nul
if errorlevel 1 (
    echo [错误] Docker 引擎未运行，请启动 Docker Desktop 后重试。
    pause
    goto MENU
)

docker ps -a --format "{{.Names}}" 2>nul | findstr /I "^cw-nacos$" >nul
if not errorlevel 1 (
    echo [信息] 已存在名为 cw-nacos 的容器，尝试启动...
    docker start cw-nacos
    if errorlevel 1 (
        echo [错误] 启动失败，请手动: docker logs cw-nacos
    ) else (
        echo [OK] Nacos 已启动。控制台: http://localhost:8848/nacos  默认 nacos/nacos
    )
    pause
    goto MENU
)

echo [信息] 创建并启动容器 cw-nacos ...
docker run -d --name cw-nacos -p 8848:8848 -p 9848:9848 -e MODE=standalone -e JVM_XMS=256m -e JVM_XMX=512m nacos/nacos-server:v2.2.3
if errorlevel 1 (
    echo [错误] docker run 失败，请检查网络（需拉取镜像）或端口占用。
) else (
    echo [OK] Nacos 已启动。约等待 30~60 秒后访问: http://localhost:8848/nacos
    echo      默认账号密码: nacos / nacos
)
pause
goto MENU

:OPEN_KAFKA_DOC
if exist "%KAFKA_DOC%" (
    start "" "%KAFKA_DOC%"
) else (
    echo 未找到: %KAFKA_DOC%
)
pause
goto MENU

:END
endlocal
exit /b 0
