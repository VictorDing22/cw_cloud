#!/bin/bash
# ========================================
# CW_Cloud 工业健康监测系统 - 一键启动脚本
# 服务器: 8.145.42.157
# ========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 路径配置
PROJECT_DIR="/opt/cw-cloud/CW_Cloud"
KAFKA_HOME="/opt/kafka/current"
NACOS_HOME="/opt/nacos"
JAVA_SERVICES_DIR="$PROJECT_DIR/yudao-cloud"
LOG_DIR="/opt/cw-cloud/logs"

# 创建日志目录
mkdir -p $LOG_DIR
mkdir -p $PROJECT_DIR/logs

echo ""
echo "========================================"
echo -e "${BLUE}  CW_Cloud 一键启动脚本${NC}"
echo "========================================"
echo ""

# 检查端口函数
check_port() {
    local port=$1
    netstat -tuln 2>/dev/null | grep -q ":$port " && return 0 || return 1
}

# 等待端口函数
wait_for_port() {
    local port=$1
    local name=$2
    local max_wait=$3
    local count=0
    
    while ! check_port $port; do
        count=$((count + 1))
        if [ $count -ge $max_wait ]; then
            echo -e "  ${RED}[超时]${NC} $name (端口 $port)"
            return 1
        fi
        sleep 1
    done
    echo -e "  ${GREEN}[OK]${NC} $name (端口 $port)"
    return 0
}

# ========================================
# 1. 检查基础服务
# ========================================
echo -e "${YELLOW}[1/6] 检查基础服务...${NC}"

# MySQL
if check_port 3306; then
    echo -e "  ${GREEN}[OK]${NC} MySQL (3306)"
else
    echo -e "  ${RED}[未运行]${NC} MySQL - 尝试启动..."
    systemctl start mysql
    sleep 3
fi

# Redis
if check_port 6379; then
    echo -e "  ${GREEN}[OK]${NC} Redis (6379)"
else
    echo -e "  ${RED}[未运行]${NC} Redis - 尝试启动..."
    systemctl start redis-server
    sleep 2
fi

# Nginx
if systemctl is-active --quiet nginx; then
    echo -e "  ${GREEN}[OK]${NC} Nginx (80)"
else
    echo -e "  ${RED}[未运行]${NC} Nginx - 尝试启动..."
    systemctl start nginx
fi

echo ""

# ========================================
# 2. 启动 Zookeeper 和 Kafka
# ========================================
echo -e "${YELLOW}[2/6] 启动 Kafka/Zookeeper...${NC}"

# Zookeeper
if check_port 2181; then
    echo -e "  ${GREEN}[OK]${NC} Zookeeper (2181) 已运行"
else
    echo "  启动 Zookeeper..."
    nohup $KAFKA_HOME/bin/zookeeper-server-start.sh $KAFKA_HOME/config/zookeeper.properties > $LOG_DIR/zookeeper.log 2>&1 &
    wait_for_port 2181 "Zookeeper" 30
fi

# Kafka
if check_port 9092; then
    echo -e "  ${GREEN}[OK]${NC} Kafka (9092) 已运行"
else
    echo "  启动 Kafka..."
    sleep 3
    nohup $KAFKA_HOME/bin/kafka-server-start.sh $KAFKA_HOME/config/server.properties > $LOG_DIR/kafka.log 2>&1 &
    wait_for_port 9092 "Kafka" 30
fi

echo ""

# ========================================
# 3. 启动 Nacos
# ========================================
echo -e "${YELLOW}[3/6] 启动 Nacos...${NC}"

if check_port 8848; then
    echo -e "  ${GREEN}[OK]${NC} Nacos (8848) 已运行"
else
    echo "  启动 Nacos..."
    cd $NACOS_HOME/bin
    bash startup.sh -m standalone > /dev/null 2>&1
    wait_for_port 8848 "Nacos" 60
fi

echo ""

# ========================================
# 4. 启动 Java 后端微服务
# ========================================
echo -e "${YELLOW}[4/6] 启动 Java 后端微服务...${NC}"

# Gateway
if check_port 48080; then
    echo -e "  ${GREEN}[OK]${NC} Gateway (48080) 已运行"
else
    echo "  启动 Gateway..."
    GATEWAY_JAR="$JAVA_SERVICES_DIR/yudao-gateway/target/yudao-gateway.jar"
    if [ -f "$GATEWAY_JAR" ]; then
        nohup java -Xms512m -Xmx1g -jar $GATEWAY_JAR > $LOG_DIR/gateway.log 2>&1 &
        wait_for_port 48080 "Gateway" 60
    else
        echo -e "  ${RED}[错误]${NC} Gateway JAR 不存在: $GATEWAY_JAR"
    fi
fi

# System
if check_port 48081; then
    echo -e "  ${GREEN}[OK]${NC} System (48081) 已运行"
else
    echo "  启动 System..."
    SYSTEM_JAR="$JAVA_SERVICES_DIR/yudao-module-system/yudao-module-system-server/target/yudao-module-system-server.jar"
    if [ -f "$SYSTEM_JAR" ]; then
        nohup java -Xms512m -Xmx1g -jar $SYSTEM_JAR > $LOG_DIR/system.log 2>&1 &
        wait_for_port 48081 "System" 60
    else
        echo -e "  ${RED}[错误]${NC} System JAR 不存在"
    fi
fi

