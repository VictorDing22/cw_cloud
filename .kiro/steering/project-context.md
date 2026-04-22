# 工业健康监测平台 - 项目上下文

## 项目简介
这是一个基于 Spring Cloud Alibaba + Vue3 的工业设备健康监测系统，用于实时信号采集、滤波处理和异常检测。

## 三大子系统

1. **CW_Cloud 管理平台** - Web管理后台 + 微服务架构
   - 技术栈：Spring Cloud Alibaba + Vue3 + MySQL
   - 端口：48080（Gateway）、3000（Frontend）
   - 功能：用户权限、设备管理、历史数据查询

2. **backend.jar 实时滤波服务** - Kafka驱动的实时计算引擎
   - 技术栈：Spring Boot + Kafka + WebSocket
   - 端口：8080
   - 功能：实时滤波（LMS/NLMS）、WebSocket推送

3. **floatdata 流处理引擎** - 大数据流式处理
   - 技术栈：Spark Streaming + Kafka + Python
   - 功能：信号分析、FFT频谱、异常检测

## 关键目录结构
```
yudao-cloud/          # 后端微服务（Spring Cloud）
yudao-ui-admin-vue3/  # 前端项目（Vue3 + Element Plus）
services/             # 数据处理服务（Node.js/Python）
  - high-speed-filter-service.py  # 高速滤波服务（实时监控用）
  - tdms-signal-producer.py       # TDMS数据生产者
  - websocket-bridge.js           # WebSocket桥接服务
  - remote-filter-service.py      # 远程滤波服务（调用盛老师API）
services/distributed/             # 分布式滤波服务
  - dispatcher.py                 # 数据分发器
  - filter_worker.py              # 滤波Worker（调用远程API）
  - aggregator.py                 # 聚合重排服务
k8s-flink-solution/               # K8s+Flink高速方案
scripts/              # 启动脚本
config/               # 配置文件
sql/                  # 数据库脚本
```

## 常用端口
| 服务 | 端口 |
|------|------|
| Gateway | 48080 |
| Frontend | 80 |
| backend.jar | 8080 |
| Kafka | 9092 |
| Nacos | 8848 |
| MySQL | 3306 |
| WebSocket Bridge | 8083 |
| 分布式聚合服务 | 8082 |
| Signal Producer | 3003 |

## 服务器部署信息
- 服务器IP: 8.145.42.157
- 项目路径: `/opt/cw-cloud/CW_Cloud`
- 部署脚本: `cd /opt/cw-cloud && ./deploy.sh`
- Kafka路径: `/opt/kafka/current`
- 日志目录: `/opt/cw-cloud/CW_Cloud/logs/`

## 开发流程（重要！）

**每次修改代码后必须遵循以下流程：**

1. **本地开发** - 在功能分支上修改代码
2. **提交推送** - `git add -A && git commit -m "描述" && git push origin 分支名`
3. **服务器测试** - 
   ```bash
   cd /opt/cw-cloud/CW_Cloud
   git fetch origin
   git checkout 分支名
   git pull
   # 如果是前端修改，需要重新构建
   cd yudao-ui-admin-vue3 && npm run build:local
   sudo cp -r dist/* /var/www/html/
   # 如果是后端服务修改，需要重启对应服务
   ```
4. **测试通过后合并** - 
   ```bash
   git checkout main
   git merge 分支名
   git push origin main
   ```

**当前开发状态：**
- 本地分支：`feature/high-speed-anomaly`
- 稳定版本：`05a2219`（服务器当前运行版本）
- 服务器 WebSocket Bridge 端口：8081（不是8083！）
## 盛老师滤波微服务 API
| 算法 | 地址 | 端口 | 路径 |
|------|------|------|------|
| Kalman | http://49.235.44.231:8000 | 8000 | /kalman/audio/run |
| RLS | http://49.235.44.231:8001 | 8001 | /rls/audio/run |
| LS | http://49.235.44.231:8002 | 8002 | /ls/audio/run |

### API 调用示例
```bash
curl -X POST http://49.235.44.231:8000/kalman/audio/run \
  -H "Content-Type: application/json" \
  -d '{"signal": [0.2, 0.9, -0.8, 0.7, -0.6], "model": "level", "process_noise_var": 1e-3, "measurement_noise_var": 1e-2}'
```

## 两套监控系统

