# 数据处理流水线详解：从 TDMS 文件到 TDengine 入库

## 全局数据流

```
TDMS 文件 (磁盘)
    ↓ Python 模拟器: 读取 → 分组 → 切片 → 发 Kafka
Kafka raw_topic (消息队列)
    ↓ Flink Job: 消费 → 解析报文 → 展开为逐样本记录 → 批量写入
TDengine raw_data (时序数据库)
```

---

## 一、原始 TDMS 文件

### 1.1 什么是 TDMS

TDMS (Technical Data Management Streaming) 是 NI (National Instruments) 定义的二进制数据格式，用于高速数据采集系统。本项目的 TDMS 文件来自真实声发射传感器，每个文件记录了一个通道的连续电压波形。

### 1.2 文件清单

共 25 个 TDMS 文件，全部位于 `floatdata/data/`，总大小约 1.3 GB。

| 文件 | 样本数 | 采样率 | 时长 | 起始时间 | 电压范围 (V) | 大小 |
|------|--------|--------|------|---------|-------------|------|
| data-10-left-1.tdms | 24,000,000 | 2 MHz | 12.0s | 2023-04-10 05:53:11 | [-0.0108, 0.0085] | 45.8 MB |
| data-10-left-2.tdms | 18,000,000 | 2 MHz | 9.0s | 2023-04-10 05:53:32 | [-0.0112, 0.0094] | 34.3 MB |
| data-10-left-3.tdms | 22,000,000 | 2 MHz | 11.0s | 2023-04-10 05:53:57 | [-0.0120, 0.0105] | 42.0 MB |
| data-10-right-1.tdms | 24,000,000 | 2 MHz | 12.0s | 2023-04-10 05:54:26 | [-0.0108, 0.0080] | 45.8 MB |
| data-10-right-2.tdms | 42,000,000 | 2 MHz | 21.0s | 2023-04-10 05:54:45 | [-0.0131, 0.0091] | 80.1 MB |
| data-10-right-3.tdms | 24,000,000 | 2 MHz | 12.0s | 2023-04-10 05:55:13 | [-0.0111, 0.0075] | 45.8 MB |
| data-15-left-1.tdms | 32,000,000 | 2 MHz | 16.0s | 2023-04-10 05:56:57 | [-0.0118, 0.0158] | 61.0 MB |
| data-15-left-2.tdms | 24,000,000 | 2 MHz | 12.0s | 2023-04-10 05:57:20 | [-0.0100, 0.0077] | 45.8 MB |
| data-15-left-3.tdms | 22,000,000 | 2 MHz | 11.0s | 2023-04-10 05:57:42 | [-0.0108, 0.0075] | 42.0 MB |
| data-15-right-{1,2,3}.tdms | 26M / 28M / 24M | 2 MHz | 13/14/12s | 05:58:xx | ±0.01 | ~150 MB |
| data-20-left-{1,2,3}.tdms | 32M / 24M / 22M | 2 MHz | 16/12/11s | 05:59-06:00 | ±0.01~0.09 | ~149 MB |
| data-20-right-{1,2,3}.tdms | 22M / 20M / 24M | 2 MHz | 11/10/12s | 06:00-06:01 | ±0.01 | ~126 MB |
| data1.tdms | 50,000,000 | 2 MHz | 25.0s | 2023-04-07 11:47:08 | [-0.594, 0.610] | 95.4 MB |
| data2023.tdms | 46,000,000 | 2 MHz | 23.0s | 2023-04-10 05:46:29 | [-0.020, 0.018] | 87.8 MB |
| data2023-left-{2,3}.tdms | 24M / 26M | 2 MHz | 12/13s | 05:47-05:48 | ±0.01~0.02 | ~95 MB |
| data2023-right-{1,2,3}.tdms | 26M / 36M / 28M | 2 MHz | 13/18/14s | 05:48-05:49 | ±0.01 | ~172 MB |

### 1.3 单个 TDMS 文件内部结构

以 `data-10-left-1.tdms` 为例：