# Infra
if check_port 48082; then
    echo -e "  ${GREEN}[OK]${NC} Infra (48082) 已运行"
else
    echo "  启动 Infra..."
    INFRA_JAR="$JAVA_SERVICES_DIR/yudao-module-infra/yudao-module-infra-server/target/yudao-module-infra-server.jar"
    if [ -f "$INFRA_JAR" ]; then
        nohup java -Xms512m -Xmx1g -jar $INFRA_JAR > $LOG_DIR/infra.log 2>&1 &
        wait_for_port 48082 "Infra" 60
    else
        echo -e "  ${RED}[错误]${NC} Infra JAR 不存在"
    fi
fi

echo ""

# ========================================
# 5. 创建 Kafka Topics
# ========================================
echo -e "${YELLOW}[5/6] 检查 Kafka Topics...${NC}"

$KAFKA_HOME/bin/kafka-topics.sh --create --topic sample-input --bootstrap-server 127.0.0.1:9092 --partitions 1 --replication-factor 1 --if-not-exists 2>/dev/null
$KAFKA_HOME/bin/kafka-topics.sh --create --topic sample-output --bootstrap-server 127.0.0.1:9092 --partitions 1 --replication-factor 1 --if-not-exists 2>/dev/null
echo -e "  ${GREEN}[OK]${NC} Kafka Topics 已就绪"

echo ""

# ========================================
# 6. 启动数据处理服务
# ========================================
echo -e "${YELLOW}[6/6] 启动数据处理服务...${NC}"

cd $PROJECT_DIR

# 滤波服务
if pgrep -f "high-speed-filter-service" > /dev/null; then
    echo -e "  ${GREEN}[OK]${NC} 滤波服务 已运行"
else
    echo "  启动滤波服务..."
    nohup python3 services/high-speed-filter-service.py --brokers 127.0.0.1:9092 > logs/filter-service.log 2>&1 &
    sleep 2
    if pgrep -f "high-speed-filter-service" > /dev/null; then
        echo -e "  ${GREEN}[OK]${NC} 滤波服务"
    else
        echo -e "  ${RED}[失败]${NC} 滤波服务"
    fi
fi

# 数据生产者
if pgrep -f "tdms-kafka-producer" > /dev/null; then
    echo -e "  ${GREEN}[OK]${NC} 数据生产者 已运行"
else
    echo "  启动数据生产者..."
    nohup python3 services/tdms-kafka-producer-ultra.py --threads 4 --packet-size 5000 > logs/producer.log 2>&1 &
    sleep 2
    if pgrep -f "tdms-kafka-producer" > /dev/null; then
        echo -e "  ${GREEN}[OK]${NC} 数据生产者"
    else
        echo -e "  ${RED}[失败]${NC} 数据生产者"
    fi
fi

# WebSocket Bridge
if check_port 8081; then
    echo -e "  ${GREEN}[OK]${NC} WebSocket Bridge (8081) 已运行"
else
    echo "  启动 WebSocket Bridge..."
    pm2 restart websocket-bridge 2>/dev/null || pm2 start services/websocket-bridge.js --name websocket-bridge
    sleep 2
    if check_port 8081; then
        echo -e "  ${GREEN}[OK]${NC} WebSocket Bridge (8081)"
    else
        echo -e "  ${RED}[失败]${NC} WebSocket Bridge"
    fi
fi

echo ""

# ========================================
# 完成 - 显示状态
# ========================================
echo "========================================"
echo -e "${GREEN}  启动完成！${NC}"
echo "========================================"
echo ""
echo "服务状态:"
echo "-------------------------------------------"
check_port 3306 && echo -e "  ${GREEN}✓${NC} MySQL (3306)" || echo -e "  ${RED}✗${NC} MySQL (3306)"
check_port 6379 && echo -e "  ${GREEN}✓${NC} Redis (6379)" || echo -e "  ${RED}✗${NC} Redis (6379)"
check_port 80 && echo -e "  ${GREEN}✓${NC} Nginx (80)" || echo -e "  ${RED}✗${NC} Nginx (80)"
check_port 2181 && echo -e "  ${GREEN}✓${NC} Zookeeper (2181)" || echo -e "  ${RED}✗${NC} Zookeeper (2181)"
check_port 9092 && echo -e "  ${GREEN}✓${NC} Kafka (9092)" || echo -e "  ${RED}✗${NC} Kafka (9092)"
check_port 8848 && echo -e "  ${GREEN}✓${NC} Nacos (8848)" || echo -e "  ${RED}✗${NC} Nacos (8848)"
check_port 48080 && echo -e "  ${GREEN}✓${NC} Gateway (48080)" || echo -e "  ${RED}✗${NC} Gateway (48080)"
check_port 48081 && echo -e "  ${GREEN}✓${NC} System (48081)" || echo -e "  ${RED}✗${NC} System (48081)"
check_port 48082 && echo -e "  ${GREEN}✓${NC} Infra (48082)" || echo -e "  ${RED}✗${NC} Infra (48082)"
check_port 8081 && echo -e "  ${GREEN}✓${NC} WebSocket (8081)" || echo -e "  ${RED}✗${NC} WebSocket (8081)"
echo "-------------------------------------------"
echo ""
echo "访问地址: http://8.145.42.157"
echo "账号: admin / admin123"
echo ""
echo "日志目录: $LOG_DIR"
echo ""
