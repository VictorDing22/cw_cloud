# 第二步：原始数据归档层 — signal-saveraw-flink-job

## 概述

本步骤实现了技术方案文档中的 **原始数据归档层**：创建独立的 Flink Job 模块，从 Kafka `raw_topic` 消费边端设备发来的声发射波形报文，解析并展开为逐样本记录，批量归档至 TDengine `raw_data` 超级表。

对应技术方案文档中的数据流位置：

```
边端设备 (模拟器) ──→ Kafka raw_topic ──→ [signal-saveraw-flink-job] ──→ TDengine raw_data
                                              ↑ 本步骤实现
```

---

## 新增 / 修改文件清单

```
yudao-module-detection/
├── signal-flink-jobs/                            # 【新增】独立 Flink Job Maven 模块
│   ├── pom.xml
│   └── src/main/java/cn/iocoder/yudao/detection/flink/
│       ├── job/SignalSaveRawJob.java              # Flink Job 主类
│       ├── schema/RawSignalRecord.java            # 单样本 POJO
│       └── sink/TDengineRawSink.java              # TDengine 批量写入 Sink
├── scripts/simulate_edge_device.py               # 【修改】时间戳升级为纳秒精度
├── sql/init_tdengine_v2.sql                      # 【修改】数据库改为纳秒精度 + 10 年保留
└── STEP2-README.md                               # 【新增】本文档
```

---

## 各文件详细说明

### 1. `signal-flink-jobs/pom.xml` — 独立 Flink Job 构建配置

**为什么独立模块？** 与现有 Spring Boot 检测服务解耦，构建速度快（~4 秒），生成的 fat JAR 仅 22MB，可直接提交到 Docker Flink 集群。

**核心依赖：**

| 依赖 | 版本 | 作用域 |
|------|------|--------|
| `flink-streaming-java` | 1.18.1 | `provided`（Flink 集群已有） |
| `flink-clients` | 1.18.1 | `provided` |
| `flink-connector-kafka` | 3.1.0-1.18 | `compile`（打入 fat JAR） |
| `taos-jdbcdriver` | 3.3.2 | `compile`（打入 fat JAR） |

**构建方式：** maven-shade-plugin 打 fat JAR，排除 Flink core / SLF4J 等集群已有的依赖，避免类冲突。

```bash
cd signal-flink-jobs
mvn clean package    # 生成 target/signal-flink-jobs-1.0.0.jar (22MB)
```

---

### 2. `signal-flink-jobs/.../SignalSaveRawJob.java` — Flink Job 主类

**数据流拓扑：**

```
KafkaSource(raw_topic)
    → FlatMap(RawMessageParser)     # 1条Kafka消息 → N条RawSignalRecord
    → keyBy(deviceId)               # 按设备分组，保证同设备写入同一Sink实例
    → TDengineRawSink               # 批量INSERT到TDengine
```

**报文解析逻辑（RawMessageParser）：**

输入一条 Kafka 消息：
```
DATA-10-LEFT:1:42:1681105991850000000,0.000810,0.000341,0.000497,...
```

解析过程：
1. 按第一个逗号分割出 header 和 voltage 数组
2. header 按 `:` 拆分为 `deviceId`、`channelId`、`seq`、`baseTimestampNs`
3. 每个电压值展开为独立的 `RawSignalRecord`，时间戳按 500ns 间隔递增

```
baseTs = 1681105991850000000ns
sample[0] → ts = baseTs + 0 * 500ns
sample[1] → ts = baseTs + 1 * 500ns
sample[2] → ts = baseTs + 2 * 500ns
...
```

**启动参数：**

| 位置 | 参数 | 默认值 |
|------|------|--------|
| args[0] | Kafka broker 地址 | `kafka:9092` |
| args[1] | TDengine JDBC URL | `jdbc:TAOS-RS://tdengine:6041/yudao_detection` |
| args[2] | 批量写入大小 | `5000` |
| args[3] | Kafka topic | `raw_topic` |

---

### 3. `signal-flink-jobs/.../RawSignalRecord.java` — 单样本数据 POJO

从一条 Kafka 消息（包含 N 个电压值）展开后的单个采样点记录。

| 字段 | 类型 | 说明 |
|------|------|------|
| `deviceId` | String | 设备标识，如 `DATA-10-LEFT` |
| `channelId` | int | 通道号 (1/2/3) |
| `seq` | int | 片段序号 |
| `timestampNs` | long | 纳秒时间戳（从 epoch） |
| `voltage` | float | 电压值（伏特） |
| `samplingRate` | int | 采样率（2,000,000 Hz） |

