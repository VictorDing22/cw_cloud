# 查看异常结果
$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  查看异常结果 (实时)" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "正在读取异常检测结果..." -ForegroundColor Yellow
Write-Host "按 Ctrl+C 停止" -ForegroundColor Gray
Write-Host ""

# 切换到Kafka目录
Set-Location "C:\kafka\bin\windows"

# 运行Kafka消费者
& ".\kafka-console-consumer.bat" --bootstrap-server localhost:9092 --topic anomaly-detection-result --from-beginning
