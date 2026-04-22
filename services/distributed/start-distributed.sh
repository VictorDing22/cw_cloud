#!/bin/bash
# 启动分布式滤波服务 (非Docker模式，用于测试)

cd "$(dirname "$0")"

echo "=============================================="
echo "  启动分布式滤波服务"
echo "=============================================="

# 检查依赖
python3 -c "import kafka, numpy, scipy, websockets" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "[ERROR] 缺少Python依赖"
    echo "安装: pip3 install kafka-python numpy scipy websockets"
    exit 1
fi

KAFKA_BROKERS=${KAFKA_BROKERS:-"localhost:9092"}
NUM_WORKERS=${NUM_WORKERS:-4}

echo "Kafka: $KAFKA_BROKERS"
echo "Workers: $NUM_WORKERS"
echo ""

# 创建日志目录
mkdir -p ../../logs/distributed

# 启动Dispatcher
echo "[1/6] 启动 Dispatcher..."
nohup python3 dispatcher.py --brokers $KAFKA_BROKERS --partitions $NUM_WORKERS \
    > ../../logs/distributed/dispatcher.log 2>&1 &
echo "PID: $!"
sleep 1

# 启动Workers
for i in $(seq 0 $((NUM_WORKERS-1))); do
    echo "[$(($i+2))/6] 启动 Filter Worker $i..."
    nohup python3 filter_worker.py --brokers $KAFKA_BROKERS --partition $i --worker-id filter-worker-$i \
        > ../../logs/distributed/worker-$i.log 2>&1 &
    echo "PID: $!"
    sleep 0.5
done

# 启动Aggregator
echo "[6/6] 启动 Aggregator..."
nohup python3 aggregator.py --brokers $KAFKA_BROKERS --partitions $NUM_WORKERS \
    > ../../logs/distributed/aggregator.log 2>&1 &
echo "PID: $!"

echo ""
echo "=============================================="
echo "  分布式服务已启动"
echo "=============================================="
echo "  WebSocket: ws://localhost:8082/distributed"
echo "  日志目录: logs/distributed/"
echo "=============================================="
echo ""
echo "查看日志: tail -f ../../logs/distributed/*.log"
echo "停止服务: ./stop-distributed.sh"
