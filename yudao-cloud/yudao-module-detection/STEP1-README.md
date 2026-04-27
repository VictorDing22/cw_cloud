# 第一步：数据接入层基础设施搭建

## 概述

本步骤完成了声发射工业检测平台**数据接入层**的基础设施搭建和验证。核心目标是：跳过 Netty 网关，直接用 Python 脚本模拟边端设备，将声发射波形数据发送到 Kafka `raw_topic`，为后续 Flink 流处理 Job 提供数据源。

对应技术方案文档中的位置：

```
边端设备 (模拟器替代) ──→ Kafka raw_topic ──→ [后续步骤: Flink Job 消费]
```

---

## 新增文件清单

```
yudao-module-detection/
├── docker-compose-infra.yml              # 基础设施 Docker Compose
├── sql/init_tdengine_v2.sql              # TDengine 建库建表脚本 (新版)
├── scripts/simulate_edge_device.py       # 边端设备模拟器
├── scripts/consume_raw_topic.py          # 调试用消费者
└── STEP1-README.md                       # 本文档
```

---

## 各文件说明

### 1. `docker-compose-infra.yml` — 基础设施一键启动

将 Kafka、TDengine、Flink 全部容器化部署，每个组件都有可视化 Web 页面。

**包含的服务：**

| 服务 | 镜像 | 宿主机端口 | 说明 |
|------|------|-----------|------|
| `kafka` | `apache/kafka:3.9.2` | 9092 (容器内), 9094 (宿主机) | KRaft 模式，无需 Zookeeper |
| `kafka-init` | 同上 | — | 一次性容器，自动创建 4 个 topic 后退出 |
| `kafka-ui` | `provectuslabs/kafka-ui` | **8089** | Kafka Web 管理界面 |
| `tdengine` | `tdengine/tdengine:3.3.2.0` | 6030, 6041, **6060** | 时序数据库 + REST API + Explorer |
| `flink-jobmanager` | `flink:1.18.1` | **8081** | Flink 集群主节点 + Web Dashboard |
| `flink-taskmanager` | `flink:1.18.1` | — | Flink 工作节点，16 个 task slot |

**Kafka 监听器设计：**
- `PLAINTEXT://:9092` — 容器间通信（Flink Job、kafka-init 等容器用此地址）
- `EXTERNAL://:9094` — 宿主机访问（模拟器脚本、本地开发调试用此地址）

**自动创建的 Kafka Topics：**

| Topic | 分区数 | 用途 |
|-------|--------|------|
| `raw_topic` | 8 | 边端原始波形数据 |
| `filtered_topic` | 8 | 滤波后波形数据 |
| `anomaly_topic` | 8 | 异常检测结果 |
| `exception_topic` | 4 | 处理失败的异常数据 |

分区策略：按设备 ID 哈希取模，同一设备的所有通道数据始终落入同一分区，保证消息时序性。

**启动命令：**

```bash
cd yudao-module-detection
docker compose -f docker-compose-infra.yml up -d
```

**可视化界面：**
- Kafka UI: http://localhost:8089
- Flink Dashboard: http://localhost:8081
- TDengine Explorer: http://localhost:6060 (账号 root / 密码 taosdata)

---

### 2. `sql/init_tdengine_v2.sql` — TDengine 表结构

基于技术方案文档设计的 4 张超级表（STable），首次启动后需手动执行：

```bash
docker cp sql/init_tdengine_v2.sql detection-tdengine:/tmp/init.sql
docker exec detection-tdengine taos -f /tmp/init.sql
```

**表结构说明：**

| 超级表 | 对应流程 | 列 | 标签 |
|--------|---------|------|------|
| `raw_data` | Netty/模拟器 → Kafka → Flink 归档 | ts, voltage, sampling, seq | device_id, channel_id |
| `filtered_data` | 滤波 Flink Job 输出 | ts, voltage, seq | device_id, channel_id |
| `feature_data` | 异常检测 Flink Job 输出 | ts, amplitude, energy, area, skewness, rise_time, hit_duration, counts, ra, af, is_error, error_type, alert_level, loc_x, loc_y, seq | device_id, channel_id |
| `detection_results` | 旧版兼容 | ts, raw_val, filtered_val, is_anomaly | channel_name |

