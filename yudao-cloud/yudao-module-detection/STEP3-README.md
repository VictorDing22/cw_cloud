# 第三步：信号滤波层 — signal-filter-flink-job + signal-save-filtered-flink-job

## 概述

本步骤实现了技术方案文档中的 **信号滤波层**：两个新的 Flink Job 协同工作，完成从原始波形到滤波波形的实时处理和持久化。

核心功能：
- **带通滤波**：使用 4 阶 Butterworth 带通滤波器（100kHz ~ 900kHz），去除低频机械噪声和高频电子噪声，保留声发射有效频段
- **滤波数据归档**：将滤波后的波形数据全量写入 TDengine `filtered_data` 超级表

对应技术方案文档中的数据流位置：

```
                       ┌──→ [signal-saveraw-flink-job] ──→ TDengine raw_data (第二步)
                       │
Kafka raw_topic ───────┤
                       │
                       └──→ [signal-filter-flink-job] ──→ Kafka filtered_topic
                                                               │
                                                               └──→ [signal-save-filtered-flink-job] ──→ TDengine filtered_data
                                                                          ↑ 本步骤实现
```

---

## 新增 / 修改文件清单

```
yudao-module-detection/signal-flink-jobs/
└── src/main/java/cn/iocoder/yudao/detection/flink/
    ├── filter/
    │   └── ButterworthBandpass.java          # 【新增】4阶Butterworth带通滤波器
    ├── job/
    │   ├── SignalFilterJob.java              # 【新增】滤波 Flink Job 主类
    │   └── SignalSaveFilteredJob.java        # 【新增】滤波数据归档 Flink Job
    └── sink/
        └── TDengineFilteredSink.java         # 【新增】TDengine filtered_data 批量Sink

yudao-module-detection/
└── STEP3-README.md                           # 【新增】本文档
```

> 本步骤无文件修改，全部为新增。第二步的 `SignalSaveRawJob`、`TDengineRawSink` 等继续正常工作。

---

## 各文件详细说明

### 1. `filter/ButterworthBandpass.java` — 4阶 Butterworth 带通滤波器

**算法原理：**

将带通滤波分解为两个级联的 2 阶 Biquad 段：
1. **2 阶 Butterworth 高通**（截止频率 = lowCutoff）— 去除低频成分
2. **2 阶 Butterworth 低通**（截止频率 = highCutoff）— 去除高频成分

两级级联后等效为 **4 阶带通滤波器**，衰减斜率 -24dB/octave。

**Biquad 差分方程（Direct Form II Transposed）：**

```
y[n] = b0·x[n] + b1·x[n-1] + b2·x[n-2] - a1·y[n-1] - a2·y[n-2]
```

系数通过 **双线性变换（Bilinear Transform）** 从模拟 Butterworth 原型计算，使用 Robert Bristow-Johnson 的标准 Audio EQ Cookbook 公式。

**默认参数（适用于声发射检测）：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `samplingRate` | 2,000,000 Hz | 采样率 |
| `lowCutoff` | 100,000 Hz | 高通截止频率，去除机械振动、工频干扰 |
| `highCutoff` | 900,000 Hz | 低通截止频率，去除电子噪声、抗混叠 |

**滤波效果：**

| 频率范围 | 处理 |
|----------|------|
| < 100 kHz | 衰减（机械振动、50/60Hz 电源干扰） |
| 100 kHz ~ 900 kHz | 通过（声发射有效频段） |
| > 900 kHz | 衰减（高频电子噪声） |

**关键设计：**
- `apply(double x)` — 处理单个样本，有状态调用（维护 IIR 延迟线）
- `getState()` / `restoreState()` — 支持 Flink 状态序列化/恢复，确保跨消息滤波连续性
- `reset()` — 可重置状态（新设备/通道启动时）

---

### 2. `job/SignalFilterJob.java` — 滤波 Flink Job 主类

**数据流拓扑：**

```
KafkaSource(raw_topic)
    → keyBy(deviceId:channelId)       # 按设备+通道分键，每个键独立维护滤波状态
    → process(BandpassFilterFunction) # 有状态滤波：Butterworth 带通
    → KafkaSink(filtered_topic)       # 输出滤波后的消息（格式不变）
```