```
TDMS 文件
└── Group: "_unnamedTask<7>"
    └── Channel: "Dev2/ai1"
        ├── Properties:
        │   ├── wf_start_time: 2023-04-10T05:53:11.850322  ← 采集起始时间
        │   ├── wf_increment:  0.0000005 (秒)               ← 采样间隔 = 500ns = 1/2MHz
        │   ├── unit_string:   "Volts"                       ← 电压单位
        │   └── NI_Scale[1]_Polynomial_Coefficients: [...]   ← 多项式校准系数
        └── Data: float64[24,000,000]                        ← 2400万个电压采样点
            [0] = 0.00080997 V
            [1] = 0.00034122 V
            [2] = 0.00049747 V
            [3] = -0.00028380 V
            ...
            [23,999,999] = -0.00153382 V
```

关键参数计算：
- **采样率** = 1 / wf_increment = 1 / 0.0000005 = **2,000,000 Hz (2 MHz)**
- **采样间隔** = 500 纳秒
- **总时长** = 24,000,000 / 2,000,000 = **12 秒**
- **起始时间戳（纳秒）** = `wf_start_time` 转为 epoch nanoseconds = **1681105991850321920**

---

## 二、设备分组

### 2.1 分组规则

模拟器按文件名正则 `^(.+?)-(\d+)\.tdms$` 自动分组：

```
文件名                    →  设备ID (device_id)      通道 (channel_id)
─────────────────────────────────────────────────────────────────────
data-10-left-1.tdms       →  DATA-10-LEFT             ch=1
data-10-left-2.tdms       →  DATA-10-LEFT             ch=2
data-10-left-3.tdms       →  DATA-10-LEFT             ch=3

data-10-right-1.tdms      →  DATA-10-RIGHT            ch=1
data-10-right-2.tdms      →  DATA-10-RIGHT            ch=2
data-10-right-3.tdms      →  DATA-10-RIGHT            ch=3

data-15-left-1.tdms       →  DATA-15-LEFT             ch=1
data-15-left-2.tdms       →  DATA-15-LEFT             ch=2
data-15-left-3.tdms       →  DATA-15-LEFT             ch=3

data-15-right-{1,2,3}     →  DATA-15-RIGHT            ch=1/2/3
data-20-left-{1,2,3}      →  DATA-20-LEFT             ch=1/2/3
data-20-right-{1,2,3}     →  DATA-20-RIGHT            ch=1/2/3

data2023-left-{2,3}       →  DATA2023-LEFT            ch=2/3  (缺 ch=1)
data2023-right-{1,2,3}    →  DATA2023-RIGHT           ch=1/2/3

data1.tdms                →  DATA1                    ch=1  (独立文件)
data2023.tdms             →  DATA2023                 ch=1  (独立文件)
```

共 **10 个设备组**，其中 6 个完整 3 通道。

### 2.2 为什么按设备分组

声发射检测中，同一试件（设备）的多个传感器（通道）需要关联分析。分组保证：
- 同一设备的所有通道数据进入 **同一 Kafka 分区**（按 deviceId 哈希）
- Flink 中按 deviceId keyBy，同一设备的数据由 **同一算子实例** 处理
- TDengine 中同一设备的子表共享相同的 `device_id` 标签

---

## 三、切片与 Kafka 发送

### 3.1 为什么要切片

一个 TDMS 文件有 2400 万个样本点，不能作为一条消息发送。按 `frag_size=1000` 切片后：

```
24,000,000 个样本 ÷ 1000 样本/片段 = 24,000 个片段 = 24,000 条 Kafka 消息
```

### 3.2 切片过程（以 data-10-left-1.tdms 为例）

```
原始数组: [v0, v1, v2, ..., v999, v1000, v1001, ..., v1999, v2000, ...]
            |_______ 片段1 _______|  |_______ 片段2 _________|  |___...

片段 1: 数组 [0:1000]      基准时间戳 = 1681105991850321920 ns
片段 2: 数组 [1000:2000]   基准时间戳 = 1681105991850821920 ns  (+500,000 ns)
片段 3: 数组 [2000:3000]   基准时间戳 = 1681105991851321920 ns  (+500,000 ns)
...
片段 24000: 数组 [23999000:24000000]
```

