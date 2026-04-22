#!/bin/bash
echo "🚀 启动 YuDao Cloud"
cd /home/darkaling/yudao-cloud
docker compose -f docker-compose-simple.yml up mysql redis nacos -d
sleep 30
mkdir -p yudao-gateway/logs yudao-module-system/yudao-module-system-server/logs yudao-module-infra/yudao-module-infra-server/logs
cd yudao-gateway && java -jar target/yudao-gateway.jar --spring.profiles.active=local --server.port=48080 > logs/gateway.log 2>&1 &
sleep 30
cd /home/darkaling/yudao-cloud/yudao-module-system/yudao-module-system-server && java -jar target/yudao-module-system-server.jar --spring.profiles.active=local --server.port=48081 > logs/system.log 2>&1 &
sleep 30
cd /home/darkaling/yudao-cloud/yudao-module-infra/yudao-module-infra-server && java -jar target/yudao-module-infra-server.jar --spring.profiles.active=local --server.port=48082 > logs/infra.log 2>&1 &
sleep 30
cd /home/darkaling/yudao-ui-admin-vue3 && npm run dev > /tmp/frontend.log 2>&1 &
echo "🎉 启动完成！访问: http://localhost:3000/"
