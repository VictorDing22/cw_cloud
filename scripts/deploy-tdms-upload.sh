#!/bin/bash
# TDMS 文件上传服务部署脚本

set -e

PROJECT_ROOT="/opt/cw-cloud/CW_Cloud"
LOG_DIR="$PROJECT_ROOT/logs"

echo "=========================================="
echo "  TDMS 文件上传服务部署"
echo "=========================================="

# 1. 停止旧服务
echo "[1/4] 停止旧服务..."
pkill -f "tdms-upload-api.py" 2>/dev/null || true
sleep 1

# 2. 检查依赖
echo "[2/4] 检查 Python 依赖..."
python3 -c "import nptdms" 2>/dev/null || {
    echo "安装 nptdms..."
    pip3 install npTDMS -i https://pypi.tuna.tsinghua.edu.cn/simple
}

# 3. 启动服务
echo "[3/4] 启动 TDMS 上传服务 (端口 3004)..."
cd "$PROJECT_ROOT"
mkdir -p "$LOG_DIR"
nohup python3 services/tdms-upload-api.py > "$LOG_DIR/tdms-upload.log" 2>&1 &
sleep 2

# 检查是否启动成功
if curl -s http://127.0.0.1:3004/health | grep -q "ok"; then
    echo "[OK] TDMS 上传服务启动成功"
else
    echo "[ERROR] TDMS 上传服务启动失败，查看日志: $LOG_DIR/tdms-upload.log"
    exit 1
fi

# 4. 配置 Nginx
echo "[4/4] 配置 Nginx 代理..."

NGINX_CONF="/etc/nginx/sites-available/default"
if [ -f "$NGINX_CONF" ]; then
    # 检查是否已配置
    if grep -q "tdms-upload" "$NGINX_CONF"; then
        echo "[OK] Nginx 已配置 TDMS 上传代理"
    else
        echo "添加 Nginx 配置..."
        # 在 server 块末尾添加配置
        sudo sed -i '/^}/i \
    # TDMS 文件上传 API (端口 3004)\
    location /api/tdms-upload/ {\
        proxy_pass http://127.0.0.1:3004/;\
        proxy_set_header Host $host;\
        proxy_set_header X-Real-IP $remote_addr;\
        client_max_body_size 100M;\
        proxy_read_timeout 300;\
    }' "$NGINX_CONF"
        
        # 重载 Nginx
        sudo nginx -t && sudo systemctl reload nginx
        echo "[OK] Nginx 配置已更新"
    fi
else
    echo "[WARN] 未找到 Nginx 配置文件，请手动配置"
    echo "配置内容见: $PROJECT_ROOT/config/nginx-tdms-upload.conf"
fi

echo ""
echo "=========================================="
echo "  部署完成!"
echo "=========================================="
echo "  TDMS 上传 API: http://localhost:3004"
echo "  通过 Nginx:    http://your-domain/api/tdms-upload/"
echo "  日志文件:      $LOG_DIR/tdms-upload.log"
echo "=========================================="
