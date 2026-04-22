# Monitor Kafka messages in real-time

$KAFKA_DIR = "C:\kafka"

if (-not (Test-Path $KAFKA_DIR)) {
    Write-Host "[ERROR] Kafka not found: $KAFKA_DIR" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================"
Write-Host "Kafka Message Monitor"
Write-Host "Topic: acoustic-emission-signal"
Write-Host "========================================"
Write-Host ""
Write-Host "Listening for messages..." -ForegroundColor Yellow
Write-Host ""

# 切换到Kafka目录执行
Push-Location "$KAFKA_DIR\bin\windows"
& ".\kafka-console-consumer.bat" --topic acoustic-emission-signal --bootstrap-server localhost:9092 --from-beginning
Pop-Location
