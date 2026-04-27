# 第三步：滤波预处理层 — 微服务滤波 + 波形实时推送

> 前置条件：`STEP2-EXECUTE.md` 第二步已完成，signal-saveraw-flink-job 正常运行。

```bash
cd /Users/dingsaier/Desktop/cw_cloud/yudao-cloud/yudao-module-detection
```

---

## 概述

本步骤实现技术方案文档 **（3）滤波预处理层**：

> signal-filter-flink-Job 消费 raw_topic 原始数据，通过异步调用滤波微服务，完成带通滤波、去噪、基线校正等预处理，将滤波后的干净波形写入 filtered_topic；signal-save-filtered-flink-job 监听 filtered_topic，将滤波后数据归档至 filtered_data 表。存储失败的数据写入 exception_topic 做后续处理。

采用 **方案 B（微服务滤波）**：Flink Job 通过异步 HTTP 调用 `filter-gateway` 网关，网关路由到盛老师的 Kalman/RLS/LS 滤波 API。同时部署 **WebSocket Bridge** 将滤波结果实时推送到前端 detection 页面进行波形展示。

```
                        ┌──→ [signal-saveraw-flink-job] → TDengine raw_data (第二步)
                        │
Kafka raw_topic ────────┤
   (5 partitions)       │
                        └──→ [signal-filter-flink-job]
                               │
                               │ Flink AsyncDataStream (100 并发)
                               │ HTTP POST → filter-gateway:8010/filter
                               ▼
                         filter-gateway (Docker)
                            ├─ Kalman → 49.235.44.231:8000
                            ├─ RLS    → 49.235.44.231:8001
                            └─ LS     → 49.235.44.231:8002
                               │
                         ┌─────┘ 滤波成功
                         │              滤波失败 / 超时
                         ▼                    ▼
                   Kafka filtered_topic   Kafka exception_topic
                     (5 partitions)          (4 partitions)
                         │
                    ┌────┴────┐
                    ▼         ▼
    [signal-save-filtered]   [websocket-bridge]
            │                      │
            ▼                      ▼
    TDengine filtered_data    ws://localhost:8083
                              → detection 页面波形展示
```

---

## Kafka Topic 配置

沿用第一步创建的 topic，无需额外操作：

| Topic | Partitions | 用途 |
|-------|-----------|------|
| `raw_topic` | 5 | 原始数据（第二步已在消费） |
| `filtered_topic` | 5 | 滤波后数据（与 raw_topic 对齐，避免空分区） |
| `exception_topic` | 4 | 异常死信队列（滤波失败 + 存储失败） |

---

## 新增 / 修改文件清单

```
yudao-module-detection/
├── docker-compose-infra.yml                          # 【修改】新增 filter-gateway + websocket-bridge
├── docker/
│   └── websocket-bridge/
│       ├── websocket_bridge.py                       # 【新增】CSV→JSON WebSocket 波形推送
│       └── Dockerfile                                # 【新增】
├── signal-flink-jobs/
│   ├── pom.xml                                       # 【修改】新增 Jackson 依赖
│   └── src/main/java/cn/iocoder/yudao/detection/flink/
│       └── job/
│           └── SignalFilterJob.java                  # 【修改】重写为异步 HTTP 调用 filter-gateway
└── STEP3-README.md                                   # 【修改】本文档

k8s-flink-solution/docker/filter-gateway/             # 【引用】滤波网关（无修改，docker-compose 直接引用）
```

---

## 各组件详细说明

### 1. `SignalFilterJob.java` — 微服务滤波 Flink Job

**数据流拓扑：**

```
KafkaSource(raw_topic)
  → AsyncDataStream.unorderedWait(AsyncFilterFunction, 30s, 100并发)
      ├─ 成功: 滤波后 CSV 消息
      └─ 失败: ERROR_PREFIX 标记
  → process(ErrorSplitter)
      ├─ 正常流 → KafkaSink(filtered_topic)
      └─ 异常流 → KafkaSink(exception_topic)
```

**AsyncFilterFunction 处理流程：**

1. 解析 CSV 消息头（`deviceId:channel:seq:ts`）
2. 提取电压数组 → 构建 JSON 请求 `{"signal": [...], "filter_type": "kalman"}`
3. HTTP POST → `filter-gateway:8010/filter`
4. 解析响应中的 `filtered_signal` 数组
5. 重建 CSV 消息：原始 header + 滤波后电压值
6. 失败/超时 → 包装为 `ExceptionMessages` 错误 JSON → exception_topic

**关键设计：**

- 专用线程池（`max(8, CPU*2)` 线程）执行 HTTP 调用，避免阻塞 Flink 算子
- 最大 100 个并发异步请求（Flink 背压自动调节）
- 30 秒超时，超时消息自动进入 exception_topic
- 输出消息格式与输入完全一致，下游 signal-save-filtered-flink-job 无需修改

**启动参数：**