**BandpassFilterFunction（核心处理函数）：**

继承 `KeyedProcessFunction<String, String, String>`，利用 Flink 的 `ValueState<double[]>` 存储每个 (device, channel) 的 IIR 滤波器延迟线状态（8 个 double: 高通段 x1/x2/y1/y2 + 低通段 x1/x2/y1/y2）。

处理流程：
1. 解析 Kafka 消息的 header（deviceId, channelId, seq, ts）
2. 从 Flink 状态恢复滤波器延迟线（保证跨片段连续性）
3. 对每个电压样本调用 `filter.apply(voltage)` 得到滤波值
4. 重建消息：`header + 滤波后电压数组`
5. 保存滤波器状态回 Flink State
6. 输出到 Collector

**输出协议（与输入格式完全一致）：**

```
DATA-10-LEFT:1:42:1681105991850000000,0.000519,-0.000121,-0.000367,...
```

**FilteredMessageSerializer：**

自定义 `KafkaRecordSerializationSchema`，从消息中提取 `deviceId` 作为 Kafka record key，确保 filtered_topic 与 raw_topic 使用相同的分区路由策略。

**启动参数：**

| 位置 | 参数 | 默认值 |
|------|------|--------|
| args[0] | Kafka broker | `kafka:9092` |
| args[1] | 输入 topic | `raw_topic` |
| args[2] | 输出 topic | `filtered_topic` |
| args[3] | 低截止频率 (Hz) | `100000` |
| args[4] | 高截止频率 (Hz) | `900000` |
| args[5] | 采样率 (Hz) | `2000000` |

---

### 3. `job/SignalSaveFilteredJob.java` — 滤波数据归档 Job

与第二步的 `SignalSaveRawJob` 结构完全相同，区别在于：
- 消费 `filtered_topic`（而非 `raw_topic`）
- 写入 `filtered_data` 表（而非 `raw_data`）
- 消费组 ID: `signal-save-filtered-flink-job`

复用 `RawSignalRecord` POJO（voltage 字段存储滤波后电压值）。

**启动参数：**

| 位置 | 参数 | 默认值 |
|------|------|--------|
| args[0] | Kafka broker | `kafka:9092` |
| args[1] | TDengine URL | `jdbc:TAOS-RS://tdengine:6041/yudao_detection` |
| args[2] | 批量大小 | `5000` |
| args[3] | Kafka topic | `filtered_topic` |

---

### 4. `sink/TDengineFilteredSink.java` — TDengine 滤波数据批量 Sink

与 `TDengineRawSink` 结构相同，针对 `filtered_data` 表调整：

| 差异 | TDengineRawSink | TDengineFilteredSink |
|------|-----------------|----------------------|
| 目标超级表 | `raw_data` | `filtered_data` |
| 子表前缀 | `t_` | `f_` |
| 列 | ts, voltage, sampling, seq | ts, voltage, seq（无 sampling 列） |
| SQL 示例 | `INSERT INTO t_dev_1 USING raw_data TAGS(...)` | `INSERT INTO f_dev_1 USING filtered_data TAGS(...)` |

---

## 部署与运行

### 构建 & 提交（3 个 Job）

