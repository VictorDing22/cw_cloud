@echo off
REM Start FloatData Streaming System

setlocal enabledelayedexpansion

set KAFKA_HOME=%~dp0kafka_3.6.0
set PROJECT_HOME=%~dp0

echo.
echo ========================================
echo FloatData Streaming System
echo Starting All Components
echo ========================================
echo.

if not exist "%KAFKA_HOME%" (
    echo [ERROR] Kafka not found at: %KAFKA_HOME%
    pause
    exit /b 1
)

echo [1/6] Starting Zookeeper...
start "Zookeeper" cmd /k "cd /d %KAFKA_HOME% && bin\windows\zookeeper-server-start.bat config\zookeeper.properties"
timeout /t 3 /nobreak

echo [2/6] Starting Kafka Broker...
start "Kafka" cmd /k "cd /d %KAFKA_HOME% && bin\windows\kafka-server-start.bat config\server.properties"
timeout /t 3 /nobreak

echo [3/6] Creating Kafka topics...
timeout /t 2 /nobreak
call "%KAFKA_HOME%\bin\windows\kafka-topics.bat" --create --topic acoustic-emission-signal --bootstrap-server localhost:9092 --partitions 4 --replication-factor 1 --if-not-exists >nul 2>&1
call "%KAFKA_HOME%\bin\windows\kafka-topics.bat" --create --topic anomaly-detection-result --bootstrap-server localhost:9092 --partitions 4 --replication-factor 1 --if-not-exists >nul 2>&1
echo [OK] Topics created

echo.
echo [4/6] Starting Netty Server (port 9090)...
start "Netty Server" cmd /k "cd /d %PROJECT_HOME% && java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.server.NettyServer"
timeout /t 2 /nobreak

echo [5/6] Starting Spark Processor...
start "Spark Processor" cmd /k "cd /d %PROJECT_HOME% && java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.spark.StreamProcessor"
timeout /t 3 /nobreak

echo [6/6] Starting Acoustic Emission Client...
start "AE Client" cmd /k "cd /d %PROJECT_HOME% && java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.client.AcousticEmissionClient localhost 9090"

echo.
echo ========================================
echo [SUCCESS] System started!
echo ========================================
echo.
echo Running components:
echo   [1] Zookeeper       - localhost:2181
echo   [2] Kafka Broker    - localhost:9092
echo   [3] Netty Server    - localhost:9090
echo   [4] Spark Processor - Processing streams
echo   [5] AE Client       - Sending signals
echo.
echo To monitor Kafka messages, open a new PowerShell and run:
echo   $KAFKA_HOME = 'e:\Code\floatdata\floatdata-streaming\kafka_3.6.0'
echo   & $KAFKA_HOME\bin\windows\kafka-console-consumer.bat --topic acoustic-emission-signal --bootstrap-server localhost:9092 --from-beginning
echo.
pause
