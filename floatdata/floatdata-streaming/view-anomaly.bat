@echo off
echo.
echo ========================================
echo   查看异常结果 (Kafka Topic)
echo ========================================
echo.
echo 正在读取异常检测结果...
echo 按 Ctrl+C 停止
echo.

kafka_3.6.0\bin\windows\kafka-console-consumer.bat --bootstrap-server localhost:9092 --topic anomaly-detection-result --from-beginning