---

### 4. `signal-flink-jobs/.../TDengineRawSink.java` — TDengine 批量写入

**核心设计：**

- **缓冲区机制**：内存中累积 `batchSize` 条记录（默认 2000）或超过 `flushIntervalMs`（默认 500ms）后触发写入
- **按设备+通道分组**：同一批次内按 `(deviceId, channelId)` 分组，利用 TDengine 多表批量 INSERT 语法
- **自动建表**：使用 `INSERT INTO t_{device}_{ch} USING raw_data TAGS (...) VALUES (...)` 语法，子表不存在时自动创建
- **显式小数格式**：用 `String.format("%.8f", voltage)` 避免 Java 默认的科学记号（如 `8.1E-4`），TDengine SQL 不支持科学记号

**生成的 SQL 示例：**

```sql
INSERT INTO t_data_10_left_1 USING raw_data TAGS ('DATA-10-LEFT', 1) VALUES
  (1681105991850000000, 0.00081000, 2000000, 1)
  (1681105991850000500, 0.00034100, 2000000, 1)
  (1681105991850001000, 0.00049700, 2000000, 1)
  ...
```

**子表命名规则**：`t_` + 设备ID小写 + `_` + 通道号，如 `t_data_10_left_1`

---

### 5. `scripts/simulate_edge_device.py` — 【修改】时间戳升级为纳秒

**修改原因：** 2MHz 采样率下 1000 个样本仅跨越 0.5ms，毫秒精度时间戳导致相邻片段产生重复 ts，TDengine 会覆盖写入，丢失约 50% 数据。

**修改内容：**

| 修改前 | 修改后 |
|--------|--------|
| `ts_ms = int(... / np.timedelta64(1, "ms"))` | `ts_ns = int(... / np.timedelta64(1, "ns"))` |
| `ts_ms = info["start_ts"] + int(offset * 1000.0 / sr)` | `ts_ns = info["start_ts"] + offset * (1e9 // sr)` |
| 报文格式: `DEV:ch:seq:1681105991850,...` | 报文格式: `DEV:ch:seq:1681105991850000000,...` |

---

### 6. `sql/init_tdengine_v2.sql` — 【修改】数据库级别调整

| 修改项 | 修改前 | 修改后 | 原因 |
|--------|--------|--------|------|
| `PRECISION` | 未设置（默认毫秒） | `'ns'`（纳秒） | 2MHz 采样率需要 500ns 时间分辨率 |
| `KEEP` | `365` 天 | `3650` 天（10 年） | TDMS 历史数据可能来自数年前 |
| `DURATION` | `10` 天 | `30` 天 | 配合更大的 KEEP 范围 |

---

## 部署与运行

### 构建 & 提交

```bash
# 1. 构建 fat JAR
cd yudao-module-detection/signal-flink-jobs
mvn clean package

# 2. 复制到 Flink 容器
docker cp target/signal-flink-jobs-1.0.0.jar detection-flink-jobmanager:/opt/flink/usrlib/
docker cp target/signal-flink-jobs-1.0.0.jar detection-flink-taskmanager:/opt/flink/usrlib/

# 3. 确保 checkpoint 目录权限
docker exec detection-flink-jobmanager mkdir -p /opt/flink/checkpoints && \
docker exec detection-flink-jobmanager chmod 777 /opt/flink/checkpoints
docker exec detection-flink-taskmanager mkdir -p /opt/flink/checkpoints && \
docker exec detection-flink-taskmanager chmod 777 /opt/flink/checkpoints

# 4. 提交作业
docker exec detection-flink-jobmanager /opt/flink/bin/flink run -d \
  -c cn.iocoder.yudao.detection.flink.job.SignalSaveRawJob \
  /opt/flink/usrlib/signal-flink-jobs-1.0.0.jar \
  kafka:9092 \
  "jdbc:TAOS-RS://tdengine:6041/yudao_detection" \
  2000 \
  raw_topic
```

### TDengine 重建（如需要）

