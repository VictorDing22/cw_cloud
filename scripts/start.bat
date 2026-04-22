@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
color 0A
title Industrial Health Monitoring - Start

pushd "%~dp0.."
set "PROJECT_ROOT=!CD!"
popd
set "BACKEND_ROOT=!PROJECT_ROOT!\yudao-cloud"
set "FRONTEND_ROOT=!PROJECT_ROOT!\yudao-ui-admin-vue3"
set "DOCKER_MW=!PROJECT_ROOT!\scripts\docker-middleware.yml"

cd /d "!PROJECT_ROOT!"

echo.
echo ========================================
echo   CW Cloud - start script
echo   PROJECT_ROOT=!PROJECT_ROOT!
echo ========================================
echo.

:: ---------- delay helper (works without console stdin) ----------
:: ping count N ~= N-1 seconds

:: ========================================
:: Stage 0: Docker middleware
:: ========================================
echo [0/5] Docker middleware...
where docker >nul 2>nul
if !errorlevel! neq 0 (
    echo   [SKIP] docker not in PATH
) else (
    docker info >nul 2>nul
    if !errorlevel! neq 0 (
        echo   [SKIP] Docker engine not running - start Docker Desktop for Nacos+Kafka
    ) else (
        echo   [INFO] docker compose up -d ...
        docker compose version >nul 2>nul
        if !errorlevel! equ 0 (
            docker compose -f "!DOCKER_MW!" up -d
        ) else (
            docker-compose -f "!DOCKER_MW!" up -d
        )
        if !errorlevel! neq 0 (
            echo   [WARN] docker compose failed - check ports 8848 2181 9092
        ) else (
            echo   [OK] docker compose up -d
        )
    )
)
echo.

:: ========================================
:: Stage 1: Check / fallback processes
:: ========================================
echo [1/5] Infrastructure...
echo.

echo   MySQL 3306...
netstat -ano 2>nul | findstr ":3306" | findstr "LISTENING" >nul
if !errorlevel! equ 0 (echo   [OK] MySQL) else (echo   [X] MySQL down)

echo   Redis 6379...
netstat -ano 2>nul | findstr ":6379" | findstr "LISTENING" >nul
if !errorlevel! equ 0 (echo   [OK] Redis) else (echo   [X] Redis down)

echo   Nacos 8848...
netstat -ano 2>nul | findstr ":8848" | findstr "LISTENING" >nul
if !errorlevel! equ 0 (
    echo   [OK] Nacos
) else (
    if exist "!FRONTEND_ROOT!\nacos\bin\startup.cmd" (
        echo   [INFO] Starting local Nacos from frontend folder...
        start "Nacos" /MIN cmd /c "cd /d !FRONTEND_ROOT!\nacos\bin && startup.cmd -m standalone"
        echo   Waiting Nacos...
        ping 127.0.0.1 -n 26 >nul
    ) else (
        echo   [X] Nacos not running - enable Docker or add yudao-ui-admin-vue3\nacos
    )
)

echo   Kafka 9092...
netstat -ano 2>nul | findstr ":9092" | findstr "LISTENING" >nul
if !errorlevel! equ 0 (
    echo   [OK] Kafka
) else (
    if exist "!PROJECT_ROOT!\kafka\bin\windows\kafka-server-start.bat" (
        echo   [INFO] Starting embedded Kafka...
        netstat -ano 2>nul | findstr ":2181" | findstr "LISTENING" >nul
        if not !errorlevel! equ 0 (
            start "Zookeeper" /MIN cmd /c "cd /d !PROJECT_ROOT!\kafka && bin\windows\zookeeper-server-start.bat config\zookeeper.properties"
            ping 127.0.0.1 -n 9 >nul
        )
        start "Kafka" /MIN cmd /c "cd /d !PROJECT_ROOT!\kafka && bin\windows\kafka-server-start.bat config\server.properties"
        ping 127.0.0.1 -n 11 >nul
    ) else (
        echo   [SKIP] No kafka folder / Docker Kafka
    )
)

echo.

:: ========================================
:: Stage 2: Maven build if JARs missing
:: ========================================
echo [2/5] Backend JARs...
set "GATEWAY_JAR=!BACKEND_ROOT!\yudao-gateway\target\yudao-gateway.jar"
set "SYSTEM_JAR=!BACKEND_ROOT!\yudao-module-system\yudao-module-system-server\target\yudao-module-system-server.jar"
set "INFRA_JAR=!BACKEND_ROOT!\yudao-module-infra\yudao-module-infra-server\target\yudao-module-infra-server.jar"

set "NEED_MVN=1"
if exist "!GATEWAY_JAR!" if exist "!SYSTEM_JAR!" if exist "!INFRA_JAR!" set "NEED_MVN=0"

