#!/bin/bash
# 启动远程滤波服务 - 调用盛老师的滤波微服务

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
LOG_DIR="$PROJECT_DIR/logs"

mkdir -p "$LOG_DIR"

# 默认使用 Kalman 算法
ALGORITHM=${1:-kalman}

echo "=========================================="
echo "  启动远程滤波服务"
echo "=========================================="
echo "  算法: $ALGORITHM"
echo "  日志: $LOG_DIR/remote-filter.log"
echo "=========================================="

# 检查依赖
pip3 show aiohttp > /dev/null 2>&1 || pip3 install aiohttp

# 停止旧进程
pkill -f "remote-filter-service.py" 2>/dev/null

# 启动服务
cd "$PROJECT_DIR"
nohup python3 services/remote-filter-service.py \
    --algorithm "$ALGORITHM" \
    --input-topic sample-input \
    --output-topic sample-output \
    > "$LOG_DIR/remote-filter.log" 2>&1 &

echo "[OK] 服务已启动 (PID: $!)"
echo ""
echo "查看日志: tail -f $LOG_DIR/remote-filter.log"
echo "停止服务: pkill -f remote-filter-service.py"
