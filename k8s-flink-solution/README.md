# K8s+Flink 分布式滤波方案

基于 Docker Compose 的分布式滤波部署方案，使用盛老师的 Kalman/RLS/LS 滤波 API。

## 架构

```
sample-input (Kafka) 
    ↓
filter-processor-1/2/3 (Docker, 调用盛老师API)
    ↓
distributed-filtered (Kafka)
    ↓
websocket-bridge-distributed (Docker, 端口8085)
    ↓
Nginx (/distributed)
    ↓
前端 DistributedFilterMonitor.vue
```

## 快速部署

```bash
# 1. 进入目录
cd /opt/cw-cloud/CW_Cloud/k8s-flink-solution

# 2. 部署
chmod +x deploy.sh
./deploy.sh

# 3. 配置 Nginx (一次性)
sudo nano /etc/nginx/sites-available/default
# 添加 nginx-distributed.conf 中的配置
sudo nginx -t && sudo systemctl reload nginx
```

## 端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| Flink Dashboard | 8084 | Flink 管理界面 |
| Filter Gateway API | 8010 | 滤波 API 网关 |
| WebSocket (分布式) | 8085 | 分布式监控 WebSocket |

## 常用命令

```bash
# 查看状态
docker-compose ps

# 查看日志
docker-compose logs -f filter-processor-1

# 停止服务
./stop.sh

# 重启
docker-compose restart
```

## 测试

```bash
# 测试滤波 API
curl -X POST http://localhost:8010/filter \
  -H 'Content-Type: application/json' \
  -d '{"signal": [0.1, 0.5, -0.3, 0.8], "filter_type": "kalman"}'
```