> 注：`duration` 和 `level` 是 TDengine 保留关键字，分别改名为 `hit_duration` 和 `alert_level`。

---

### 3. `scripts/simulate_edge_device.py` — 边端设备模拟器（真实 TDMS 回放）

读取 `floatdata/data/` 目录下的真实 TDMS 采集文件，按文件命名规则自动分组为虚拟设备+通道，切片为固定大小的片段后发送到 Kafka。

**数据来源：** `floatdata/data/*.tdms` — 真实声发射传感器采集的 2MHz 电压波形，每文件 1800 万~5000 万样本点。

**设备分组规则：** 按文件名自动识别，例如：
- `data-10-left-1.tdms`, `data-10-left-2.tdms`, `data-10-left-3.tdms` → 设备 `DATA-10-LEFT`，通道 1/2/3
- 共自动发现 10 个设备组，其中 6 个完整 3 通道

**报文格式（与技术方案一致）：**

```
deviceid:通道号:片段号:时间戳,v1,v2,v3,...
```

示例（真实数据）：

```
DATA-10-LEFT:1:1:1681105991850,0.000810,0.000341,0.000497,-0.000284,...
```

**分区路由：** 以 `deviceId` 为 key，MD5 哈希后取模，同一设备的全部通道消息落入同一 Kafka 分区。

**使用方法：**

```bash
pip install kafka-python nptdms

# 查看自动发现的设备分组（不发送数据）
python3 scripts/simulate_edge_device.py --list

# 发送所有设备的全部数据（每片段 1000 样本点）
python3 scripts/simulate_edge_device.py

# 只发 data-10-left 设备组，每片段 2000 样本
python3 scripts/simulate_edge_device.py --filter "data-10-left" --frag-size 2000

# 发 50 个片段批次后停止
python3 scripts/simulate_edge_device.py --burst 50

# 全部参数
python3 scripts/simulate_edge_device.py \
  --broker localhost:9094 \
  --data-dir ../../floatdata/data \
  --frag-size 1000 \
  --interval 0.05 \
  --burst 0 \
  --filter "data-10"
```

---

### 4. `scripts/consume_raw_topic.py` — 调试用消费者

用于验证 `raw_topic` 中的数据是否正确写入，解析报文并格式化输出。

```bash
# 读取最新消息
python3 scripts/consume_raw_topic.py

# 从头读取，最多 20 条
python3 scripts/consume_raw_topic.py --from-beginning --count 20
```

输出示例：

```
P1|OFF=     0 | dev=AE-DEVICE-002 ch=1 seq=   1 samples=  100 preview=[0.001574, 0.002675, 0.002804, -0.000608, 0.00158]
P1|OFF=     1 | dev=AE-DEVICE-002 ch=2 seq=   1 samples=  100 preview=[-0.002032, 0.001534, -0.001511, 0.000141, -0.00505]
```

---

## 验证结果

| 检查项 | 状态 |
|--------|------|
| Kafka 容器运行 + 健康检查通过 | OK |
| 4 个 topic 自动创建（raw_topic 8 分区） | OK |
| TDengine 容器运行 + 4 张超级表创建 | OK |
| Flink JobManager + TaskManager 运行 | OK |
| Kafka UI (8089) 可访问 | OK |
| Flink Dashboard (8081) 可访问 | OK |
| TDengine Explorer (6060) 可访问 | OK |
| 模拟器发送 → Kafka → 消费者读取 端到端通过 | OK |

---

## 下一步

按技术方案文档，下一步需要实现：

1. **`signal-saveraw-flink-job`** — 消费 `raw_topic`，将原始数据全量归档至 TDengine `raw_data` 表
2. **`signal-filter-flink-job`** — 消费 `raw_topic`，调用滤波服务，输出到 `filtered_topic`
3. **`signal-anomaly-flink-job`** — 消费 `filtered_topic`，特征计算 + 异常检测，输出到 `anomaly_topic`
