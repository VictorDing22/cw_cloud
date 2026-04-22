#!/bin/bash
# 高速滤波服务启动脚本
# 替代 backend.jar

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
SERVICE_FILE="$PROJECT_DIR/services/high-speed-filter-service.py"
LOG_DIR="$PROJECT_DIR/logs"
LOG_FILE="$LOG_DIR/filter-service.log"
PID_FILE="$LOG_DIR/filter-service.pid"

# 创建日志目录
mkdir -p "$LOG_DIR"

# 默认参数
ALGORITHM="lms"
WORKERS=4
MULTITHREAD=false
BROKERS="localhost:9092"

# 解析参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --algorithm|-a)
            ALGORITHM="$2"
            shift 2
            ;;
        --workers|-w)
            WORKERS="$2"
            shift 2
            ;;
        --multithread|-m)
            MULTITHREAD=true
            shift
            ;;
        --brokers|-b)
            BROKERS="$2"
            shift 2
            ;;
        --help|-h)
            echo "用法: $0 [选项]"
            echo ""
            echo "选项:"
            echo "  -a, --algorithm   滤波算法 (lms/kalman/lowpass/bandpass) [默认: lms]"
            echo "  -w, --workers     工作线程数 [默认: 4]"
            echo "  -m, --multithread 启用多线程模式"
            echo "  -b, --brokers     Kafka brokers [默认: localhost:9092]"
            echo "  -h, --help        显示帮助"
            exit 0
            ;;
        *)
            echo "未知参数: $1"
            exit 1
            ;;
    esac
done

# 检查是否已运行
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if ps -p "$OLD_PID" > /dev/null 2>&1; then
        echo "[警告] 服务已在运行 (PID: $OLD_PID)"
        echo "使用 stop-filter-service.sh 停止服务"
        exit 1
    else
        rm -f "$PID_FILE"
    fi
fi

# 检查Python
if ! command -v python3 &> /dev/null; then
    echo "[错误] 未找到 python3"
    exit 1
fi

# 检查依赖
echo "检查依赖..."
python3 -c "import numpy; import kafka; from scipy import signal" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "[错误] 缺少依赖，请运行:"
    echo "  pip3 install numpy kafka-python scipy"
    exit 1
fi

# 构建命令
CMD="python3 $SERVICE_FILE --algorithm $ALGORITHM --workers $WORKERS --brokers $BROKERS"
if [ "$MULTITHREAD" = true ]; then
    CMD="$CMD --multithread"
fi

echo "============================================================"
echo "  启动高速滤波服务"
echo "============================================================"
echo "  算法: $ALGORITHM"
echo "  线程: $WORKERS"
echo "  多线程: $MULTITHREAD"
echo "  Brokers: $BROKERS"
echo "  日志: $LOG_FILE"
echo "============================================================"

# 启动服务
nohup $CMD > "$LOG_FILE" 2>&1 &
PID=$!
echo $PID > "$PID_FILE"

sleep 2

# 检查是否启动成功
if ps -p $PID > /dev/null 2>&1; then
    echo "[OK] 服务已启动 (PID: $PID)"
    echo ""
    echo "查看日志: tail -f $LOG_FILE"
    echo "停止服务: $SCRIPT_DIR/stop-filter-service.sh"
else
    echo "[错误] 服务启动失败"
    echo "查看日志: cat $LOG_FILE"
    exit 1
fi
