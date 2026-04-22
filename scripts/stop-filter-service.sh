#!/bin/bash
# 停止高速滤波服务

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
LOG_DIR="$PROJECT_DIR/logs"
PID_FILE="$LOG_DIR/filter-service.pid"

echo "停止高速滤波服务..."

# 检查PID文件
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "正在停止服务 (PID: $PID)..."
        kill -TERM "$PID"
        
        # 等待进程结束
        for i in {1..10}; do
            if ! ps -p "$PID" > /dev/null 2>&1; then
                break
            fi
            sleep 0.5
        done
        
        # 强制终止
        if ps -p "$PID" > /dev/null 2>&1; then
            echo "强制终止..."
            kill -9 "$PID"
        fi
        
        echo "[OK] 服务已停止"
    else
        echo "[信息] 服务未运行"
    fi
    
    rm -f "$PID_FILE"
else
    echo "[信息] 未找到PID文件"
    
    # 尝试查找进程
    PIDS=$(pgrep -f "high-speed-filter-service.py")
    if [ -n "$PIDS" ]; then
        echo "发现运行中的服务进程: $PIDS"
        echo "正在停止..."
        kill -TERM $PIDS 2>/dev/null
        sleep 1
        kill -9 $PIDS 2>/dev/null
        echo "[OK] 已停止"
    fi
fi
