# Start system with VISIBLE windows for debugging
$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  DEBUG MODE - Starting with visible windows" -ForegroundColor White
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

cd $PSScriptRoot

# Stop existing processes
Write-Host "Stopping existing processes..." -ForegroundColor Yellow
cmd /c stop.bat 2>$null
Start-Sleep -Seconds 3

# Start Zookeeper
Write-Host "[1/5] Starting Zookeeper..." -ForegroundColor Yellow
Start-Process cmd -ArgumentList "/k cd /d C:\kafka && bin\windows\zookeeper-server-start.bat config\zookeeper.properties"
Start-Sleep -Seconds 5

# Start Kafka
Write-Host "[2/5] Starting Kafka..." -ForegroundColor Yellow
Start-Process cmd -ArgumentList "/k cd /d C:\kafka && bin\windows\kafka-server-start.bat config\server.properties"
Start-Sleep -Seconds 10

# Create topics
Write-Host "[3/5] Creating Kafka topics..." -ForegroundColor Yellow
cd C:\kafka\bin\windows
& ".\kafka-topics.bat" --create --topic acoustic-emission-signal --bootstrap-server localhost:9092 --partitions 4 --replication-factor 1 --if-not-exists 2>$null
& ".\kafka-topics.bat" --create --topic anomaly-detection-result --bootstrap-server localhost:9092 --partitions 4 --replication-factor 1 --if-not-exists 2>$null
Write-Host "      [OK] Topics created" -ForegroundColor Green

cd $PSScriptRoot

# Start Netty Server
Write-Host "[4/5] Starting Netty Server..." -ForegroundColor Yellow
Write-Host "      WATCH THIS WINDOW for errors!" -ForegroundColor Red
Start-Process cmd -ArgumentList "/k java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.server.NettyServer"
Start-Sleep -Seconds 5

# Start Spark Processor
Write-Host "[5/5] Starting Spark Processor..." -ForegroundColor Yellow  
Write-Host "      WATCH THIS WINDOW for processing logs!" -ForegroundColor Red
Start-Process cmd -ArgumentList "/k java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.spark.StreamProcessor"
Start-Sleep -Seconds 5

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  System started in DEBUG mode!" -ForegroundColor White
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "You should see 4 windows:" -ForegroundColor Cyan
Write-Host "  [1] Zookeeper" -ForegroundColor White
Write-Host "  [2] Kafka Broker" -ForegroundColor White
Write-Host "  [3] Netty Server ⭐ WATCH FOR ERRORS" -ForegroundColor Yellow
Write-Host "  [4] Spark Processor ⭐ WATCH FOR LOGS" -ForegroundColor Yellow
Write-Host ""
Write-Host "Next:" -ForegroundColor Cyan
Write-Host "  1. Start data sender:" -ForegroundColor White
Write-Host "     java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.client.AcousticEmissionClient localhost 9090"
Write-Host ""
Write-Host "  2. In NEW window, check for data:" -ForegroundColor White
Write-Host "     python 查看异常.py"
Write-Host ""
