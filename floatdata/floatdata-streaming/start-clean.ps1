# Clean startup script - Minimal windows, clean Kafka data
$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  FloatData Streaming System - Clean Start" -ForegroundColor White
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

cd $PSScriptRoot

# Step 1: Stop all existing processes
Write-Host "[1/7] Stopping existing processes..." -ForegroundColor Yellow
taskkill /F /IM java.exe 2>&1 | Out-Null
Start-Sleep -Seconds 3

# Step 2: Clean Kafka and Zookeeper data to avoid Cluster ID conflicts
Write-Host "[2/7] Cleaning Kafka and Zookeeper data..." -ForegroundColor Yellow
Remove-Item -Path "C:\kafka\logs" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "C:\kafka\data" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "C:\tmp\zookeeper" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "C:\kafka\zookeeper" -Recurse -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2
Write-Host "      [OK] Data cleaned" -ForegroundColor Green

# Step 3: Start Zookeeper (minimized window)
Write-Host "[3/7] Starting Zookeeper..." -ForegroundColor Yellow
$zkProcess = Start-Process -FilePath "C:\kafka\bin\windows\zookeeper-server-start.bat" `
    -ArgumentList "C:\kafka\config\zookeeper.properties" `
    -WindowStyle Minimized `
    -PassThru
Start-Sleep -Seconds 10
Write-Host "      [OK] Zookeeper started (PID: $($zkProcess.Id))" -ForegroundColor Green

# Step 4: Start Kafka Broker (minimized window)
Write-Host "[4/7] Starting Kafka Broker..." -ForegroundColor Yellow
$kafkaProcess = Start-Process -FilePath "C:\kafka\bin\windows\kafka-server-start.bat" `
    -ArgumentList "C:\kafka\config\server.properties" `
    -WindowStyle Minimized `
    -PassThru

# Wait and verify Kafka is running (with retries)
$maxAttempts = 6
$attempt = 0
$kafkaRunning = $false

while (-not $kafkaRunning -and $attempt -lt $maxAttempts) {
    Start-Sleep -Seconds 5
    $attempt++
    $kafkaRunning = Test-NetConnection localhost -Port 9092 -WarningAction SilentlyContinue -InformationLevel Quiet
    if (-not $kafkaRunning) {
        Write-Host "      Waiting for Kafka... ($attempt/$maxAttempts)" -ForegroundColor Yellow
    }
}

if ($kafkaRunning) {
    Write-Host "      [OK] Kafka Broker started (PID: $($kafkaProcess.Id))" -ForegroundColor Green
} else {
    Write-Host "      [ERROR] Kafka failed to start! Check C:\kafka\logs\server.log" -ForegroundColor Red
    Write-Host "      Try manually: C:\kafka\bin\windows\kafka-server-start.bat C:\kafka\config\server.properties" -ForegroundColor Yellow
    exit 1
}

# Step 5: Create Kafka topics
Write-Host "[5/7] Creating Kafka topics..." -ForegroundColor Yellow
cd C:\kafka\bin\windows
& ".\kafka-topics.bat" --create --topic acoustic-emission-signal `
    --bootstrap-server localhost:9092 --partitions 4 --replication-factor 1 --if-not-exists 2>&1 | Out-Null
& ".\kafka-topics.bat" --create --topic anomaly-detection-result `
    --bootstrap-server localhost:9092 --partitions 4 --replication-factor 1 --if-not-exists 2>&1 | Out-Null
cd $PSScriptRoot
Write-Host "      [OK] Topics created" -ForegroundColor Green

# Step 6: Start Netty Server (minimized window)
Write-Host "[6/7] Starting Netty Server..." -ForegroundColor Yellow
$nettyProcess = Start-Process -FilePath "java" `
    -ArgumentList "-cp target\floatdata-streaming-1.0.0.jar com.floatdata.server.NettyServer" `
    -WorkingDirectory $PSScriptRoot `
    -WindowStyle Minimized `
    -PassThru
Start-Sleep -Seconds 5

$nettyRunning = Test-NetConnection localhost -Port 9090 -WarningAction SilentlyContinue -InformationLevel Quiet
if ($nettyRunning) {
    Write-Host "      [OK] Netty Server started (PID: $($nettyProcess.Id))" -ForegroundColor Green
} else {
    Write-Host "      [ERROR] Netty failed to start!" -ForegroundColor Red
}

# Step 7: Start Spark Processor (minimized window with Java 17+ compatibility)
Write-Host "[7/7] Starting Spark Processor..." -ForegroundColor Yellow
$sparkProcess = Start-Process -FilePath "cmd" `
    -ArgumentList "/c cd /d $PSScriptRoot && start-spark.bat" `
    -WindowStyle Minimized `
    -PassThru
Start-Sleep -Seconds 5
Write-Host "      [OK] Spark Processor starting (PID: $($sparkProcess.Id))" -ForegroundColor Green

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  [SUCCESS] System Started!" -ForegroundColor White
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "All components running in background (minimized windows):" -ForegroundColor Cyan
Write-Host "  [1] Zookeeper    - PID $($zkProcess.Id)" -ForegroundColor White
Write-Host "  [2] Kafka Broker - PID $($kafkaProcess.Id)" -ForegroundColor White
Write-Host "  [3] Netty Server - PID $($nettyProcess.Id)" -ForegroundColor White
Write-Host "  [4] Spark        - PID $($sparkProcess.Id)" -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Start data sender (in new window):" -ForegroundColor White
Write-Host "     java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.client.AcousticEmissionClient localhost 9090"
Write-Host ""
Write-Host "  2. Wait 30 seconds, then view anomaly results:" -ForegroundColor White
Write-Host "     python 查看异常.py" -ForegroundColor White
Write-Host ""
Write-Host "To stop all:" -ForegroundColor Cyan
Write-Host "  .\stop.bat" -ForegroundColor White
Write-Host ""