if "!NEED_MVN!"=="0" (
    echo   [OK] JARs present
) else (
    where mvn >nul 2>nul
    if !errorlevel! neq 0 (
        echo   [X] Missing JARs and mvn not in PATH. Install Maven.
    ) else (
        echo   [INFO] mvn package ^(first run slow^)...
        pushd "!BACKEND_ROOT!"
        call mvn -pl yudao-gateway,yudao-module-system/yudao-module-system-server,yudao-module-infra/yudao-module-infra-server -am package -DskipTests
        if errorlevel 1 (
            echo   [X] Maven failed
        )
        popd
    )
)
echo.

:: ========================================
:: Stage 3: Backend processes
:: ========================================
echo [3/5] Backend services...
echo.

if not exist "!GATEWAY_JAR!" (
    echo   [X] Skip Gateway - no JAR
) else (
    netstat -ano 2>nul | findstr ":48080" | findstr "LISTENING" >nul
    if !errorlevel! equ 0 (
        echo   [OK] Gateway 48080
    ) else (
        echo   [INFO] Start Gateway 48080...
        start "Gateway" /MIN java -jar "!GATEWAY_JAR!" --spring.profiles.active=local --server.port=48080
        ping 127.0.0.1 -n 16 >nul
    )
)

if not exist "!SYSTEM_JAR!" (
    echo   [X] Skip System - no JAR
) else (
    netstat -ano 2>nul | findstr ":48081" | findstr "LISTENING" >nul
    if !errorlevel! equ 0 (
        echo   [OK] System 48081
    ) else (
        echo   [INFO] Start System 48081...
        start "System" /MIN java -jar "!SYSTEM_JAR!" --spring.profiles.active=local --server.port=48081
        ping 127.0.0.1 -n 16 >nul
    )
)

if not exist "!INFRA_JAR!" (
    echo   [X] Skip Infra - no JAR
) else (
    netstat -ano 2>nul | findstr ":48082" | findstr "LISTENING" >nul
    if !errorlevel! equ 0 (
        echo   [OK] Infra 48082
    ) else (
        echo   [INFO] Start Infra 48082...
        start "Infra" /MIN java -jar "!INFRA_JAR!" --spring.profiles.active=local --server.port=48082
        ping 127.0.0.1 -n 11 >nul
    )
)

echo.

:: ========================================
:: Stage 4: Data services
:: ========================================
echo [4/5] Data services...
echo.

netstat -ano 2>nul | findstr ":8080" | findstr "LISTENING" >nul
if !errorlevel! equ 0 (
    echo   [OK] Backend filter 8080
) else (
    if exist "!PROJECT_ROOT!\backend.jar" (
        echo   [INFO] Start backend.jar 8080...
        start "Backend-Filter" /MIN java --add-opens java.base/java.lang=ALL-UNNAMED -jar "!PROJECT_ROOT!\backend.jar"
        ping 127.0.0.1 -n 6 >nul
    ) else (
        echo   [SKIP] backend.jar
    )
)

netstat -ano 2>nul | findstr ":3002" | findstr "LISTENING" >nul
if !errorlevel! equ 0 (
    echo   [OK] TDMS API 3002
) else (
    echo   [INFO] Start TDMS API...
    start "TDMS-API" /MIN cmd /c "cd /d !PROJECT_ROOT!\services && node tdms-api-server.js"
    ping 127.0.0.1 -n 3 >nul
)

netstat -ano 2>nul | findstr ":8081" | findstr "LISTENING" >nul
if !errorlevel! equ 0 (
    echo   [OK] WebSocket 8081
) else (
    echo   [INFO] Start WebSocket bridge...
    start "WebSocket-Bridge" /MIN cmd /c "cd /d !PROJECT_ROOT!\services && node websocket-bridge.js"
    ping 127.0.0.1 -n 3 >nul
)

echo.

:: ========================================
:: Stage 5: Frontend
:: ========================================
echo [5/5] Frontend...
echo.

where npm >nul 2>nul
if !errorlevel! neq 0 (
    echo   [X] npm not in PATH
) else (
    echo   [INFO] npm run dev ^(see VITE_PORT in .env^)...
    start "Vue3-Frontend" cmd /c "cd /d !FRONTEND_ROOT! && npm run dev"
    ping 127.0.0.1 -n 6 >nul
)

echo.
echo ========================================
echo   Done
echo ========================================
echo   UI:    http://localhost:3000
echo   GW:    http://localhost:48080
echo   Login: admin / admin123
echo   Nacos: http://localhost:8848/nacos
echo.

echo [Status]
call "!PROJECT_ROOT!\scripts\status.bat"

endlocal
exit /b 0
