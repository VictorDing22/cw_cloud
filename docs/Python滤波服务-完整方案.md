# Python 高速滤波服务 - 完整方案

## 方案概述

使用 Python 实现的高速滤波服务替代 `backend.jar`，解决处理速度瓶颈问题。

## 创建的文件

| 文件 | 说明 |
|------|------|
| `services/high-speed-filter-service.py` | 核心滤波服务 |
| `scripts/start-filter-service.sh` | Linux 启动脚本 |
| `scripts/stop-filter-service.sh` | Linux 停止脚本 |
| `scripts/start-filter-service.bat` | Windows 启动脚本 |
| `scripts/deploy-filter-service.sh` | 服务器一键部署脚本 |
| `scripts/test-data-pipeline.sh` | 数据管道测试脚本 |
| `services/test-filter-performance.py` | 性能测试脚本 |
| `docs/高速滤波服务部署指南.md` | 详细部署文档 |

## 功能特性

### 支持的滤波算法

1. **LMS (最小均方)** - 自适应滤波，适合去除相关噪声
2. **Kalman (卡尔曼)** - 状态估计滤波，适合平滑信号
3. **LowPass (低通)** - Butterworth 低通滤波
4. **BandPass (带通)** - Butterworth 带通滤波

### 性能优化

- 多线程处理模式
- Kafka 批量消费/生产
- LZ4 压缩
- NumPy 向量化计算
- 设备级滤波器实例池

### 输出格式

完全兼容 `websocket-bridge.js` 和前端 `FilterMonitor.vue`：

```json
{
  "type": "signal-data",
  "deviceId": "tdms-xxx",
  "timestamp": 1702800000000,
  "sampleRate": 50000,
  "originalSamples": [...],
  "filteredSamples": [...],
  "residuals": [...],
  "snrBefore": 15.5,
  "snrAfter": 28.3,
  "snrImprovement": 12.8,
  "statistics": {...},
  "processingTimeMs": 5.2,
  "filterType": "LMS",
  "mode": "real-data"
}
```

## 服务器部署步骤

### 1. 上传文件到服务器

```bash
# 从本地上传
scp services/high-speed-filter-service.py root@8.145.42.157:/opt/cw-cloud/CW_Cloud/services/
scp scripts/deploy-filter-service.sh root@8.145.42.157:/opt/cw-cloud/CW_Cloud/scripts/
scp scripts/start-filter-service.sh root@8.145.42.157:/opt/cw-cloud/CW_Cloud/scripts/
scp scripts/stop-filter-service.sh root@8.145.42.157:/opt/cw-cloud/CW_Cloud/scripts/
```

### 2. 在服务器上执行部署

```bash
ssh root@8.145.42.157
cd /opt/cw-cloud/CW_Cloud
chmod +x scripts/*.sh
./scripts/deploy-filter-service.sh
```

### 3. 启动数据生产者

```bash
python3 services/tdms-kafka-producer-ultra.py --threads 6 --packet-size 10000
```

### 4. 验证数据流

```bash
./scripts/test-data-pipeline.sh
```

## 命令行参数

```bash
python3 services/high-speed-filter-service.py [选项]

选项:
  --brokers         Kafka 服务器地址 (默认: localhost:9092)
  --input-topic     输入主题 (默认: sample-input)
  --output-topic    输出主题 (默认: sample-output)
  --algorithm       滤波算法: lms/kalman/lowpass/bandpass (默认: lms)
  --multithread     启用多线程模式
  --workers         工作线程数 (默认: 4)
  --batch-size      Kafka 批量拉取大小 (默认: 500)
```

## 使用 PM2 管理

```bash
# 启动
pm2 start services/high-speed-filter-service.py \
    --name filter-service \
    --interpreter python3 \
    -- --multithread --workers 8

# 查看状态
pm2 status

# 查看日志
pm2 logs filter-service

# 停止
pm2 stop filter-service

# 开机自启
pm2 startup
pm2 save
```

## 数据流架构

```
┌─────────────────┐     ┌──────────────┐     ┌─────────────────────┐
│  TDMS 数据文件   │────▶│ sample-input │────▶│ high-speed-filter   │
│  (floatdata/)   │     │   (Kafka)    │     │   -service.py       │
└─────────────────┘     └──────────────┘     └──────────┬──────────┘
                                                        │
                                                        ▼
┌─────────────────┐     ┌──────────────┐     ┌─────────────────────┐
│   前端页面       │◀────│ WebSocket    │◀────│   sample-output     │
│ FilterMonitor   │     │   Bridge     │     │     (Kafka)         │
└─────────────────┘     └──────────────┘     └─────────────────────┘
```

## 性能预期

| 配置 | 预期处理速度 |
|------|-------------|
| 单线程 | 50-100 K/s |
| 4 线程 | 150-300 K/s |
| 8 线程 | 300-500 K/s |

## 故障排除

### 服务无法启动

```bash
# 检查依赖
python3 -c "import numpy; import kafka; from scipy import signal; print('OK')"

# 检查 Kafka
kafkacat -b localhost:9092 -L
```

### 处理速度慢

1. 启用多线程: `--multithread --workers 8`
2. 增加批量大小: `--batch-size 1000`
3. 检查 CPU 使用率: `top`

### 前端无数据

1. 检查 WebSocket Bridge: `pm2 logs websocket-bridge`
2. 检查 Kafka 输出: `kafkacat -b localhost:9092 -t sample-output -C -c 5`
3. 检查滤波服务日志: `tail -f logs/filter-service.log`
