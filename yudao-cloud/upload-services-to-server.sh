#!/bin/bash

# MySQL、Redis、Nacos 服务文件上传脚本
# 使用方法: ./upload-services-to-server.sh [服务器IP] [用户名] [目标目录]

# 设置默认值
SERVER_IP=${1:-"your-server-ip"}
USERNAME=${2:-"root"}
TARGET_DIR=${3:-"/opt/yudao-cloud"}

echo "=========================================="
echo "开始上传 MySQL、Redis、Nacos 相关文件到服务器"
echo "=========================================="
echo "服务器: $USERNAME@$SERVER_IP"
echo "目标目录: $TARGET_DIR"
echo ""

# 创建目标目录
echo "1. 在服务器上创建目标目录..."
ssh $USERNAME@$SERVER_IP "mkdir -p $TARGET_DIR/sql/mysql"

# 上传 docker-compose.yml 文件
echo "2. 上传 docker-compose.yml 文件..."
scp docker-compose.yml $USERNAME@$SERVER_IP:$TARGET_DIR/

# 上传 MySQL SQL 脚本目录
echo "3. 上传 MySQL SQL 初始化脚本..."
scp -r sql/mysql/* $USERNAME@$SERVER_IP:$TARGET_DIR/sql/mysql/

echo ""
echo "=========================================="
echo "文件上传完成！"
echo "=========================================="
echo ""
echo "接下来可以在服务器上执行："
echo "  cd $TARGET_DIR"
echo "  docker-compose up -d mysql redis nacos"
echo ""
