# 信号检测平台 — 项目执行手册

```bash
cd /Users/dingsaier/Desktop/cw_cloud/yudao-cloud/yudao-module-detection
```

## 前置条件

| 工具 | 验证命令 |
|------|---------|
| Docker 29+ | `docker --version` |
| Java 17 | `java -version` |
| Maven 3.9+ | `mvn -version` |
| Python 3.9+ | `pip3 install kafka-python nptdms` |

TDMS 数据文件放在 `floatdata/data/`（仓库根目录下）。

---

## 一、启动

```bash
# 启动基础设施（Kafka、TDengine、Flink、Kalman、WebSocket Bridge）
docker compose -f docker-compose-infra.yml up -d --build

# 等 Kafka 就绪（约 60s），初始化 TDengine 表
docker cp sql/init_tdengine_v2.sql detection-tdengine:/tmp/init.sql
docker exec detection-tdengine taos -f /tmp/init.sql

# 构建并部署全部 4 个 Flink Job
bash scripts/deploy-flink-job.sh --all
```

全部启动完成。打开 http://localhost:8083/monitor 即可看到实时监控页面（波形、告警、特征图表）。

---

## 二、运行数据

```bash
# 短时验证（约 26 秒自动停止）
python3 scripts/simulate_edge_device.py --interval 0 --burst 200

# 持续压测（Ctrl+C 停止）
python3 scripts/simulate_edge_device.py --interval 0 --burst 0

# 只跑某个设备
python3 scripts/simulate_edge_device.py --filter "data-10-left" --burst 200
```

---

## 三、验证

```bash
# Flink 4 个 Job 都在跑
bash scripts/deploy-flink-job.sh --status

# TDengine 三张表都有数据
docker exec detection-tdengine taos -s "USE yudao_detection; SELECT COUNT(*) FROM raw_data;"
docker exec detection-tdengine taos -s "USE yudao_detection; SELECT COUNT(*) FROM filtered_data;"
docker exec detection-tdengine taos -s "USE yudao_detection; SELECT COUNT(*) FROM feature_data;"

# Kafka 异常告警消息（应有 JSON 输出）
docker exec detection-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic anomaly_topic \
  --from-beginning --timeout-ms 5000 2>/dev/null | head -3
```

| 检查项 | 期望 |
|--------|------|
| `--status` | 4 个 Job 均 RUNNING |
| raw_data | 记录数 > 0 |
| filtered_data | 记录数 > 0 |
| feature_data | 记录数 > 0 |
| anomaly_topic | 有 errorType / alertLevel 的 JSON |
| http://localhost:8081 | Flink Dashboard |
| http://localhost:8089 | Kafka UI |
| http://localhost:6060 | TDengine Explorer (root/taosdata) |
| http://localhost:8083/monitor | 实时监控平台（波形+告警+特征） |
| http://localhost:8000/health | Kalman 服务 healthy |

---

## 四、日常操作

```bash
# 查看 Job 状态
bash scripts/deploy-flink-job.sh --status

# 只更新某个 Job（不影响其他）
bash scripts/deploy-flink-job.sh --job saveraw
bash scripts/deploy-flink-job.sh --job filter
bash scripts/deploy-flink-job.sh --job savefiltered
bash scripts/deploy-flink-job.sh --job anomaly

# 调整异常阈值后重新部署 anomaly
AMP_THRESHOLD=0.3 ENERGY_THRESHOLD=5.0 bash scripts/deploy-flink-job.sh --job anomaly

# 取消单个 / 全部 Job
bash scripts/deploy-flink-job.sh --cancel anomaly
bash scripts/deploy-flink-job.sh --cancel-all

# 重启微服务
docker compose -f docker-compose-infra.yml up -d --build kalman-service websocket-bridge

# 查看 Kafka 消费 lag
docker exec detection-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group signal-saveraw-flink-job 2>/dev/null | grep raw_topic
```

---

## 五、完全重置

```bash
# 停掉一切，删除所有数据
docker compose -f docker-compose-infra.yml down -v
```

然后从「一、启动」重新开始。

---

## 数据流

```
TDMS 文件 → simulate_edge_device.py → Kafka raw_topic
  → SaveRaw Job       → TDengine raw_data
  → Filter Job        → Kafka filtered_topic
    → SaveFiltered Job → TDengine filtered_data
    → Anomaly Job      → TDengine feature_data + Kafka anomaly_topic
      → WebSocket Bridge → 前端实时展示
```

## 服务地址

| 服务 | 地址 |
|------|------|
| Flink Dashboard | http://localhost:8081 |
| Kafka UI | http://localhost:8089 |
| TDengine Explorer | http://localhost:6060 (root/taosdata) |
| Kalman 滤波服务 | http://localhost:8000/health |
| 实时监控平台 | http://localhost:8083/monitor |
| 简易波形监控 | http://localhost:8083 |
