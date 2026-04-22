#!/bin/bash
# 数据管道测试脚本
# 测试完整的数据流: TDMS -> Kafka -> 滤波服务 -> WebSocket -> 前端

echo "============================================================"
echo "  数据管道测试"
echo "============================================================"

PROJECT_DIR="/opt/cw-cloud/CW_Cloud"
cd "$PROJECT_DIR" 2>/dev/null || cd "$(dirname "$0")/.."

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

check_service() {
    local name=$1
    local pattern=$2
    if pgrep -f "$pattern" > /dev/null 2>&1; then
        echo -e "  ${GREEN}[OK]${NC} $name"
        return 0
    else
        echo -e "  ${RED}[FAIL]${NC} $name"
        return 1
    fi
}

check_port() {
    local name=$1
    local port=$2
    if netstat -tuln 2>/dev/null | grep -q ":$port " || ss -tuln 2>/dev/null | grep -q ":$port "; then
        echo -e "  ${GREEN}[OK]${NC} $name (端口 $port)"
        return 0
    else
        echo -e "  ${RED}[FAIL]${NC} $name (端口 $port)"
        return 1
    fi
}

echo ""
echo "1. 检查基础服务..."
echo "-----------------------------------------------------------"
check_port "Kafka" 9092
check_port "Zookeeper" 2181
check_port "MySQL" 3306
check_port "Redis" 6379

echo ""
echo "2. 检查应用服务..."
echo "-----------------------------------------------------------"
check_service "滤波服务" "high-speed-filter-service.py"
check_service "WebSocket Bridge" "websocket-bridge"
check_port "WebSocket" 8081

echo ""
echo "3. 检查 Kafka 主题..."
echo "-----------------------------------------------------------"

# 检查主题是否存在
if command -v kafka-topics.sh &> /dev/null; then
    TOPICS=$(kafka-topics.sh --list --bootstrap-server localhost:9092 2>/dev/null)
elif command -v kafkacat &> /dev/null; then
    TOPICS=$(kafkacat -b localhost:9092 -L 2>/dev/null | grep "topic" | awk '{print $2}' | tr -d '"')
else
    TOPICS="无法检查 (缺少 kafka-topics.sh 或 kafkacat)"
fi

if echo "$TOPICS" | grep -q "sample-input"; then
    echo -e "  ${GREEN}[OK]${NC} sample-input 主题存在"
else
    echo -e "  ${YELLOW}[WARN]${NC} sample-input 主题可能不存在"
fi

if echo "$TOPICS" | grep -q "sample-output"; then
    echo -e "  ${GREEN}[OK]${NC} sample-output 主题存在"
else
    echo -e "  ${YELLOW}[WARN]${NC} sample-output 主题可能不存在"
fi

echo ""
echo "4. 测试数据流..."
echo "-----------------------------------------------------------"

# 检查是否有数据在流动
echo "  检查 sample-input 消息..."
if command -v kafkacat &> /dev/null; then
    MSG_COUNT=$(timeout 3 kafkacat -b localhost:9092 -t sample-input -C -c 1 -q 2>/dev/null | wc -l)
    if [ "$MSG_COUNT" -gt 0 ]; then
        echo -e "  ${GREEN}[OK]${NC} sample-input 有数据"
    else
        echo -e "  ${YELLOW}[WARN]${NC} sample-input 无数据 (需要启动数据生产者)"
    fi
else
    echo -e "  ${YELLOW}[SKIP]${NC} 无法检查 (缺少 kafkacat)"
fi

echo "  检查 sample-output 消息..."
if command -v kafkacat &> /dev/null; then
    MSG_COUNT=$(timeout 3 kafkacat -b localhost:9092 -t sample-output -C -c 1 -q 2>/dev/null | wc -l)
    if [ "$MSG_COUNT" -gt 0 ]; then
        echo -e "  ${GREEN}[OK]${NC} sample-output 有数据 (滤波服务正在工作)"
    else
        echo -e "  ${YELLOW}[WARN]${NC} sample-output 无数据"
    fi
else
    echo -e "  ${YELLOW}[SKIP]${NC} 无法检查 (缺少 kafkacat)"
fi

echo ""
echo "5. 服务日志..."
echo "-----------------------------------------------------------"

if [ -f "logs/filter-service.log" ]; then
    echo "  滤波服务最近日志:"
    tail -5 logs/filter-service.log 2>/dev/null | sed 's/^/    /'
fi

if command -v pm2 &> /dev/null; then
    echo ""
    echo "  PM2 服务状态:"
    pm2 list 2>/dev/null | grep -E "filter-service|websocket" | sed 's/^/    /'
fi

echo ""
echo "============================================================"
echo "  测试完成"
echo "============================================================"
echo ""
echo "如果有服务未运行，请执行:"
echo "  1. 启动滤波服务: ./scripts/deploy-filter-service.sh"
echo "  2. 启动数据生产者: python3 services/tdms-kafka-producer-ultra.py"
echo "  3. 启动 WebSocket: pm2 start services/websocket-bridge.js --name websocket-bridge"
echo ""