每个片段的基准时间戳计算：
```
frag_ts = start_ts_ns + (seq - 1) * frag_size * sample_interval_ns
        = start_ts_ns + (seq - 1) * 1000 * 500
        = start_ts_ns + (seq - 1) * 500,000 ns
```

### 3.3 Kafka 消息格式

每个切片生成一条 Kafka 消息，格式：

```
设备ID:通道号:片段序号:纳秒时间戳,电压1,电压2,电压3,...电压1000
```

实际消息示例（片段 1）：

```
DATA-10-LEFT:1:1:1681105991850321920,0.000810,0.000341,0.000497,-0.000284,0.000654,...(共1000个值)
```

- **Key**: `DATA-10-LEFT`（用于 Kafka 分区路由）
- **Value**: 上面那条完整字符串
- **大小**: ~9.8 KB / 条
- **Topic**: `raw_topic`（8 个分区）

### 3.4 一次完整发送的数据量

以 `--filter "data-10-left" --burst 50 --frag-size 1000` 为例：

```
设备 DATA-10-LEFT 有 3 个通道
每轮发送 3 条消息 (ch1 + ch2 + ch3)
发 50 轮 = 150 条 Kafka 消息
每条含 1000 个电压值
→ 总计 150,000 个采样点待入库
```

---

## 四、Flink Job 解析与展开

### 4.1 消息解析

Flink 消费一条 Kafka 消息后，`RawMessageParser` 按以下步骤解析：

```
输入: "DATA-10-LEFT:1:1:1681105991850321920,0.000810,0.000341,0.000497,-0.000284,..."
                                            ↑ 第一个逗号

Step 1: 按第一个逗号分割
  header   = "DATA-10-LEFT:1:1:1681105991850321920"
  voltages = "0.000810,0.000341,0.000497,-0.000284,..."

Step 2: header 按 ':' 拆分
  parts[0] = "DATA-10-LEFT"    → deviceId
  parts[1] = "1"               → channelId
  parts[2] = "1"               → seq (片段序号)
  parts[3] = "1681105991850321920"  → baseTimestampNs

Step 3: voltages 按 ',' 拆分为数组
  values = ["0.000810", "0.000341", "0.000497", "-0.000284", ...]
  共 1000 个值
```

### 4.2 展开为逐样本记录

1 条 Kafka 消息展开为 1000 条 `RawSignalRecord`，每条对应一个独立采样点：

```
sample_interval_ns = 1,000,000,000 / 2,000,000 = 500 ns

record[0]: ts = 1681105991850321920 + 0×500 = 1681105991850321920  voltage = 0.00080997
record[1]: ts = 1681105991850321920 + 1×500 = 1681105991850322420  voltage = 0.00034122
record[2]: ts = 1681105991850321920 + 2×500 = 1681105991850322920  voltage = 0.00049747
record[3]: ts = 1681105991850321920 + 3×500 = 1681105991850323420  voltage = -0.00028380
record[4]: ts = 1681105991850321920 + 4×500 = 1681105991850323920  voltage = 0.00065372
...
record[999]: ts = 1681105991850321920 + 999×500 = 1681105991850821420
```

每条 record 的字段：

| 字段 | 类型 | 示例 | 说明 |
|------|------|------|------|
| deviceId | String | `DATA-10-LEFT` | 设备标识 |
| channelId | int | `1` | 通道号 |
| seq | int | `1` | 片段序号 |
| timestampNs | long | `1681105991850321920` | 纳秒时间戳 |
| voltage | float | `0.00080997` | 电压值 (V) |
| samplingRate | int | `2000000` | 采样率 (Hz) |

---

## 五、TDengine 批量写入

### 5.1 缓冲与触发

`TDengineRawSink` 不是收一条写一条，而是：
- 内存缓冲区累积到 **2000 条** record，或
- 距上次写入超过 **500ms**

触发一次批量 flush。

### 5.2 分组拼 SQL

