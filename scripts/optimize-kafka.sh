#!/bin/bash
# Kafka优化脚本 - 提升吞吐量到2M+样本/秒

echo "=========================================="
echo "  Kafka 高吞吐量优化"
echo "=========================================="

KAFKA_HOME="/opt/kafka/current"
CONFIG_SRC="/opt/cw-cloud/CW_Cloud/config/kafka-server-optimized.properties"
CONFIG_DST="$KAFKA_HOME/config/server.properties"

# 检查Kafka目录
if [ ! -d "$KAFKA_HOME" ]; then
    echo "[ERROR] Kafka目录不存在: $KAFKA_HOME"
    exit 1
fi

# 备份原配置
echo "[1/4] 备份原配置..."
cp "$CONFIG_DST" "$CONFIG_DST.backup.$(date +%Y%m%d%H%M%S)"

# 停止Kafka
echo "[2/4] 停止Kafka..."
$KAFKA_HOME/bin/kafka-server-stop.sh 2>/dev/null
sleep 5

# 检查是否停止
if netstat -tlnp | grep -q ":9092"; then
    echo "[WARN] Kafka未完全停止，强制终止..."
    pkill -f kafka.Kafka
    sleep 3
fi

# 应用优化配置
echo "[3/4] 应用优化配置..."
if [ -f "$CONFIG_SRC" ]; then
    # 保留原有的log.dirs配置
    LOG_DIRS=$(grep "^log.dirs=" "$CONFIG_DST.backup."* 2>/dev/null | tail -1 | cut -d= -f2)
    if [ -z "$LOG_DIRS" ]; then
        LOG_DIRS="/var/kafka-logs"
    fi
    
    cp "$CONFIG_SRC" "$CONFIG_DST"
    sed -i "s|^log.dirs=.*|log.dirs=$LOG_DIRS|" "$CONFIG_DST"
    echo "  配置已更新"
else
    echo "[WARN] 优化配置文件不存在，使用手动优化..."
    # 手动添加优化参数
    cat >> "$CONFIG_DST" << EOF

# === 高吞吐量优化 ===
num.network.threads=8
num.io.threads=16
socket.send.buffer.bytes=1048576
socket.receive.buffer.bytes=1048576
log.flush.interval.messages=50000
EOF
fi

# 启动Kafka
echo "[4/4] 启动Kafka..."
$KAFKA_HOME/bin/kafka-server-start.sh -daemon $CONFIG_DST

# 等待启动
echo "等待Kafka启动..."
for i in {1..30}; do
    if netstat -tlnp | grep -q ":9092"; then
        echo ""
        echo "[OK] Kafka已启动"
        break
    fi
    echo -n "."
    sleep 1
done

# 验证
if netstat -tlnp | grep -q ":9092"; then
    echo ""
    echo "=========================================="
    echo "  优化完成！"
    echo "=========================================="
    echo "  网络线程: 8"
    echo "  IO线程: 16"
    echo "  Socket缓冲: 1MB"
    echo ""
    echo "运行生产者测试:"
    echo "  python3 services/tdms-kafka-producer-ultra.py"
else
    echo ""
    echo "[ERROR] Kafka启动失败，请检查日志:"
    echo "  tail -100 $KAFKA_HOME/logs/server.log"
fi
