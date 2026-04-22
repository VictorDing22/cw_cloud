wslws#!/bin/bash

# Docker管理脚本
# 用于管理YuDao Cloud的Docker容器

# 设置颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 函数：打印带颜色的消息
print_message() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] ✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] ⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ❌ $1${NC}"
}

# 显示使用帮助
show_help() {
    echo "YuDao Cloud Docker 管理脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  start      启动所有服务"
    echo "  stop       停止所有服务"
    echo "  restart    重启所有服务"
    echo "  status     查看服务状态"
    echo "  logs       查看所有服务日志"
    echo "  logs [service]  查看指定服务日志"
    echo "  build      重新构建镜像"
    echo "  clean      清理未使用的镜像和容器"
    echo "  ps         查看运行中的容器"
    echo "  help       显示此帮助信息"
    echo ""
    echo "服务名称:"
    echo "  mysql, redis, nacos, gateway, system-server, infra-server, filter-server, admin-ui"
    echo ""
    echo "示例:"
    echo "  $0 start                    # 启动所有服务"
    echo "  $0 logs gateway             # 查看网关服务日志"
    echo "  $0 restart system-server    # 重启系统服务"
}

# 启动服务
start_services() {
    print_message "启动 YuDao Cloud 服务..."
    docker compose up -d
    
    if [ $? -eq 0 ]; then
        print_success "服务启动成功！"
        echo ""
        print_message "访问地址："
        echo "- 前端管理界面: http://localhost:3000"
        echo "- API网关: http://localhost:48080"
        echo "- Nacos控制台: http://localhost:8848/nacos (用户名/密码: nacos/nacos)"
        echo ""
        print_message "查看服务状态: $0 status"
        print_message "查看服务日志: $0 logs"
    else
        print_error "服务启动失败！"
        exit 1
    fi
}

# 停止服务
stop_services() {
    print_message "停止 YuDao Cloud 服务..."
    docker compose down
    
    if [ $? -eq 0 ]; then
        print_success "服务已停止"
    else
        print_error "服务停止失败！"
        exit 1
    fi
}

# 重启服务
restart_services() {
    print_message "重启 YuDao Cloud 服务..."
    docker compose restart
    
    if [ $? -eq 0 ]; then
        print_success "服务重启成功！"
    else
        print_error "服务重启失败！"
        exit 1
    fi
}

# 查看服务状态
show_status() {
    print_message "YuDao Cloud 服务状态："
    docker compose ps
    
    echo ""
    print_message "资源使用情况："
    docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}"
}

# 查看日志
show_logs() {
    if [ -z "$2" ]; then
        print_message "显示所有服务日志 (按 Ctrl+C 退出):"
        docker compose logs -f --tail=100
    else
        print_message "显示 $2 服务日志 (按 Ctrl+C 退出):"
        docker compose logs -f --tail=100 "$2"
    fi
}

# 重新构建
rebuild_services() {
    print_message "重新构建 YuDao Cloud 镜像..."
    ./build-docker.sh
}

# 清理资源
clean_resources() {
    print_warning "这将删除未使用的镜像、容器和网络"
    read -p "确定要继续吗？(y/N): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_message "清理Docker资源..."
        docker system prune -f
        docker volume prune -f
        print_success "清理完成"
    else
        print_message "取消清理操作"
    fi
}

# 查看容器
show_containers() {
    print_message "运行中的容器："
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}

# 主逻辑
case "$1" in
    start)
        start_services
        ;;
    stop)
        stop_services
        ;;
    restart)
        if [ -z "$2" ]; then
            restart_services
        else
            print_message "重启 $2 服务..."
            docker compose restart "$2"
        fi
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs "$@"
        ;;
    build)
        rebuild_services
        ;;
    clean)
        clean_resources
        ;;
    ps)
        show_containers
        ;;
    help|--help|-h)
        show_help
        ;;
    "")
        show_help
        ;;
    *)
        print_error "未知选项: $1"
        echo ""
        show_help
        exit 1
        ;;
esac
