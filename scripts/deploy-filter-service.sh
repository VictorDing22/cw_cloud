#!/bin/bash
# 高速滤波服务一键部署脚本
# 用于在服务器上快速部署 Python 滤波服务替代 backend.jar

set -e

echo "============================================================"
echo "  高速滤波服务部署脚本"
echo "============================================================"

# 配置
PROJECT_DIR="/opt/cw-cloud/CW_Cloud"
SERVICE_FILE="services/high-speed-filter-service.py"
LOG_DIR="$PROJECT_DIR/logs"

# 检查是否在正确目录
if [ ! -f "$PROJECT_DIR/$SERVICE_FILE" ]; then
    echo "[ERROR] 未找到服务文件: $PROJECT_DIR/$SERVICE_FILE"
    echo "请确保在正确的目录运行此脚本"
    exit 1
fi

cd "$PROJECT_DIR"

# 1. 安装依赖
echo ""
echo "[1/5] 安装 Python 依赖..."
pip3 install numpy kafka-python scipy lz4 --quiet 2>/dev/null || {
    echo "[WARN] pip3 安装失败，尝试 pip..."
    pip install numpy kafka-python scipy lz4 --quiet
}
echo "[OK] 依赖安装完成"

# 2. 停止旧服务
echo ""
echo "[2/5] 停止旧服务..."

# 停止 backend.jar
if pgrep -f "backend.jar" > /dev/null; then
    echo "  停止 backend.jar..."
    pkill -f "backend.jar" || true
    sleep 2
fi

# 停止旧的 Python 滤波服务
if pgrep -f "high-speed-filter-service.py" > /dev/null; then
    echo "  停止旧的滤波服务..."
    pkill -f "high-speed-filter-service.py" || true
    sleep 2
fi

# 停止 PM2 管理的服务
if command -v pm2 &> /dev/null; then
    pm2 delete filter-service 2>/dev/null || true
fi

echo "[OK] 旧服务已停止"

# 3. 创建日志目录
echo ""
echo "[3/5] 准备环境..."
mkdir -p "$LOG_DIR"
echo "[OK] 日志目录: $LOG_DIR"

# 4. 启动服务
echo ""
echo "[4/5] 启动高速滤波服务..."

# 检查是否有 PM2
if command -v pm2 &> /dev/null; then
    echo "  使用 PM2 管理服务..."
    pm2 start "$PROJECT_DIR/$SERVICE_FILE" \
        --name filter-service \
        --interpreter python3 \
        -- --multithread --workers 8 --algorithm lms
    
    sleep 3
    pm2 status filter-service
else
    echo "  使用 nohup 启动服务..."
    nohup python3 "$PROJECT_DIR/$SERVICE_FILE" \
        --multithread --workers 8 --algorithm lms \
        > "$LOG_DIR/filter-service.log" 2>&1 &
    
    PID=$!
    echo $PID > "$LOG_DIR/filter-service.pid"
    
    sleep 3
    
    if ps -p $PID > /dev/null 2>&1; then
        echo "[OK] 服务已启动 (PID: $PID)"
    else
        echo "[ERROR] 服务启动失败"
        echo "查看日志: tail -50 $LOG_DIR/filter-service.log"
        exit 1
    fi
fi

# 5. 验证服务
echo ""
echo "[5/5] 验证服务..."

sleep 2

# 检查进程
if pgrep -f "high-speed-filter-service.py" > /dev/null; then
    echo "[OK] 滤波服务进程运行中"
else
    echo "[WARN] 未检测到滤波服务进程"
fi

# 检查 Kafka 连接
echo ""
echo "============================================================"
echo "  部署完成!"
echo "============================================================"
echo ""
echo "服务状态:"
if command -v pm2 &> /dev/null; then
    echo "  pm2 status filter-service"
    echo "  pm2 logs filter-service"
else
    echo "  查看日志: tail -f $LOG_DIR/filter-service.log"
    echo "  停止服务: pkill -f high-speed-filter-service.py"
fi
echo ""
echo "测试数据流:"
echo "  1. 启动数据生产者: python3 services/tdms-kafka-producer-ultra.py"
echo "  2. 查看前端实时监控页面"
echo ""
