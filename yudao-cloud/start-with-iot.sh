#!/bin/bash

# 工业故障监测平台 - 完整启动脚本（包含IoT服务）
# 创建时间: 2025-10-20

echo "========================================="
echo " 工业故障监测平台 - 启动脚本"
echo "========================================="

# 停止旧服务
echo "[1/6] 正在停止旧服务..."
bash quick-stop.sh 2>/dev/null
sleep 2

# 编译IoT模块
echo "[2/6] 正在编译IoT模块..."
cd /home/darkaling/yudao-cloud
mvn clean package -DskipTests -pl yudao-module-iot/yudao-module-iot-biz -am -q

if [ ! -f "yudao-module-iot/yudao-module-iot-biz/target/yudao-module-iot-biz.jar" ]; then
    echo "错误: IoT模块编译失败！"
    exit 1
fi

echo "   ✓ IoT模块编译完成"

# 启动基础服务
echo "[3/6] 正在启动基础服务（Gateway, System, Infra）..."
bash quick-start.sh
sleep 15

# 启动IoT服务
echo "[4/6] 正在启动IoT服务..."
nohup java -jar yudao-module-iot/yudao-module-iot-biz/target/yudao-module-iot-biz.jar \
  --spring.profiles.active=local \
  --server.port=18083 \
  --spring.application.name=iot-server \
  > logs/iot.log 2>&1 &
  
IOT_PID=$!
echo $IOT_PID > iot.pid
echo "   ✓ IoT服务已启动 (PID: $IOT_PID)"

# 等待服务启动
echo "[5/6] 等待服务完全启动..."
for i in {1..30}; do
    if grep -q "Started" logs/iot.log 2>/dev/null; then
        echo "   ✓ IoT服务启动成功！"
        break
    fi
    echo -n "."
    sleep 1
done
echo ""

# 验证服务
echo "[6/6] 验证服务..."

# 检查进程
if ps -p $IOT_PID > /dev/null; then
    echo "   ✓ IoT服务进程运行正常"
else
    echo "   ✗ IoT服务进程异常，请查看日志: tail -f logs/iot.log"
fi

# 检查端口
if netstat -tulpn 2>/dev/null | grep -q 18083; then
    echo "   ✓ IoT服务端口18083已监听"
else
    echo "   ⚠ IoT服务端口18083未监听"
fi

# 测试API
echo ""
echo "正在测试API..."
sleep 5
RESPONSE=$(curl -s -w "\n%{http_code}" http://localhost:48080/admin-api/iot/alert/user/list?pageNo=1\&pageSize=10 2>/dev/null | tail -1)

if [ "$RESPONSE" = "200" ]; then
    echo "   ✓ API测试成功！"
else
    echo "   ⚠ API测试失败，HTTP状态码: $RESPONSE"
    echo "     请稍等片刻后重试，或查看日志"
fi

echo ""
echo "========================================="
echo " 启动完成！"
echo "========================================="
echo ""
echo "服务状态:"
echo "  - Gateway:  http://localhost:48080"
echo "  - IoT API:  http://localhost:48080/admin-api/iot/alert/"
echo "  - 前端:     http://localhost:3000"
echo ""
echo "查看日志:"
echo "  tail -f logs/gateway.log    # Gateway日志"
echo "  tail -f logs/iot.log        # IoT日志"
echo ""
echo "停止服务:"
echo "  bash quick-stop.sh && kill \$(cat iot.pid)"
echo ""
echo "========================================="

