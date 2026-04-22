# Complete Demo Script - Run the entire system
$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  FloatData Streaming System - Complete Demo" -ForegroundColor White
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

cd $PSScriptRoot

# Step 1: Stop everything
Write-Host "[1/7] Stopping existing processes..." -ForegroundColor Yellow
cmd /c stop.bat 2>$null
Start-Sleep -Seconds 3

# Step 2: Start Zookeeper
Write-Host "[2/7] Starting Zookeeper..." -ForegroundColor Yellow
Start-Process cmd -ArgumentList "/k cd /d C:\kafka && bin\windows\zookeeper-server-start.bat config\zookeeper.properties"
Start-Sleep -Seconds 8

# Step 3: Start Kafka
Write-Host "[3/7] Starting Kafka Broker..." -ForegroundColor Yellow
Start-Process cmd -ArgumentList "/k cd /d C:\kafka && bin\windows\kafka-server-start.bat config\server.properties"
Start-Sleep -Seconds 15

# Step 4: Create topics
Write-Host "[4/7] Creating Kafka topics..." -ForegroundColor Yellow
cd C:\kafka\bin\windows
& ".\kafka-topics.bat" --create --topic acoustic-emission-signal --bootstrap-server localhost:9092 --partitions 4 --replication-factor 1 --if-not-exists 2>$null
& ".\kafka-topics.bat" --create --topic anomaly-detection-result --bootstrap-server localhost:9092 --partitions 4 --replication-factor 1 --if-not-exists 2>$null
Write-Host "      [OK] Topics created" -ForegroundColor Green
cd $PSScriptRoot

# Step 5: Start Netty
Write-Host "[5/7] Starting Netty Server..." -ForegroundColor Yellow
Start-Process cmd -ArgumentList "/k cd /d $PSScriptRoot && java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.server.NettyServer"
Start-Sleep -Seconds 5

# Step 6: Start Spark
Write-Host "[6/7] Starting Spark Processor..." -ForegroundColor Yellow
Start-Process cmd -ArgumentList "/k cd /d $PSScriptRoot && start-spark.bat"
Start-Sleep -Seconds 25

# Step 7: Start data sender
Write-Host "[7/7] Starting data sender..." -ForegroundColor Yellow
Start-Process cmd -ArgumentList "/k cd /d $PSScriptRoot && java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.client.AcousticEmissionClient localhost 9090"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  [SUCCESS] System is running!" -ForegroundColor White
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "5 windows opened:" -ForegroundColor Cyan
Write-Host "  [1] Zookeeper" -ForegroundColor White
Write-Host "  [2] Kafka Broker" -ForegroundColor White
Write-Host "  [3] Netty Server" -ForegroundColor White
Write-Host "  [4] Spark Processor ⭐ Watch for processing logs" -ForegroundColor Yellow
Write-Host "  [5] Data Sender" -ForegroundColor White
Write-Host ""
Write-Host "Wait 30 seconds, then run:" -ForegroundColor Cyan
Write-Host "  python 查看异常.py" -ForegroundColor White
Write-Host ""
Write-Host "To stop:" -ForegroundColor Cyan
Write-Host "  .\stop.bat" -ForegroundColor White
Write-Host ""
