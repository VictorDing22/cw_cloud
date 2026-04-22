#!/bin/bash
# 修复 Kafka 数据管道 - 清除旧的 LZ4 消息，重建 topic
# 服务器恢复后执行此脚本

echo "=========================================="
echo "  修复 Kafka 数据管道"
echo "=========================================="

cd /opt/cw-cloud/CW_Cloud

# 1. 停止所有服务
echo "[1/8] 停止所有服务..."
pkill -f high-speed-filter-service 2>/dev/null
pkill -f tdms-kafka-producer 2>/dev/null
pm2 stop websocket-bridge 2>/dev/null
sleep 2

# 2. 删除旧的 Kafka topic（清除 LZ4 压缩的旧消息）
echo "[2/8] 删除旧的 Kafka topic..."
kafka-topics.sh --delete --topic sample-input --bootstrap-server 127.0.0.1:9092 2>/dev/null
kafka-topics.sh --delete --topic sample-output --bootstrap-server 127.0.0.1:9092 2>/dev/null
sleep 5

# 3. 重建 topic
echo "[3/8] 重建 Kafka topic..."
kafka-topics.sh --create --topic sample-input --bootstrap-server 127.0.0.1:9092 --partitions 1 --replication-factor 1
kafka-topics.sh --create --topic sample-output --bootstrap-server 127.0.0.1:9092 --partitions 1 --replication-factor 1

# 4. 确认 topic
echo "[4/8] 确认 topic 列表..."
kafka-topics.sh --list --bootstrap-server 127.0.0.1:9092

# 5. 创建日志目录
mkdir -p logs

# 6. 启动滤波服务
echo "[5/8] 启动滤波服务..."
nohup python3 services/high-speed-filter-service.py --bro