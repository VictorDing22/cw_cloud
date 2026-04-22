#!/bin/bash

# Docker服务测试脚本
echo "🧪 测试YuDao Cloud Docker部署"
echo "================================"

# 设置颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

echo ""
echo "1. 检查容器状态..."
docker compose ps

echo ""
echo "2. 测试基础设施服务..."

# 测试MySQL
echo -n "MySQL (3306): "
if docker exec yudao-mysql mysql -uroot -p20041102 -e "SELECT 1;" &>/dev/null; then
    print_success "运行正常"
else
    print_error "连接失败"
fi

# 测试Redis
echo -n "Redis (6379): "
if docker exec yudao-redis redis-cli ping | grep -q "PONG"; then
    print_success "运行正常"
else
    print_error "连接失败"
fi

# 测试Nacos
echo -n "Nacos (8848): "
if curl -s http://localhost:8848/nacos/v1/console/health &>/dev/null; then
    print_success "运行正常"
else
    print_warning "可能还在启动中"
fi

echo ""
echo "3. 测试应用服务..."

# 等待服务启动
echo "等待应用服务启动完成..."
sleep 30

# 测试Gateway
echo -n "Gateway (48080): "
if curl -s http://localhost:48080/actuator/health | grep -q "UP"; then
    print_success "运行正常"
else
    print_warning "可能还在启动中"
fi

# 测试System服务
echo -n "System Service (48081): "
if curl -s http://localhost:48081/actuator/health | grep -q "UP"; then
    print_success "运行正常"
else
    print_warning "可能还在启动中"
fi

# 测试Infra服务
echo -n "Infra Service (48082): "
if curl -s http://localhost:48082/actuator/health | grep -q "UP"; then
    print_success "运行正常"
else
    print_warning "可能还在启动中"
fi

echo ""
echo "4. 测试API接口..."

# 测试登录接口（通过Gateway）
echo -n "登录接口: "
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:48080/admin-api/system/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')

if echo "$LOGIN_RESPONSE" | grep -q '"code":0'; then
    print_success "登录接口正常"
elif echo "$LOGIN_RESPONSE" | grep -q '"code"'; then
    print_warning "登录接口响应异常（可能是密码错误）"
else
    print_error "登录接口无响应"
fi

echo ""
echo "5. 查看服务注册情况..."
echo "Nacos服务注册情况："
curl -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=yudao-gateway" | jq . 2>/dev/null || echo "Gateway未注册或jq未安装"

echo ""
echo "📊 总结："
echo "- 如果所有服务都显示✅，说明Docker部署成功"
echo "- 如果有⚠️服务，请等待更长时间或查看日志"
echo "- 如果有❌服务，请检查配置和日志"
echo ""
echo "📋 管理命令："
echo "- 查看日志: docker compose logs [service-name]"
echo "- 重启服务: docker compose restart [service-name]"
echo "- 停止所有: docker compose down"
