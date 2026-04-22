#!/bin/bash
# K8s+Flink 分布式滤波方案 - Docker Compose 部署脚本
# 使用现有 Kafka (localhost:9092)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "  K8s+Flink 分布式滤波方案部署"
echo "=========================================="

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "[ERROR] Docker 未安装"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "[ERROR] Docker Compose 未安装"
    exit 1
fi

echo "[1/5] 检查 Kafka 连接..."
if ! nc -z localhost 9092 2>/dev/null; then
    echo "[WARN] Kafka (localhost:9092) 不可达，请确保 Kafka 已启动"
fi

echo "[2/5] 停止旧容器..."
docker-compose down --remove-orphans 2>/dev/null || true

echo "[3/5] 构建镜像..."
docker-compose build --no-cache

echo "[4/5] 启动服务..."
docker-compose up -d

echo "[5/5] 等待服务启动..."
sleep 5

echo ""
echo "=========================================="
echo "  服务状态"
echo "=========================================="
docker-compose ps

echo ""
echo "=========================================="
echo "  端口说明"
echo "=========================================="
echo "  Flink Dashboard:    http://localhost:8084"
echo "  Filter Gateway API: http://localhost:8010"
echo "  WebSocket (分布式):  ws://localhost:8085/realtime"
echo ""
echo "=========================================="
echo "  Nginx 配置 (需手动添加)"
echo "=========================================="
echo "在 /etc/nginx/sites-available/default 中添加:"
echo ""
echo "  # 分布式滤波 WebSocket (Docker)"
echo "  location /distributed {"
echo "      proxy_pass http://127.0.0.1:8085;"
echo "      proxy_http_version 1.1;"
echo "      proxy_set_header Upgrade \$http_upgrade;"
echo "      proxy_set_header Connection \"upgrade\";"
echo "      proxy_set_header Host \$host;"
echo "      proxy_read_timeout 86400;"
echo "  }"
echo ""
echo "然后执行: sudo nginx -t && sudo systemctl reload nginx"
echo ""
echo "=========================================="
echo "  测试命令"
echo "=========================================="
echo "# 测试滤波 API"
echo "curl -X POST http://localhost:8010/filter \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"signal\": [0.1, 0.5, -0.3, 0.8], \"filter_type\": \"kalman\"}'"
echo ""
echo "# 查看日志"
echo "docker-compose logs -f filter-processor-1"
echo ""
echo "[OK] 部署完成!"
