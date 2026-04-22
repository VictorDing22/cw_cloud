#!/bin/bash
# 停止 K8s+Flink 分布式滤波服务

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "停止 K8s+Flink 分布式滤波服务..."
docker-compose down

echo "[OK] 服务已停止"
