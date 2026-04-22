#!/bin/bash
# 服务检查和自启动脚本
# 检查所有必要服务，如果未运行则自动启动

PROJECT_DIR="/opt/cw-cloud/CW_Cloud"
KAFKA_DIR="/opt/kafka"  # 根据实际路径调整

echo "============================================================"
echo "  服务状态检查"
echo "============================================================"
echo ""

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

check_port() {
    local name=$1
    local port=$2
    if ss -tuln | grep -q ":$port "; then
        echo -e "  ${GREEN}[运行中]${NC} $name (端口 $port)"
        return 0
    else
        echo -e "  ${RED}[未运行]${NC} $name (端口 $port)"
        return 1
    fi
}

check_process() {
    local name=$1
    local pattern=$2
    if pgrep -f "$pattern" > /dev/null 2>&1; then
        echo -e "  ${GREEN}[运行中]${NC} $name"
        return 0
    else
        echo -e "  ${RED}[未运行]${NC} $name"
        return 1
    fi
}

# 1. 检查基础服务
echo "1. 基础服务"
echo "-----------------------------------------------------------"
check_port "MySQL" 3306
check_port "Redis" 6379
check_port "Zookeeper" 2181
check_port "Kafka" 9092
check_port "Nacos" 8848

# 2. 检查微服务
echo ""
echo "2. 微服务"
echo "-----------------------------------------------------------"
check_port "Gateway" 48080
check_port "System" 48081
check_port "Infra" 48082

# 3. 检查数据处理服务
echo ""
echo "3. 数据处理服务"
echo "-----------------------------------------------------------"
check_process "滤波服务" "high-speed-filter-service"
check_process "数据生产者" "tdms-kafka-producer"
check_port "WebSocket Bridge" 8081

# 4. PM2 服务
echo ""
echo "4. PM2 管理的服务"
echo "-----------------------------------------------------------"
if command -v pm2 &> /dev/null; then
    pm2 list 2>/dev/null | grep -E "online|stopped" | head -10
else
    echo "  PM2 未安装"
fi

echo ""
echo "============================================================"

# 询问是否自动启动未运行的服务
if [ "$1" == "--auto" ]; then
    echo ""
    echo "自动启动模式..."
    
    # 启动 Kafka (如果未运行)
    if ! ss -tuln | grep -q ":9092 "; then
        echo "启动 Kafka..."
        if [ -d "$KAFKA_DIR" ]; then
            cd $KAFKA_DIR
            bin/zookeeper-server-start.sh -daemon config/zookeeper.properties
            sleep 5
            bin/kafka-server-start.sh -daemon config/server.properties
            sleep 5
        fi
    fi
    
    # 启动 WebSocket Bridge
    if ! ss -tuln | grep -q ":8081 "; then
        echo "启动 WebSocket Bridge..."
        cd $PROJECT_DIR
        pm2 start services/websocket-bridge.js --name websocket-bridge 2>/dev/null
    fi
    
    # 启动滤波服务
    if ! pgrep -f "high-speed-filter-service" > /dev/null; then
        echo "启动滤波服务..."
        cd $PROJECT_DIR
        nohup python3 services/high-speed-filter-service.py --multithread --workers 8 > logs/filter-service.log 2>&1 &
    fi
    
    echo ""
    echo "服务启动完成，等待5秒后重新检查..."
    sleep 5
    exec $0
fi

echo ""
echo "使用 '$0 --auto' 自动启动未运行的服务"
