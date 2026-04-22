#!/bin/bash
# 配置 Kafka 数据保留策略，防止磁盘爆满
# 使用方法: bash scripts/setup-kafka-retention.sh

KAFKA_CONFIG="/opt/kafka/current/config/server.properties"

echo "=== 配置 Kafka 数据保留策略 ==="

# 备份原配置
cp $KAFKA_CONFIG ${KAFKA_CONFIG}.bak

# 添加保留配置
cat >> $KAFKA_CONFIG << 'EOF'

# === 数据保留配置 (防止磁盘爆满) ===
log.retention.hours=1
log.retention.bytes=1073741824
log.segment.bytes=104857600
log.retention.check.interval.ms=300000
log.cleanup.policy=delete
EOF

echo "[OK] 配置已添加到 $KAFKA_CONFIG"
echo "[INFO] 需要重启 Kafka 生效："
echo "  /opt/kafka/current/bin/kafka-server-stop.sh"
echo "  /opt/kafka/current/bin/kafka-server-start.sh -daemon /opt/kafka/current/config/server.properties"
