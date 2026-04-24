# 环境启动文档（从零开始）

## 前置条件

| 工具 | 最低版本 | 验证命令 |
|------|---------|---------|
| Docker | 29+ | `docker --version` |
| Java | 17 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Python 3 | 3.9+ | `python3 --version` |
| kafka-python | — | `pip3 install kafka-python` |
| nptdms | — | `pip3 install nptdms` |

TDMS 数据文件应存放于 `floatdata/data/`（相对于仓库根目录），模拟器脚本会自动读取。当前保留 5 个设备的数据文件（data-10-left、data-10-right、data-15-right、data-20-right、data2023-left），其余已备份至 `floatdata/data_backup/`。

文件结构依赖：

```
yudao-module-detection/
├── docker-compose-infra.yml
├── config/explorer.toml          ← TDengine Explorer 配置（已修正 cluster 地址）
├── sql/init_tdengine_v2.sql
└── scripts/
```

---

## 第一阶段：基础设施启动

所有命令均在 `yudao-module-detection/` 目录下执行：

```bash
cd /Users/dingsaier/Desktop/cw_cloud/yudao-cloud/yudao-module-detection
```

### 1. 启动全部容器

```bash
docker compose -f docker-compose-infra.yml up -d
```

等待约 60 秒，Kafka 健康检查通过后 `kafka-init` 会自动创建 4 个 topic（其中 `raw_topic` 为 **5 partitions**，其余 8 partitions）。

### 2. 确认容器状态

```bash
docker compose -f docker-compose-infra.yml ps
```

正常状态：

| 容器 | 状态 |
|------|------|
| `detection-kafka` | Up (healthy) |
| `detection-kafka-ui` | Up |
| `detection-tdengine` | Up (healthy) |
| `detection-flink-jobmanager` | Up |
| `detection-flink-taskmanager` | Up |
| `detection-kafka-init` | Exited (0) — 正常，一次性初始化容器 |

### 3. 初始化 TDengine 数据库

> **重要**：`taos -f` 不支持多行 SQL 语句，已将 SQL 文件改为单行格式。

```bash
docker cp sql/init_tdengine_v2.sql detection-tdengine:/tmp/init.sql
docker exec detection-tdengine taos -f /tmp/init.sql
```

如果 `taos -f` 仍然报错，使用以下备用方案（直接逐条执行）：

```bash
docker exec detection-tdengine taos -s "DROP DATABASE IF EXISTS yudao_detection; CREATE DATABASE yudao_detection KEEP 3650 DURATION 30 BUFFER 256 WAL_LEVEL 1 PRECISION 'ns';"

docker exec detection-tdengine taos -s "USE yudao_detection; CREATE STABLE IF NOT EXISTS raw_data (ts TIMESTAMP, voltage FLOAT, sampling INT, seq INT) TAGS (device_id NCHAR(32), channel_id TINYINT);"

docker exec detection-tdengine taos -s "USE yudao_detection; CREATE STABLE IF NOT EXISTS filtered_data (ts TIMESTAMP, voltage FLOAT, seq INT) TAGS (device_id NCHAR(32), channel_id TINYINT);"

docker exec detection-tdengine taos -s "USE yudao_detection; CREATE STABLE IF NOT EXISTS feature_data (ts TIMESTAMP, amplitude FLOAT, energy FLOAT, area FLOAT, skewness FLOAT, rise_time FLOAT, hit_duration FLOAT, counts INT, ra FLOAT, af FLOAT, is_error TINYINT, error_type NCHAR(32), alert_level TINYINT, loc_x FLOAT, loc_y FLOAT, seq INT) TAGS (device_id NCHAR(32), channel_id TINYINT);"

docker exec detection-tdengine taos -s "USE yudao_detection; CREATE STABLE IF NOT EXISTS detection_results (ts TIMESTAMP, raw_val DOUBLE, filtered_val DOUBLE, is_anomaly TINYINT) TAGS (channel_name BINARY(64));"
```

### 4. 验证 TDengine 超级表

```bash
docker exec detection-tdengine taos -s "USE yudao_detection; SHOW STABLES;"
```

应看到 4 行：`raw_data`、`filtered_data`、`feature_data`、`detection_results`。

### 5. 验证 Kafka Topics

```bash
docker exec detection-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

应看到：`anomaly_topic`、`exception_topic`、`filtered_topic`、`raw_topic`。

验证 `raw_topic` 分区数：

```bash
docker exec detection-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --describe --topic raw_topic | head -1
```

应显示 `PartitionCount: 5`。

### 6. 验证 Web 界面

| 服务 | 地址 | 账号 |
|------|------|------|
| Kafka UI | http://localhost:8089 | 无需登录 |
| Flink Dashboard | http://localhost:8081 | 无需登录 |
| TDengine Explorer | http://localhost:6060 | root / taosdata |

---

## 第一阶段验证清单

| 检查项 | 验证命令 | 期望结果 |
|--------|---------|---------|
| 容器全部运行 | `docker compose -f docker-compose-infra.yml ps` | 5 个容器 Up |
| Kafka 4 个 topic | `docker exec detection-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list` | 4 个 topic，raw_topic 为 5 partitions |
| TDengine 4 张超级表 | `docker exec detection-tdengine taos -s "USE yudao_detection; SHOW STABLES;"` | 4 row(s) |
| Kafka UI 可访问 | 浏览器打开 http://localhost:8089 | 页面正常 |
| Flink Dashboard 可访问 | 浏览器打开 http://localhost:8081 | 页面正常 |
| TDengine Explorer 可登录 | 浏览器打开 http://localhost:6060 | root/taosdata 登录成功 |

全部通过后，进入第二阶段。

---

## 完全重置

如需从零重来（清除所有数据和容器）：

```bash
cd /Users/dingsaier/Desktop/cw_cloud/yudao-cloud/yudao-module-detection
docker compose -f docker-compose-infra.yml down -v
```

然后从「第一阶段」重新开始。