| 位置 | 参数 | 默认值 |
|------|------|--------|
| args[0] | Kafka broker | `kafka:9092` |
| args[1] | 输入 topic | `raw_topic` |
| args[2] | 输出 topic | `filtered_topic` |
| args[3] | filter-gateway URL | `http://filter-gateway:8010` |
| args[4] | 滤波算法类型 | `kalman` |
| args[5] | 异常 topic | `exception_topic` |

### 2. `filter-gateway`（Python FastAPI）

来自 `k8s-flink-solution/docker/filter-gateway/`，是盛老师滤波 API 的统一网关。

**核心接口：**

| 端点 | 方法 | 说明 |
|------|------|------|
| `/filter` | POST | 单通道滤波，接收 `{"signal": [...], "filter_type": "kalman"}` |
| `/filter/batch` | POST | 多通道批量滤波 |
| `/health` | GET | 健康检查 + 统计信息 |
| `/stats` | GET | 调用统计（请求数、耗时、错误数） |

**支持的滤波算法：**

| 算法 | filter_type | 后端地址 |
|------|-------------|---------|
| Kalman 滤波 | `kalman` | `http://49.235.44.231:8000/kalman/audio/run` |
| RLS 递归最小二乘 | `rls` | `http://49.235.44.231:8001/rls/audio/run` |
| LS 最小二乘 | `ls` | `http://49.235.44.231:8002/ls/audio/run` |

### 3. `SignalSaveFilteredJob.java` — 滤波数据归档 Job

消费 `filtered_topic`，解析并展开电压片段，批量写入 TDengine `filtered_data` 超级表。
存储失败的数据写入 `exception_topic`。

| 配置 | 值 |
|------|---|
| 消费 topic | `filtered_topic` |
| 消费组 ID | `signal-save-filtered-flink-job` |
| 目标超级表 | `filtered_data`（子表前缀 `f_`） |
| 批量大小 | 5000 条记录 |

### 4. `websocket-bridge` — 波形实时推送服务

从 Kafka 消费 CSV 格式波形消息，解析为 JSON 后通过 WebSocket 推送给前端。

**功能特性：**

| 特性 | 说明 |
|------|------|
| 双 topic 消费 | 同时消费 `filtered_topic` + `raw_topic`，支持原始/滤波对比展示 |
| 智能节流 | 每个 device:channel:type 最多 200ms 推送一次（5 帧/秒），避免带宽过载 |
| 订阅过滤 | 客户端可订阅特定设备/通道，未订阅时接收全部 |
| 自动清理 | 断线客户端自动注销 |

**WebSocket 推送 JSON 格式：**

```json
{
  "type": "filtered-signal",
  "deviceId": "DATA-10-LEFT",
  "channelId": 1,
  "seq": 42,
  "timestamp": 1681105991850000000,
  "samples": [0.000519, -0.000121, ...],
  "sampleCount": 1000,
  "displayCount": 500
}
```

**客户端订阅协议：**

```javascript
// 连接
const ws = new WebSocket('ws://localhost:8083/realtime');

// 订阅特定设备+通道
ws.send(JSON.stringify({
  type: 'subscribe',
  deviceId: 'DATA-10-LEFT',
  channelId: 1
}));

// 接收波形数据
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  if (data.type === 'filtered-signal') {
    // 渲染滤波后波形
    renderWaveform(data.samples);
  } else if (data.type === 'raw-signal') {
    // 渲染原始波形（对比用）
    renderRawWaveform(data.samples);
  }
};
```

---

## 部署步骤

### 1. 启动 filter-gateway + websocket-bridge

```bash
# 重建并启动新增的服务（不影响已运行的 kafka/tdengine/flink）
docker compose -f docker-compose-infra.yml up -d --build filter-gateway websocket-bridge
```

确认状态：

```bash
docker compose -f docker-compose-infra.yml ps
```

| 容器 | 状态 |
|------|------|
| `detection-filter-gateway` | Up (healthy) |
| `detection-ws-bridge` | Up |

验证 filter-gateway 可用：

```bash
curl http://localhost:8010/health
```

### 2. 构建 + 部署 Flink JAR

```bash
# 构建（包含新增的 Jackson 依赖）
bash scripts/deploy-flink-job.sh
```

### 3. 提交滤波 Flink Job

```bash
# Job 2: 微服务滤波（raw_topic → filter-gateway → filtered_topic）
docker exec detection-flink-jobmanager /opt/flink/bin/flink run -d \
  -p 5 \
  -c cn.iocoder.yudao.detection.flink.job.SignalFilterJob \
  /opt/flink/usrlib/signal-flink-jobs-1.0.0.jar \
  kafka:9092 raw_topic filtered_topic http://filter-gateway:8010 kalman exception_topic

# Job 3: 滤波数据归档（filtered_topic → TDengine filtered_data）
docker exec detection-flink-jobmanager /opt/flink/bin/flink run -d \
  -p 5 \
  -c cn.iocoder.yudao.detection.flink.job.SignalSaveFilteredJob \
  /opt/flink/usrlib/signal-flink-jobs-1.0.0.jar \
  kafka:9092 "jdbc:TAOS-RS://tdengine:6041/yudao_detection" 5000 filtered_topic exception_topic
```

> **切换滤波算法**：将最后的 `kalman` 改为 `rls` 或 `ls` 即可切换滤波策略。

