#!/bin/bash
# 本地 Docker Compose 部署脚本

set -e

echo "=========================================="
echo "  本地 Docker Compose 部署"
echo "=========================================="

cd "$(dirname "$0")/.."

echo "[1/3] 构建镜像..."
docker-compose build

echo "[2/3] 启动服务..."
docker-compose up -d

echo "[3/3] 等待服务就绪..."
sleep 10

echo ""
echo "=========================================="
echo "  部署完成！"
echo "=========================================="
echo ""
echo "服务状态:"
docker-compose ps
echo ""
echo "访问地址:"
echo "  - Flink Dashboard: http://localhost:8081"
echo "  - 滤波网关 API: http://localhost:8010/docs"
echo "  - WebSocket: ws://localhost:8083/realtime"
echo ""
echo "测试滤波 API:"
echo '  curl -X POST http://localhost:8010/filter \'
echo '    -H "Content-Type: application/json" \'
echo '    -d '\''{"signal": [0.2, 0.9, -0.8, 0.7, -0.6], "filter_type": "kalman"}'\'''
echo ""
echo "查看日志:"
echo "  docker-compose logs -f filter-gateway"
echo ""