flush 时，将 buffer 中的 2000 条 record 按 `(deviceId, channelId)` 分组，拼成一条多表批量 INSERT：

```sql
INSERT INTO
  t_data_10_left_1 USING raw_data TAGS ('DATA-10-LEFT', 1) VALUES
    (1681105991850321920, 0.00080997, 2000000, 1)
    (1681105991850322420, 0.00034122, 2000000, 1)
    (1681105991850322920, 0.00049747, 2000000, 1)
    ...
  t_data_10_left_2 USING raw_data TAGS ('DATA-10-LEFT', 2) VALUES
    (1681105991850321920, -0.00203200, 2000000, 1)
    ...
  t_data_10_left_3 USING raw_data TAGS ('DATA-10-LEFT', 3) VALUES
    (1681105991850321920, 0.00120000, 2000000, 1)
    ...
```

关键细节：

| 设计点 | 做法 | 原因 |
|--------|------|------|
| 自动建表 | `USING raw_data TAGS (...)` | 子表不存在时自动创建，无需预建 |
| 子表命名 | `t_` + 设备名小写 + `_` + 通道号 | 如 `t_data_10_left_1` |
| 电压格式 | `String.format("%.8f", v)` | 避免 Java 的科学记号 `8.1E-4`，TDengine 不认 |
| 时间精度 | 纳秒 (ns) | 2MHz 采样率下 500ns 间隔，毫秒会导致时间戳重复覆盖 |

### 5.3 TDengine 表结构

```
超级表 raw_data:
├── 列 (每行一个采样点):
│   ├── ts        TIMESTAMP   ← 纳秒精度时间戳
│   ├── voltage   FLOAT       ← 电压值 (V)
│   ├── sampling  INT         ← 采样率 (2000000)
│   └── seq       INT         ← 片段序号
├── 标签:
│   ├── device_id  NCHAR(32)  ← 设备ID
│   └── channel_id TINYINT    ← 通道号
└── 子表 (自动创建):
    ├── t_data_10_left_1   (device_id='DATA-10-LEFT',  channel_id=1)
    ├── t_data_10_left_2   (device_id='DATA-10-LEFT',  channel_id=2)
    ├── t_data_10_left_3   (device_id='DATA-10-LEFT',  channel_id=3)
    ├── t_data_10_right_1  (device_id='DATA-10-RIGHT', channel_id=1)
    └── ...每个 TDMS 文件对应一个子表
```

---

## 六、完整数据旅程示例

以 `data-10-left-1.tdms` → 发 50 个片段为例，跟踪一条数据从磁盘到数据库：

```
磁盘: data-10-left-1.tdms
  └── 样本 [0] = 0.00080997 V, 时间 = 2023-04-10T05:53:11.850322 (纳秒精度)

      ↓ Python 读取 & 切片 (frag_size=1000)

Kafka raw_topic, 分区 3:
  └── key="DATA-10-LEFT"
      value="DATA-10-LEFT:1:1:1681105991850321920,0.000810,0.000341,...(1000个值)"
      ~9.8 KB

      ↓ Flink RawMessageParser 展开

RawSignalRecord:
  └── deviceId="DATA-10-LEFT", channelId=1, seq=1
      timestampNs=1681105991850321920, voltage=0.00080997, samplingRate=2000000

      ↓ TDengineRawSink 缓冲 2000 条后批量写入

TDengine 子表 t_data_10_left_1:
  └── ts=2023-04-10 05:53:11.850321920  voltage=0.00080997  sampling=2000000  seq=1
```

### 数量关系

```
1 个 TDMS 文件 (24M 样本)
    = 24,000 条 Kafka 消息 (frag_size=1000)
    = 24,000,000 条 RawSignalRecord
    = 24,000,000 行 TDengine 记录
    = 12,000 次 TDengine 批量 INSERT (batchSize=2000)

全部 25 个 TDMS 文件:
    = ~674,000,000 个采样点
    = ~1.3 GB 原始 TDMS
    → ~674,000 条 Kafka 消息
    → ~6.74 亿行 TDengine 记录
```
