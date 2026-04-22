@echo off
chcp 65001 >nul
echo.
echo ============================================================
echo   完整演示：启动系统并实时查看异常
echo ============================================================
echo.
echo 正在启动所有组件...
echo.

cd /d %~dp0

REM 启动Zookeeper
echo [1/6] 启动 Zookeeper...
start "Zookeeper" cmd /k "cd kafka_3.6.0 && bin\windows\zookeeper-server-start.bat config\zookeeper.properties"
timeout /t 8 /nobreak >nul

REM 启动Kafka
echo [2/6] 启动 Kafka Broker...
start "Kafka" cmd /k "cd kafka_3.6.0 && bin\windows\kafka-server-start.bat config\server.properties"
timeout /t 15 /nobreak >nul

REM 启动Netty
echo [3/6] 启动 Netty Server...
start "Netty" cmd /k "java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.server.NettyServer"
timeout /t 3 /nobreak >nul

REM 启动Spark
echo [4/6] 启动 Spark Processor...
start "Spark" cmd /k "java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.spark.StreamProcessor"
timeout /t 3 /nobreak >nul

REM 启动数据发送
echo [5/6] 启动数据发送器...
start "DataSender" cmd /k "java -cp target\floatdata-streaming-1.0.0.jar com.floatdata.client.AcousticEmissionClient localhost 9090"
timeout /t 3 /nobreak >nul

REM 启动Python查看异常
echo [6/6] 启动异常结果查看器...
timeout /t 5 /nobreak >nul
start "查看异常" cmd /k "python 查看异常.py"

echo.
echo ============================================================
echo   所有组件已启动！
echo ============================================================
echo.
echo 现在会打开多个窗口：
echo   - Zookeeper（后台）
echo   - Kafka（后台）  
echo   - Netty Server（TCP接收）
echo   - Spark Processor（处理+异常检测） ⭐ 看这个窗口
echo   - Data Sender（发送数据）
echo   - 查看异常（实时异常结果） ⭐⭐ 重点看这个
echo.
echo 关闭所有：运行 stop.bat
echo.
pause
