#!/bin/bash
# ========================================
# 工业健康监测系统 - 云服务器一键部署脚本
# 服务器: 8.145.42.157
# ========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PROJECT_DIR="/opt/cw-cloud/CW_Cloud"
FRONTEND_DIR="$PROJECT_DIR/yudao-ui-admin-vue3"
NGINX_DIR="/var/www/cw-cloud"

echo ""
echo "========================================"
echo "  工业健康监测系统 - 一键部署"
echo "========================================"
echo ""

# ========================================
# 1. 拉取最新代码
# ========================================
echo -e "${YELLOW}[1/5] 拉取最新代码...${NC}"
cd $PROJECT_DIR

# 暂存本地修改
git stash 2>/dev/null || true

# 拉取最新代码
git pull origin main

# 恢复本地修改（如果有）
git stash pop 2>/dev/null || true

echo -e "${GREEN}[OK] 代码更新完成${NC}"
echo ""

# ========================================
# 2. 安装依赖
# ========================================
echo -e "${YELLOW}[2/5] 检查依赖...${NC}"

# 检查Node依赖
if [ ! -d "$PROJECT_DIR/node_modules/express" ]; then
    echo "安装服务端Node依赖..."
    cd $PROJECT_DIR
    npm install express cors ws kafkajs mysql2
fi

# 检查前端依赖
if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
    echo "安装前端依赖..."
    cd $FRONTEND_DIR
    npm install
fi

echo -e "${GREEN}[OK] 依赖检查完成${NC}"
echo ""

# ========================================
# 3. 打包前端
# ========================================
echo -e "${YELLOW}[3/5] 打包前端...${NC}"
cd $FRONTEND_DIR
npm run build:prod

# 复制到Nginx目录
mkdir -p $NGINX_DIR
rm -rf $NGINX_DIR/*
cp -r dist-prod/* $NGINX_DIR/

echo -e "${GREEN}[OK] 前端打包完成${NC}"
echo ""

# ========================================
# 4. 重启数据处理服务
# ========================================
echo -e "${YELLOW}[4/5] 重启数据处理服务...${NC}"

# 停止旧进程
pkill -f "tdms-api-server.js" 2>/dev/null || true
pkill -f "websocket-bridge.js" 2>/dev/null || true
pkill -f "backend.jar" 2>/dev/null || true

sleep 2

cd $PROJECT_DIR

# 启动TDMS API (端口3002)
if [ -f "services/tdms-api-server.js" ]; then
    nohup node services/tdms-api-server.js > logs/tdms.log 2>&1 &
    echo "  [OK] TDMS API (3002)"
fi

# 启动WebSocket Bridge (端口8081)
if [ -f "services/websocket-bridge.js" ]; then
    nohup node services/websocket-bridge.js > logs/websocket.log 2>&1 &
    echo "  [OK] WebSocket Bridge (8081)"
fi

# 启动Backend滤波 (端口8080)
if [ -f "backend.jar" ]; then
    nohup java --add-opens java.base/java.lang=ALL-UNNAMED -jar backend.jar > logs/backend.log 2>&1 &
    echo "  [OK] Backend滤波 (8080)"
fi

echo -e "${GREEN}[OK] 数据处理服务已启动${NC}"
echo ""

# ========================================
# 5. 重载Nginx
# ========================================
echo -e "${YELLOW}[5/5] 重载Nginx...${NC}"
nginx -t && nginx -s reload

echo -e "${GREEN}[OK] Nginx已重载${NC}"
echo ""

# ========================================
# 完成
# ========================================
echo "========================================"
echo -e "${GREEN}  部署完成！${NC}"
echo "========================================"
echo ""
echo "访问地址: http://8.145.42.157"
echo "账号: admin / admin123"
echo ""
echo "查看日志:"
echo "  tail -f $PROJECT_DIR/logs/tdms.log"
echo "  tail -f $PROJECT_DIR/logs/websocket.log"
echo "  tail -f $PROJECT_DIR/logs/backend.log"
echo ""
