#!/bin/bash
# 启动分布式滤波服务 - 使用盛老师的远程滤波API

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"
LOG_DIR="$PROJECT_DIR/logs"

mkdir -p "$LOG_DIR"

# 默认参数
NUM_WORKERS=${1:-3}
ALGORITHM=${2:-kalman}

echo "=========================================="
echo "  启动分布式滤波服务 (远程API)"
echo "=========================================="
echo "  Workers: $NUM_WORKERS"
echo "  算法: $ALGORITHM"
echo "  API: http://49.235.44.231:800x"
echo "=========================================="

# 检查依赖
pip3 show aiohttp > /dev/null 2>&1 || pip3 install aiohttp

# 停止旧进程
echo "[1/4] 停止旧进程..."
pkill -f "dispatcher.py" 2>/dev/null
pkill -f "filter_worker.py" 2>/dev/null
pkill -f "aggregator.py" 2>/dev/null
sleep 2

# 启动 Dispatcher
echo "[2/4] 启动 Dispatcher..."
cd "$SCRIPT_DIR"
nohup python3 dispatcher.py --partitions $NUM_WORKERS > "$LOG_DIR/dispatcher.log" 2>&1 &
echo "  PID: $!"

# 启动 Workers
echo "[3/4] 启动 $NUM_WORKERS 个 Workers..."
for i in $(seq 0 $((NUM_WORKERS - 1))); do
    nohup python3 filter_worker.py \
        --partition $i \
        --worker-id "filter-worker-$i" \
        --algorithm "$ALGORITHM" \
        > "$LOG_DIR/worker-$i.log" 2>&1 &
    echo "  Worker-$i PID: $!"
done

# 启动 Aggregator
echo "[4/4] 启动 Aggregator..."
nohup python3 aggregator.py > "$LOG_DIR/aggregator.log" 2>&1 &
echo "  PID: $!"

sleep 3

echo ""
echo "=========================================="
echo "  服务已启动！"
echo "=========================================="
echo ""
echo "检查状态:"
echo "  ps aux | grep -E '(dispatcher|filter_worker|aggregator)' | grep -v grep"
echo ""
echo "查看日志:"
echo "  tail -f $LOG_DIR/worker-0.log"
echo "  tail -f $LOG_DIR/aggregator.log"
echo ""
echo "停止服务:"
echo "  $SCRIPT_DIR/stop-distributed.sh"
echo ""

# 显示进程状态
ps aux | grep -E "(dispatcher|filter_worker|aggregator)" | grep -v grep
