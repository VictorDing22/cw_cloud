#!/bin/bash
# K8s+Flink 分布式滤波 - 完整部署脚本
# 在服务器上执行: bash full-deploy.sh

set -e

echo "=========================================="
echo "  K8s+Flink 分布式滤波 - 完整部署"
echo "=========================================="

PROJECT_DIR="/opt/cw-cloud/CW_Cloud"
FLINK_DIR="$PROJECT_DIR/k8s-flink-solution"

# ========== Step 1: 拉取最新代码 ==========
echo ""
echo "[Step 1/6] 拉取最新代码..."
cd "$PROJECT_DIR"
git pull

# ========== Step 2: 检查 Docker ==========
echo ""
echo "[Step 2/6] 检查 Docker..."
if ! command -v docker &> /dev/null; then
    echo "[ERROR] Docker 未安装!"
    exit 1
fi
if ! command -v docker-compose &> /dev/null; then
    echo "[ERROR] Docker Compose 未安装!"
    exit 1
fi
echo "[OK] Docker 已安装"

# ========== Step 3: 检查 Kafka ==========
echo ""
echo "[Step 3/6] 检查 Kafka..."
if nc -z localhost 9092 2>/dev/null; then
    echo "[OK] Kafka 运行中 (localhost:9092)"
else
    echo "[WARN] Kafka 不可达，尝试启动..."
    cd /opt/kafka/current
    nohup bin/kafka-server-start.sh config/server.properties > /tmp/kafka.log 2>&1 &
    sleep 5
fi

# ========== Step 4: 部署 Docker 服务 ==========
echo ""
echo "[Step 4/6] 部署 Docker 服务..."
cd "$FLINK_DIR"
chmod +x *.sh 2>/dev/null || true

# 停止旧容器
docker-compose down --remove-orphans 2>/dev/null || true

# 构建并启动
docker-compose build
docker-compose up -d

echo "等待服务启动..."
sleep 8

# 检查状态
echo ""
echo "Docker 容器状态:"
docker-compose ps

# ========== Step 5: 配置 Nginx ==========
echo ""
echo "[Step 5/6] 配置 Nginx..."

NGINX_CONF="/etc/nginx/sites-available/default"

# 检查是否已配置
if grep -q "location /distributed" "$NGINX_CONF" 2>/dev/null; then
    echo "[OK] Nginx /distributed 已配置，跳过"
else
    echo "添加 Nginx /distributed 配置..."
    
    # 备份原配置
    sudo cp "$NGINX_CONF" "$NGINX_CONF.bak.$(date +%Y%m%d%H%M%S)"
    
    # 在 location /realtime 之前插入配置
    sudo sed -i '/location \/realtime/i\
    # 分布式滤波 WebSocket (Docker K8s+Flink)\
    location /distributed {\
        proxy_pass http://127.0.0.1:8085;\
        proxy_http_version 1.1;\
        proxy_set_header Upgrade $http_upgrade;\
        proxy_set_header Connection "upgrade";\
        proxy_set_header Host $host;\
        proxy_set_header X-Real-IP $remote_addr;\
        proxy_read_timeout 86400;\
        proxy_send_timeout 86400;\
    }\
' "$NGINX_CONF"
    
    # 测试配置
    if sudo nginx -t; then
        sudo systemctl reload nginx
        echo "[OK] Nginx 配置已更新"
    else
        echo "[ERROR] Nginx 配置错误，恢复备份..."
        sudo cp "$NGINX_CONF.bak."* "$NGINX_CONF"
        exit 1
    fi
fi

# ========== Step 6: 启动数据源 ==========
echo ""
echo "[Step 6/6] 检查数据源..."

# 检查 signal-producer 是否运行
if curl -s http://127.0.0.1:3003/status > /dev/null 2>&1; then
    echo "[OK] Signal Producer 已运行"
else
    echo "启动 Signal Producer..."
    cd "$PROJECT_DIR"
    nohup python3 services/tdms-signal-producer.py > logs/signal-producer.log 2>&1 &
    sleep 3
fi

# ========== 完成 ==========
echo ""
echo "=========================================="
echo "  部署完成!"
echo "=========================================="
echo ""
echo "服务端口:"
echo "  - Flink Dashboard:    http://localhost:8084"
echo "  - Filter Gateway API: http://localhost:8010"
echo "  - WebSocket (分布式):  ws://localhost:8085"
echo ""
echo "测试命令:"
echo "  # 测试滤波 API"
echo "  curl -X POST http://localhost:8010/filter \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"signal\": [0.1, 0.5, -0.3, 0.8], \"filter_type\": \"kalman\"}'"
echo ""
echo "  # 启动数据流"
echo "  curl -X POST http://127.0.0.1:3003/start \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"source\": \"signal-1\"}'"
echo ""
echo "  # 查看日志"
echo "  docker-compose -f $FLINK_DIR/docker-compose.yml logs -f filter-processor-1"
echo ""
echo "访问分布式监控页面测试效果"
