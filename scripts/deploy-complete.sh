#!/bin/bash
# CW Cloud 完整部署脚本
# 用法: ./scripts/deploy-complete.sh

set -e

echo "=============================================="
echo "  CW Cloud 完整部署脚本"
echo "=============================================="

cd /opt/cw-cloud/CW_Cloud

# 1. 拉取最新代码
echo ""
echo "[1/7] 拉取最新代码..."
git pull

# 2. 安装/更新 Nginx 配置
echo ""
echo "[2/7] 更新 Nginx 配置..."
cp config/nginx-cw-cloud.conf /etc/nginx/sites-available/cw-cloud
ln -sf /etc/nginx/sites-available/cw-cloud /etc/nginx/sites-enabled/cw-cloud
rm -f /etc/nginx/sites-enabled/default 2>/dev/null || true
nginx -t && systemctl reload nginx
echo "[OK] Nginx 配置已更新"

# 3. 更新 systemd 服务
echo ""
echo "[3/7] 更新 systemd 服务..."
cp scripts/systemd/*.service /etc/systemd/system/
systemctl daemon-reload

# 4. 重启所有服务
echo ""
echo "[4/7] 重启服务..."
systemctl restart cw-signal-producer
sleep 2
systemctl restart cw-filter
sleep 2
systemctl restart cw-websocket-bridge
sleep 2

# 5. 检查服务状态
echo ""
echo "[5/7] 检查服务状态..."
echo "--- Signal Producer ---"
systemctl status cw-signal-producer --no-pager | head -5
echo ""
echo "--- Filter Service ---"
systemctl status cw-filter --no-pager | head -5
echo ""
echo "--- WebSocket Bridge ---"
systemctl status cw-websocket-bridge --no-pager | head -5

# 6. 构建前端（可选）
echo ""
echo "[6/7] 是否需要重新构建前端? (y/n)"
read -t 10 BUILD_FRONTEND || BUILD_FRONTEND="n"
if [ "$BUILD_FRONTEND" = "y" ]; then
    echo "构建前端..."
    cd yudao-ui-admin-vue3
    npm run build:local
    cp -r dist/* /var/www/html/
    cd ..
    echo "[OK] 前端已构建并部署"
else
    echo "[跳过] 前端构建"
fi

# 7. 验证
echo ""
echo "[7/7] 验证部署..."
echo ""
echo "检查端口监听:"
echo "  - 3003 (Signal Producer): $(ss -tlnp | grep :3003 | wc -l) 个进程"
echo "  - 8081 (WebSocket Bridge): $(ss -tlnp | grep :8081 | wc -l) 个进程"
echo "  - 9092 (Kafka): $(ss -tlnp | grep :9092 | wc -l) 个进程"
echo ""

echo "=============================================="
echo "  部署完成!"
echo "=============================================="
echo ""
echo "测试步骤:"
echo "  1. 打开浏览器访问 http://8.145.42.157"
echo "  2. 进入 实时监控 -> 滤波监控"
echo "  3. 选择数据源 (Signal-1 或 Signal-2)"
echo "  4. 点击 '开始监控'"
echo ""
echo "查看日志:"
echo "  journalctl -u cw-websocket-bridge -f"
echo "  journalctl -u cw-filter -f"
echo "  journalctl -u cw-signal-producer -f"
echo ""
