#!/bin/bash
# 磁盘健康检查脚本 - 建议添加到 crontab 每小时执行
# 使用方法: bash scripts/check-disk-health.sh

THRESHOLD=80  # 磁盘使用率警告阈值
CRITICAL=90   # 磁盘使用率危险阈值

echo "=== 磁盘健康检查 $(date) ==="

# 检查主分区使用率
USAGE=$(df / | tail -1 | awk '{print $5}' | sed 's/%//')

echo "当前磁盘使用率: ${USAGE}%"

if [ $USAGE -ge $CRITICAL ]; then
    echo "[危险] 磁盘使用率超过 ${CRITICAL}%！"
    echo "自动清理 Kafka 数据..."
    rm -rf /opt/kafka/data/*
    echo "[OK] Kafka 数据已清理"
    
    # 重启 Kafka
    /opt/kafka/current/bin/kafka-server-stop.sh
    sleep 3
    /opt/kafka/current/bin/kafka-server-start.sh -daemon /opt/kafka/current/config/server.properties
    echo "[OK] Kafka 已重启"
    
elif [ $USAGE -ge $THRESHOLD ]; then
    echo "[警告] 磁盘使用率超过 ${THRESHOLD}%"
    echo "Kafka 数据目录大小:"
    du -sh /opt/kafka/data/
fi

# 显示大目录
echo ""
echo "=== 磁盘占用 TOP 5 ==="
du -sh /opt/* 2>/dev/null | sort -hr | head -5
