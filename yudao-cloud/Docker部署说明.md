# 🐳 YuDao Cloud Docker 容器化部署指南

## 📋 概述

本指南介绍如何使用Docker容器化部署整个YuDao Cloud项目，包括：
- 后端微服务（Gateway、System、Infra、Filter）
- 前端管理界面（Vue3 + Nginx）
- 基础设施（MySQL、Redis、Nacos）

## 🏗️ 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                Docker Compose 服务编排                      │
├─────────────────────────────────────────────────────────────┤
│  前端服务容器                                               │
│  ├── yudao-ui-admin-vue3 (Nginx + Vue3)                   │
│  └── 端口: 3000                                            │
├─────────────────────────────────────────────────────────────┤
│  后端微服务容器群                                           │
│  ├── yudao-gateway (Spring Cloud Gateway)                  │
│  │   └── 端口: 48080                                       │
│  ├── yudao-system-server (系统管理)                        │
│  │   └── 端口: 48081                                       │
│  ├── yudao-infra-server (基础设施)                         │
│  │   └── 端口: 48082                                       │
│  └── yudao-filter-server (滤波器服务) 🆕                   │
│      └── 端口: 48083                                       │
├─────────────────────────────────────────────────────────────┤
│  基础设施容器                                               │
│  ├── MySQL 8.0 (数据库)                                    │
│  │   └── 端口: 3306                                        │
│  ├── Redis 7.0 (缓存)                                      │
│  │   └── 端口: 6379                                        │
│  └── Nacos 2.2.3 (注册中心)                               │
│      └── 端口: 8848                                        │
└─────────────────────────────────────────────────────────────┘
```

## 🛠️ 环境要求

### 基础环境
- **Docker**: 20.10+ 
- **Docker Compose**: 2.0+
- **内存**: 至少 4GB
- **磁盘空间**: 至少 10GB

### 开发环境（用于构建）
- **Java**: 17+
- **Maven**: 3.6+
- **Node.js**: 18+
- **Git**: 最新版本

## 🚀 快速开始

### 1. 克隆项目
```bash
# 克隆后端项目
git clone <yudao-cloud-repo-url>
cd yudao-cloud

# 克隆前端项目
git clone <yudao-ui-admin-vue3-repo-url>
```

### 2. 一键构建和部署
```bash
# 构建所有镜像
./build-docker.sh

# 启动所有服务
./docker-manage.sh start
```

### 3. 访问应用
- **前端管理界面**: http://localhost:3000
- **API网关**: http://localhost:48080
- **Nacos控制台**: http://localhost:8848/nacos
  - 用户名: nacos
  - 密码: nacos

## 📝 详细操作步骤

### 步骤1: 项目构建

```bash
# 清理和编译Java项目
mvn clean package -DskipTests

# 构建前端项目
cd ../yudao-ui-admin-vue3
npm ci
npm run build:prod
cd ../yudao-cloud
```

### 步骤2: Docker镜像构建

```bash
# 构建所有Docker镜像
docker-compose build

# 或者使用构建脚本
./build-docker.sh
```

### 步骤3: 服务启动

```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps
```

## 🔧 管理命令

使用 `docker-manage.sh` 脚本进行日常管理：

```bash
# 启动所有服务
./docker-manage.sh start

# 停止所有服务
./docker-manage.sh stop

# 重启所有服务
./docker-manage.sh restart

# 查看服务状态
./docker-manage.sh status

# 查看所有服务日志
./docker-manage.sh logs

# 查看特定服务日志
./docker-manage.sh logs gateway

# 重新构建镜像
./docker-manage.sh build

# 清理未使用的Docker资源
./docker-manage.sh clean

# 查看运行中的容器
./docker-manage.sh ps
```

## 📊 服务监控

### 查看服务状态
```bash
# 查看所有容器状态
docker-compose ps

# 查看资源使用情况
docker stats

# 查看服务健康状态
curl http://localhost:48080/actuator/health
curl http://localhost:48081/actuator/health
curl http://localhost:48082/actuator/health
```

### 查看日志
```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f gateway
docker-compose logs -f system-server
docker-compose logs -f mysql
```

## 🔒 安全配置

### 生产环境建议

1. **数据库密码**: 修改 `docker-compose.yml` 中的MySQL密码
2. **Redis密码**: 为Redis设置密码
3. **网络隔离**: 使用自定义网络，限制容器间通信
4. **资源限制**: 为每个容器设置CPU和内存限制

```yaml
# 示例：资源限制配置
gateway:
  # ... 其他配置
  deploy:
    resources:
      limits:
        memory: 1G
        cpus: '0.5'
      reservations:
        memory: 512M
        cpus: '0.25'
```

## 🗃️ 数据持久化

### 数据卷配置
- **MySQL数据**: `mysql_data:/var/lib/mysql`
- **Redis数据**: `redis_data:/data`
- **Nacos日志**: `nacos_logs:/home/nacos/logs`

### 备份数据
```bash
# 备份MySQL数据
docker exec yudao-mysql mysqldump -uroot -p20041102 ruoyi-vue-pro > backup.sql

# 备份Redis数据
docker exec yudao-redis redis-cli BGSAVE
```

## 🐛 故障排除

### 常见问题

1. **端口冲突**
   - 检查端口是否被占用: `netstat -tulpn | grep :3000`
   - 修改 `docker-compose.yml` 中的端口映射

2. **内存不足**
   - 检查系统内存: `free -h`
   - 减少容器数量或调整JVM参数

3. **网络连接问题**
   - 检查Docker网络: `docker network ls`
   - 重启Docker服务: `sudo systemctl restart docker`

4. **镜像构建失败**
   - 清理Docker缓存: `docker system prune -a`
   - 检查Dockerfile语法

### 日志分析
```bash
# 查看容器启动日志
docker-compose logs gateway

# 查看系统资源使用
docker stats --no-stream

# 检查容器健康状态
docker inspect yudao-gateway | grep Health -A 10
```

## 🔄 版本更新

### 更新流程
1. 拉取最新代码
2. 重新构建镜像
3. 滚动更新服务

```bash
# 拉取最新代码
git pull origin main

# 重新构建
./build-docker.sh

# 滚动更新（不停机）
docker-compose up -d --no-deps gateway
docker-compose up -d --no-deps system-server
```

## 📈 性能优化

### JVM调优
修改各服务的Dockerfile中的JAVA_OPTS参数：

```dockerfile
ENV JAVA_OPTS="-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### 数据库优化
在 `docker-compose.yml` 中添加MySQL配置：

```yaml
mysql:
  # ... 其他配置
  command: >
    --default-authentication-plugin=mysql_native_password
    --innodb-buffer-pool-size=1G
    --max-connections=200
```

## 📚 相关文档

- [Docker官方文档](https://docs.docker.com/)
- [Docker Compose文档](https://docs.docker.com/compose/)
- [Spring Boot Docker部署](https://spring.io/guides/gs/spring-boot-docker/)
- [YuDao官方文档](https://cloud.iocoder.cn/)

## 🤝 贡献指南

欢迎提交问题和改进建议！

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

---

**注意**: 这是教学用的部署配置，生产环境请根据实际需求进行安全加固和性能优化。