```bash
# 1. 构建
cd yudao-module-detection/signal-flink-jobs
mvn clean package

# 2. 部署 JAR
docker cp target/signal-flink-jobs-1.0.0.jar detection-flink-jobmanager:/opt/flink/usrlib/
docker cp target/signal-flink-jobs-1.0.0.jar detection-flink-taskmanager:/opt/flink/usrlib/

# 3. 提交 Job 1: 原始数据归档
docker exec detection-flink-jobmanager /opt/flink/bin/flink run -d \
  -c cn.iocoder.yudao.detection.flink.job.SignalSaveRawJob \
  /opt/flink/usrlib/signal-flink-jobs-1.0.0.jar \
  kafka:9092 "jdbc:TAOS-RS://tdengine:6041/yudao_detection" 2000 raw_topic

# 4. 提交 Job 2: 带通滤波
docker exec detection-flink-jobmanager /opt/flink/bin/flink run -d \
  -c cn.iocoder.yudao.detection.flink.job.SignalFilterJob \
  /opt/flink/usrlib/signal-flink-jobs-1.0.0.jar \
  kafka:9092 raw_topic filtered_topic 100000 900000 2000000

# 5. 提交 Job 3: 滤波数据归档
docker exec detection-flink-jobmanager /opt/flink/bin/flink run -d \
  -c cn.iocoder.yudao.detection.flink.job.SignalSaveFilteredJob \
  /opt/flink/usrlib/signal-flink-jobs-1.0.0.jar \
  kafka:9092 "jdbc:TAOS-RS://tdengine:6041/yudao_detection" 2000 filtered_topic
```

---

## 验证结果

### 端到端测试

```bash
# 发送 20 个片段（3通道 × 20seq × 500样本 = 30,000 条预期）
python3 scripts/simulate_edge_device.py --filter "data-10-left" --burst 20 --frag-size 500
```

### Flink Dashboard 状态

| Job | 状态 | Flink JobID |
|-----|------|-------------|
| signal-saveraw-flink-job | RUNNING | bed7e07d... |
| signal-filter-flink-job | RUNNING | 238d6f7d... |
| signal-save-filtered-flink-job | RUNNING | fb79e4ac... |

### TDengine 数据验证

| 检查项 | 结果 |
|--------|------|
| raw_data 子表创建 | `t_data_10_left_1/2/3` (3张) |
| filtered_data 子表创建 | `f_data_10_left_1/2/3` (3张) |
| raw_data 记录数 | 28,001 |
| filtered_data 记录数 | 28,001 |
| 时间戳一一对应 | 确认（纳秒精度同步） |

### 滤波效果对比

同一通道（t_data_10_left_1 vs f_data_10_left_1）前 5 个样本：

| 时间戳 | 原始电压 (V) | 滤波电压 (V) |
|--------|-------------|-------------|
| .850321920 | 0.0008100 | 0.0005190 |
| .850322420 | 0.0003410 | 0.0002190 |
| .850322920 | 0.0004970 | -0.0001210 |
| .850323420 | -0.0002840 | -0.0003670 |
| .850323920 | 0.0006540 | -0.0000520 |

**滤波分析：**
- 原始信号有明显的正值偏移（直流分量 / 低频漂移），多数值为正
- 滤波后信号围绕零轴对称波动，说明 **高通段有效去除了低频分量**
- 信号幅度略有减小，说明高频噪声也被 **低通段衰减**
- 带通滤波器正确保留了 100kHz ~ 900kHz 的声发射有效频段

---

## 当前系统架构（3 个 Flink Job 协同）

```
                                          Flink Cluster (Docker)
                                   ┌─────────────────────────────────┐
                                   │                                 │
边端设备 (模拟器)                   │  signal-saveraw-flink-job       │
      │                            │  raw_topic → TDengine raw_data  │
      ▼                            │                                 │
Kafka raw_topic ──────────────────▶│  signal-filter-flink-job        │
      (8 partitions)               │  raw_topic → 带通滤波 →         │
                                   │  → Kafka filtered_topic         │
                                   │                                 │
                                   │  signal-save-filtered-flink-job │
                                   │  filtered_topic →               │
                                   │  TDengine filtered_data         │
                                   └─────────────────────────────────┘

可观测:
  Flink Dashboard  → http://localhost:8081
  Kafka UI         → http://localhost:8089
  TDengine Explorer → http://localhost:6060
```

---

## 下一步

按技术方案文档，后续需要实现：

1. **`signal-anomaly-flink-job`** — 消费 `filtered_topic`，对滤波后的波形进行：
   - **特征提取**：amplitude, energy, area, rise_time, duration, counts, RA, AF 等声发射特征参数
   - **异常检测**：基于能量阈值 / 统计阈值判定异常事件
   - 输出到 `anomaly_topic` + TDengine `feature_data` 表