### 1. 实时监控 (FilterMonitor.vue)
- 使用本地 `high-speed-filter-service.py` (Butterworth低通滤波)
- 数据流: `sample-input` → `high-speed-filter-service` → `sample-output` → `websocket-bridge(8083)` → 前端
- 特点: 低延迟，本地处理

### 2. 分布式监控 (DistributedFilterMonitor.vue)
- 使用盛老师的远程滤波API (Kalman/RLS/LS)
- 数据流: `sample-input` → `dispatcher` → `distributed-raw` → `filter_worker(远程API)` → `distributed-filtered` → `aggregator(8082)` → 前端
- 特点: 真实滤波算法，可扩展

## 启动命令

### 实时监控服务
```bash
cd /opt/cw-cloud/CW_Cloud

# 启动数据源
nohup python3 services/tdms-signal-producer.py > logs/signal-producer.log 2>&1 &

# 启动滤波服务
nohup python3 services/high-speed-filter-service.py > logs/filter.log 2>&1 &

# 启动WebSocket桥接 (端口8083)
nohup node services/websocket-bridge.js > logs/websocket.log 2>&1 &

# 启动数据流
curl -X POST http://127.0.0.1:3003/start -H "Content-Type: application/json" -d '{"source": "signal-1"}'
```

### 分布式监控服务
```bash
cd /opt/cw-cloud/CW_Cloud/services/distributed

# 使用启动脚本 (3个Worker, Kalman算法)
./start-distributed-remote.sh 3 kalman

# 或手动启动
nohup python3 dispatcher.py --partitions 3 > ../../logs/dispatcher.log 2>&1 &
nohup python3 filter_worker.py --partition 0 --worker-id filter-worker-0 --algorithm kalman > ../../logs/worker-0.log 2>&1 &
nohup python3 filter_worker.py --partition 1 --worker-id filter-worker-1 --algorithm kalman > ../../logs/worker-1.log 2>&1 &
nohup python3 filter_worker.py --partition 2 --worker-id filter-worker-2 --algorithm kalman > ../../logs/worker-2.log 2>&1 &
nohup python3 aggregator.py > ../../logs/aggregator.log 2>&1 &
```

## 当前进度/待办事项
- [x] 添加 TDMS 真实数据源 (Signal-1, Signal-2)
- [x] 实现实时滚动波形效果
- [x] 创建 tdms-signal-producer.py 服务（端口3003）
- [x] 修改前端 Signal-1/Signal-2 走真实 Kafka 数据流
- [x] 修复波形图双线显示问题
- [x] 恢复到稳定版本 05a2219
- [ ] **进行中**：提升处理速度到 2M/s
- [ ] **进行中**：添加异常检测功能
- [x] 修复磁盘满问题（Kafka日志目录迁移到 /opt/kafka/data）
- [x] 修复 WebSocket Bridge 端口冲突（改为8083）
- [x] 集成盛老师滤波API到分布式监控
- [x] 创建 K8s+Flink 高速滤波方案
- [ ] **待测试**: 分布式监控显示两条线（原始+滤波）
- [ ] **待优化**: 提升分布式处理速度

## 重要决策/约定
- **必须使用真实数据流**：所有数据处理必须通过真实的 Kafka 数据流，不使用前端模拟
- **实时监控不受影响**：分布式监控的修改不影响实时监控页面
- **分布式使用远程API**：分布式滤波调用盛老师的 Kalman/RLS/LS 微服务

## 已知问题及解决方案

### 1. 磁盘满问题
- 原因: Kafka日志在 /tmp/kafka-logs 占用181GB
- 解决: 迁移到 /opt/kafka/data，设置7天保留策略

### 2. WebSocket端口冲突
- 原因: 8081端口被Java Gateway占用
- 解决: websocket-bridge.js 改用8083端口，Nginx代理相应更新

### 3. 波形只显示一条线
- 原因: 原始信号和滤波后信号值几乎相同（LMS滤波器问题）
- 解决: 
  - 实时监控: 使用Butterworth低通滤波
  - 分布式监控: 调用盛老师的真实滤波API

## K8s + Flink 高速方案 (规划中)
目录: `k8s-flink-solution/`

架构:
```
Kafka → Flink/Python Workers → 盛老师滤波API → Kafka → WebSocket → 前端
                ↓
        K8s HPA 自动扩缩容
```

目标: 10M+ 样本/秒处理能力
