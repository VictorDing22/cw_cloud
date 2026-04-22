#!/bin/bash
# 安装 CW Cloud 服务到 systemd
# 使服务开机自启动并自动重启

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SYSTEMD_DIR="/etc/systemd/system"

echo "=== 安装 CW Cloud 服务 ==="

# 停止现有的 nohup 进程
echo "[1/6] 停止现有进程..."
pkill -f tdms-signal-producer.py 2>/dev/null || true
pkill -f high-speed-filter-service.py 2>/dev/null || true
pkill -f websocket-bridge.js 2>/dev/null || true
sleep 2

# 复制 systemd 服务文件
echo "[2/6] 复制服务文件..."
cp "$SCRIPT_DIR/systemd/cw-signal-producer.service" "$SYSTEMD_DIR/"
cp "$SCRIPT_DIR/systemd/cw-filter.service" "$SYSTEMD_DIR/"
cp "$SCRIPT_DIR/systemd/cw-websocket-bridge.service" "$SYSTEMD_DIR/"

# 重新加载 systemd
echo "[3/6] 重新加载 systemd..."
systemctl daemon-reload

# 启用服务（开机自启动）
echo "[4/6] 启用服务..."
systemctl enable cw-signal-producer.service
systemctl enable cw-filter.service
systemctl enable cw-websocket-bridge.service

# 启动服务
echo "[5/6] 启动服务..."
systemctl start cw-websocket-bridge.service
sleep 2
systemctl start cw-signal-producer.service
sleep 2
systemctl start cw-filter.service

# 检查状态
echo "[6/6] 检查服务状态..."
echo ""
echo "=== WebSocket Bridge ==="
systemctl status cw-websocket-bridge.service --no-pager | head -10

echo ""
echo "=== Signal Producer ==="
systemctl status cw-signal-producer.service --no-pager | head -10

echo ""
echo "=== Filter Service ==="
systemctl status cw-filter.service --no-pager | head -10

echo ""
echo "=== 安装完成 ==="
echo ""
echo "常用命令："
echo "  查看状态: systemctl status cw-filter.service"
echo "  查看日志: journalctl -u cw-filter.service -f"
echo "  重启服务: systemctl restart cw-filter.service"
echo "  停止服务: systemctl stop cw-filter.service"
