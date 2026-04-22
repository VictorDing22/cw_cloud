# Check if data is flowing through Kafka topics
Write-Host "Checking Kafka topics for data..." -ForegroundColor Cyan

Write-Host "`n[1] Checking INPUT topic (acoustic-emission-signal):" -ForegroundColor Yellow
Set-Location "C:\kafka\bin\windows"
$inputData = & ".\kafka-console-consumer.bat" --bootstrap-server localhost:9092 --topic acoustic-emission-signal --from-beginning --max-messages 3 --timeout-ms 3000 2>$null
if ($inputData) {
    Write-Host "✓ INPUT topic has data!" -ForegroundColor Green
    Write-Host "Sample: $($inputData[0].Substring(0, [Math]::Min(100, $inputData[0].Length)))..." -ForegroundColor Gray
} else {
    Write-Host "✗ INPUT topic is EMPTY - Netty is NOT sending data to Kafka!" -ForegroundColor Red
}

Write-Host "`n[2] Checking OUTPUT topic (anomaly-detection-result):" -ForegroundColor Yellow
$outputData = & ".\kafka-console-consumer.bat" --bootstrap-server localhost:9092 --topic anomaly-detection-result --from-beginning --max-messages 3 --timeout-ms 3000 2>$null
if ($outputData) {
    Write-Host "✓ OUTPUT topic has data!" -ForegroundColor Green
    Write-Host "Sample: $($outputData[0].Substring(0, [Math]::Min(100, $outputData[0].Length)))..." -ForegroundColor Gray
} else {
    Write-Host "✗ OUTPUT topic is EMPTY - Spark is NOT processing data!" -ForegroundColor Red
}

Write-Host ""