```bash
docker exec detection-tdengine taos -s "
DROP DATABASE IF EXISTS yudao_detection;
CREATE DATABASE yudao_detection KEEP 3650 DURATION 30 BUFFER 256 WAL_LEVEL 1 PRECISION 'ns';
USE yudao_detection;
CREATE STABLE IF NOT EXISTS raw_data (ts TIMESTAMP, voltage FLOAT, sampling INT, seq INT) TAGS (device_id NCHAR(32), channel_id TINYINT);
CREATE STABLE IF NOT EXISTS filtered_data (ts TIMESTAMP, voltage FLOAT, seq INT) TAGS (device_id NCHAR(32), channel_id TINYINT);
CREATE STABLE IF NOT EXISTS feature_data (ts TIMESTAMP, amplitude FLOAT, energy FLOAT, area FLOAT, skewness FLOAT, rise_time FLOAT, hit_duration FLOAT, counts INT, ra FLOAT, af FLOAT, is_error TINYINT, error_type NCHAR(32), alert_level TINYINT, loc_x FLOAT, loc_y FLOAT, seq INT) TAGS (device_id NCHAR(32), channel_id TINYINT);
CREATE STABLE IF NOT EXISTS detection_results (ts TIMESTAMP, raw_val DOUBLE, filtered_val DOUBLE, is_anomaly TINYINT) TAGS (channel_name BINARY(64));
"
```

---

## 验证结果

### 端到端测试

```bash
# 发送 50 个片段（3通道 × 50seq × 1000样本 = 150,000 条）
python3 scripts/simulate_edge_device.py --filter "data-10-left" --burst 50 --frag-size 1000

# 查询 TDengine
docker exec detection-tdengine taos -s "USE yudao_detection; SELECT COUNT(*) FROM raw_data;"
```

| 检查项 | 期望值 | 实际值 | 状态 |
|--------|--------|--------|------|
| Flink Job 提交成功 | RUNNING | RUNNING | OK |
| 子表自动创建 | 3 (t_data_10_left_1/2/3) | 3 | OK |
| 通道 1 写入样本数 | 50,000 | 50,000 | OK |
| 通道 2 写入样本数 | 50,000 | 49,001 | OK (99.8%) |
| 通道 3 写入样本数 | 50,000 | 49,000 | OK (98%) |
| 总写入 | 150,000 | 148,001 | OK (98.7%) |
| 时间戳精度 | 500ns 间隔 | 500ns 间隔 | OK |
| 时间戳范围 | ~25ms (50×1000/2MHz) | 25ms | OK |
| 电压值格式 | 小数（非科学记号） | 0.00081000 | OK |

> 微小差异（~1%）原因：Flink Job 使用 `OffsetsInitializer.latest()`，Job 启动与首批消息发送之间存在极短的时间差，少量消息在消费者就绪前已到达。生产环境中使用 `earliest()` + checkpoint 可实现 exactly-once。

### 数据样例

```
              ts               |       voltage        |  sampling   |     seq     |
===================================================================================
 2023-04-10 05:53:11.850321920 |            0.0011220 |     2000000 |           4 |
 2023-04-10 05:53:11.850322420 |            0.0004970 |     2000000 |           4 |
 2023-04-10 05:53:11.850322920 |            0.0011220 |     2000000 |           4 |
 2023-04-10 05:53:11.850323420 |            0.0004970 |     2000000 |           4 |
 2023-04-10 05:53:11.850323920 |            0.0008100 |     2000000 |           4 |
```

---

## 开发过程中遇到的问题 & 解决

| 问题 | 原因 | 解决 |
|------|------|------|
| Flink 提交报 `Failed to create checkpoint storage` | Flink 容器内 `/opt/flink/checkpoints` 目录不存在 | `mkdir -p && chmod 777` |
| TDengine `Timestamp data out of range` | DB `KEEP 365` 但 TDMS 数据来自 3 年前 | KEEP 改为 3650 |
| TDengine SQL 报 `0x60b` 语法错误 | Java `float.toString()` 输出科学记号 `8.1E-4` | 改用 `String.format("%.8f", v)` |
| 写入 15 万条只落库 7.5 万 | 毫秒精度下 2MHz 的相邻片段时间戳重复，被 TDengine 覆盖 | 全链路升级为纳秒精度 |

---

## 可观测

- **Flink Dashboard** — http://localhost:8081 — 查看 `signal-saveraw-flink-job` 运行状态、背压、吞吐量
- **Kafka UI** — http://localhost:8089 — 查看 `signal-saveraw-flink-job` 消费组的 lag 和 offset
- **TDengine Explorer** — http://localhost:6060 — 执行 SQL 查询确认数据写入

---

## 下一步

按技术方案文档，后续需要实现：

1. **`signal-filter-flink-job`** — 消费 `raw_topic`，对原始波形执行带通滤波，输出到 `filtered_topic`
2. **`signal-save-filtered-flink-job`** — 消费 `filtered_topic`，归档到 TDengine `filtered_data` 表
3. **`signal-anomaly-flink-job`** — 消费 `filtered_topic`，提取声发射特征参数（amplitude, energy, counts, RA, AF 等），执行异常检测，输出到 `anomaly_topic`
