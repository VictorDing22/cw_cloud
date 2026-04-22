#!/bin/bash

echo "========================================="
echo " 重启System服务（包含新的测试接口）"
echo "========================================="

cd /home/darkaling/yudao-cloud

# 1. 停止System服务
echo "[1/4] 停止System服务..."
if [ -f "system.pid" ]; then
    SYSTEM_PID=$(cat system.pid)
    kill $SYSTEM_PID 2>/dev/null
    sleep 2
    echo "   ✓ System服务已停止"
fi

# 2. 重新编译System模块
echo "[2/4] 重新编译System模块..."
cd yudao-module-system/yudao-module-system-biz
mvn clean package -DskipTests -q

if [ ! -f "target/yudao-module-system-biz.jar" ]; then
    echo "   ✗ 编译失败！"
    exit 1
fi
echo "   ✓ 编译完成"

# 3. 启动System服务
echo "[3/4] 启动System服务..."
cd /home/darkaling/yudao-cloud/yudao-module-system/yudao-module-system-biz
nohup java -jar target/yudao-module-system-biz.jar \
  --spring.profiles.active=local \
  --server.port=48081 \
  > ../../logs/system.log 2>&1 &

SYSTEM_PID=$!
echo $SYSTEM_PID > ../../system.pid
echo "   ✓ System服务已启动 (PID: $SYSTEM_PID)"

# 4. 等待服务启动
echo "[4/4] 等待服务完全启动..."
sleep 20

for i in {1..30}; do
    if tail -50 ../../logs/system.log | grep -q "Started"; then
        echo "   ✓ System服务启动成功！"
        break
    fi
    echo -n "."
    sleep 1
done
echo ""

# 测试新接口
echo "测试信号生成接口..."
sleep 3
curl -s "http://localhost:48080/admin-api/test/signal/health" | head -50

echo ""
echo "========================================="
echo " 重启完成！"
echo "========================================="
echo ""
echo "测试步骤："
echo "1. 刷新浏览器（Ctrl+Shift+R）"
echo "2. 访问：http://localhost:3000/#/test/signal"
echo "3. 点击'生成信号'按钮"
echo ""
echo "查看日志："
echo "  tail -f logs/system.log"
echo "========================================="

