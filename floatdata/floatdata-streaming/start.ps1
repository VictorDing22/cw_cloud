# FloatData Streaming System - Startup Script
# Usage: .\start.ps1 [options]
# Options:
#   -DataSource <type>   : simulator (default) | tdms | wallpainting
#   -ShowWindows         : Show component windows (default: hidden)

param(
    [string]$DataSource = "simulator",
    [switch]$ShowWindows = $false
)

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  FloatData Streaming System" -ForegroundColor White
Write-Host "  Starting Components..." -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$KAFKA_HOME = "C:\kafka"
$PROJECT_HOME = $PSScriptRoot
$WindowStyle = if ($ShowWindows) { "Normal" } else { "Hidden" }

# Check prerequisites
if (-not (Test-Path $KAFKA_HOME)) {
    Write-Host "[ERROR] Kafka not found at: $KAFKA_HOME" -ForegroundColor Red
    Write-Host "Please download Kafka 3.6.0 and extract to kafka_3.6.0/" -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path "$PROJECT_HOME\target\floatdata-streaming-1.0.0.jar")) {
    Write-Host "[ERROR] JAR file not found. Please build project first:" -ForegroundColor Red
    Write-Host "  mvn clean package -DskipTests" -ForegroundColor Yellow
    exit 1
}

# Step 1: Start Zookeeper
Write-Host "[1/5] Starting Zookeeper..." -ForegroundColor Yellow
if ($ShowWindows) {
    Start-Process cmd -ArgumentList "/k cd /d $KAFKA_HOME && bin\windows\zookeeper-server-start.bat config\zookeeper.properties"
} else {
    Start-Process -FilePath "$KAFKA_HOME\bin\windows\zookeeper-server-start.bat" -ArgumentList "$KAFKA_HOME\config\zookeeper.properties" -WindowStyle Hidden
}
Start-Sleep -Seconds 3

# Step 2: Start Kafka Broker
Write-Host "[2/5] Starting Kafka Broker..." -ForegroundColor Yellow
if ($ShowWindows) {
    Start-Process cmd -ArgumentList "/k cd /d $KAFKA_HOME && bin\windows\kafka-server-start.bat config\server.properties"
} else {
    Start-Process -FilePath "$KAFKA_HOME\bin\windows\kafka-server-start.bat" -ArgumentList "$KAFKA_HOME\config\server.properties" -WindowStyle Hidden
}
Start-Sleep -Seconds 3

# Step 3: Create Kafka topics
Write-Host "[3/5] Creating Kafka topics..." -ForegroundColor Yellow
Start-Sleep -Seconds 2
& "$KAFKA_HOME\bin\windows\kafka-topics.bat" --create --topic acoustic-emission-signal --bootstrap-server localhost:9092 --partitions 4 --replication-factor 1 --if-not-exists 2>$null
& "$KAFKA_HOME\bin\windows\kafka-topics.bat" --create --topic anomaly-detection-result --bootstrap-server localhost:9092 --partitions 4 --replication-factor 1 --if-not-exists 2>$null
Write-Host "      [OK] Topics created" -ForegroundColor Green

# Step 4: Start Netty Server
Write-Host ""
Write-Host "[4/5] Starting Netty Server (port 9090)..." -ForegroundColor Yellow
if ($ShowWindows) {
    Start-Process cmd -ArgumentList "/k cd /d $PROJECT_HOME && java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.server.NettyServer"
} else {
    Start-Process -FilePath "java" -ArgumentList "-cp target\floatdata-streaming-1.0.0.jar com.floatdata.server.NettyServer" -WindowStyle Hidden -WorkingDirectory $PROJECT_HOME
}
Start-Sleep -Seconds 3

# Step 5: Start Spark Processor
Write-Host "[5/5] Starting Spark Processor..." -ForegroundColor Yellow
if ($ShowWindows) {
    Start-Process cmd -ArgumentList "/k cd /d $PROJECT_HOME && java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.spark.StreamProcessor"
} else {
    Start-Process -FilePath "java" -ArgumentList "-cp target\floatdata-streaming-1.0.0.jar com.floatdata.spark.StreamProcessor" -WindowStyle Hidden -WorkingDirectory $PROJECT_HOME
}
Start-Sleep -Seconds 3

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  [SUCCESS] System started!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Components running:" -ForegroundColor Cyan
Write-Host "  [1] Zookeeper       - localhost:2181"
Write-Host "  [2] Kafka Broker    - localhost:9092"
Write-Host "  [3] Netty Server    - localhost:9090"
Write-Host "  [4] Spark Processor - Processing streams"
Write-Host ""

# Start data source based on parameter
switch ($DataSource.ToLower()) {
    "tdms" {
        Write-Host "Starting TDMS data reader..." -ForegroundColor Yellow
        Write-Host "Run in new terminal: python tdms-reader.py" -ForegroundColor Cyan
    }
    "wallpainting" {
        Write-Host "Starting wallpainting data reader..." -ForegroundColor Yellow
        Write-Host "Run in new terminal: python wallpainting-reader.py" -ForegroundColor Cyan
    }
    "simulator" {
        Write-Host "Starting signal simulator..." -ForegroundColor Yellow
        if ($ShowWindows) {
            Start-Process cmd -ArgumentList "/k cd /d $PROJECT_HOME && java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.client.AcousticEmissionClient localhost 9090"
        } else {
            Write-Host "Run in new terminal: java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.client.AcousticEmissionClient localhost 9090" -ForegroundColor Cyan
        }
    }
    default {
        Write-Host "Unknown data source: $DataSource" -ForegroundColor Red
        Write-Host "Valid options: simulator, tdms, wallpainting" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Monitor messages: .\monitor.ps1"
Write-Host "  2. Stop system: .\stop.bat"
Write-Host "  3. Test performance: .\test-performance.ps1"
Write-Host ""
