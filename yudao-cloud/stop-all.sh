#!/bin/bash

# 工业故障监测平台 - 完整停止脚本（包含IoT服务）

echo "========================================="
echo " 停止所有服务"
echo "========================================="

# 停止基础服务
echo "正在停止基础服务..."
bash quick-stop.sh 2>/dev/null

# 停止IoT服务
if [ -f "iot.pid" ]; then
    IOT_PID=$(cat iot.pid)
    if ps -p $IOT_PID > /dev/null 2>&1; then
        echo "正在停止IoT服务 (PID: $IOT_PID)..."
        kill $IOT_PID
        sleep 2
        
        if ps -p $IOT_PID > /dev/null 2>&1; then
            echo "强制停止IoT服务..."
            kill -9 $IOT_PID
        fi
        echo "   ✓ IoT服务已停止"
    else
        echo "   IoT服务未运行"
    fi
    rm -f iot.pid
else
    echo "   未找到IoT服务PID文件"
fi

echo "========================================="
echo " 所有服务已停止"
echo "========================================="