### 4. 发送测试数据

```bash
# 短时测试（约 26 秒）
python3 scripts/simulate_edge_device.py --interval 0 --burst 200
```

---

## 验证

### Flink Job 运行状态

```bash
docker exec detection-flink-jobmanager /opt/flink/bin/flink list 2>/dev/null
```

| Job | 状态 |
|-----|------|
| signal-saveraw-flink-job | RUNNING |
| signal-filter-flink-job | RUNNING |
| signal-save-filtered-flink-job | RUNNING |

### filter-gateway 调用统计

```bash
curl http://localhost:8010/stats
```

应看到 `total_requests` 持续增长，`errors` 为 0。

### Kafka lag 监控

```bash
# 滤波 Job
docker exec detection-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group signal-filter-flink-job 2>/dev/null | grep raw_topic

# 归档 Job
docker exec detection-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group signal-save-filtered-flink-job 2>/dev/null | grep filtered_topic
```

### TDengine 写入验证

```bash
# filtered_data 记录数
docker exec detection-tdengine taos -s \
  "USE yudao_detection; SELECT COUNT(*) FROM filtered_data;"

# 子表列表
docker exec detection-tdengine taos -s \
  "USE yudao_detection; SHOW TABLES;" | grep "^f_"

# 最新 5 条
docker exec detection-tdengine taos -s \
  "USE yudao_detection; SELECT * FROM filtered_data ORDER BY ts DESC LIMIT 5;"
```

### exception_topic 检查

```bash
# 查看是否有异常消息（正常情况应为空）
docker exec detection-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic exception_topic \
  --from-beginning --timeout-ms 3000 2>/dev/null | head -5
```

### WebSocket 波形验证

```bash
# 命令行测试 WebSocket 连接
python3 -c "
import asyncio, websockets, json
async def test():
    async with websockets.connect('ws://localhost:8083/realtime') as ws:
        for i in range(5):
            msg = json.loads(await ws.recv())
            print(f'{msg[\"type\"]}  device={msg.get(\"deviceId\")}  ch={msg.get(\"channelId\")}  samples={msg.get(\"displayCount\")}')
asyncio.run(test())
"
```

应看到 `filtered-signal` 和 `raw-signal` 交替输出。

---

## 系统架构（3 个 Flink Job + 2 个微服务）

```
                                Flink Cluster (Docker)
                         ┌──────────────────────────────────┐
                         │                                  │
边端设备 (模拟器)         │  signal-saveraw-flink-job        │
      │                  │  raw_topic → TDengine raw_data   │
      ▼                  │                                  │
Kafka raw_topic ────────▶│  signal-filter-flink-job         │
   (5 partitions)        │  raw_topic → HTTP async ──┐      │
                         │                           │      │
                         │  signal-save-filtered     │      │
                         │  filtered_topic →         │      │
                         │  TDengine filtered_data   │      │
                         └──────────────────────┬────┘      │
                                                │           │
           ┌────────────────────────────────────┘           │
           ▼                                                │
    filter-gateway (Docker :8010)                           │
    → Kalman/RLS/LS API (49.235.44.231)                     │
           │                                                │
           ▼                                                │
    Kafka filtered_topic (8 partitions)                     │
           │                                                │
           ▼                                                │
    websocket-bridge (Docker :8083) ──→ detection 页面       │
                                                            │
                   异常流 → exception_topic                  │
                                                            │

可观测:
  Flink Dashboard    → http://localhost:8081
  Kafka UI           → http://localhost:8089
  TDengine Explorer  → http://localhost:6060
  Filter Gateway     → http://localhost:8010/health
  WebSocket          → ws://localhost:8083/realtime
```

---

## 回退到第二阶段

```bash
# 取消第三步的 Flink Job（保留 signal-saveraw-flink-job）
docker exec detection-flink-jobmanager /opt/flink/bin/flink list 2>/dev/null \
  | grep -E "signal-filter|signal-save-filtered" | awk '{print $4}' \
  | xargs -I{} docker exec detection-flink-jobmanager /opt/flink/bin/flink cancel {}

# 停止 filter-gateway + websocket-bridge
docker compose -f docker-compose-infra.yml stop filter-gateway websocket-bridge

# 清空 filtered_data（保留表结构）
docker exec detection-tdengine taos -s \
  "USE yudao_detection; DELETE FROM filtered_data;"
```

---

## 下一步

按技术方案文档 **（4）异常检测与特征计算层**，后续需要实现：

1. **signal-anomaly-flink-job** — 消费 `filtered_topic`，异步调用异常检测微服务：
   - 特征计算：amplitude, energy, area, skewness, rise_time, duration, counts, RA, AF
   - 异常判断：基于能量阈值 / AI 模型
   - 输出到 `anomaly_topic`（5 partitions）+ TDengine `feature_data` 表
2. **定位服务** — 监听 `anomaly_topic`，基于 TDOA 到达时间差算法实现缺陷定位
3. **告警/展示服务** — 监听 `anomaly_topic`，波形可视化 + 告警推送 + TDengine 归档
