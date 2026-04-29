# 第四阶段：异常检测与特征计算层

> 前置条件：`STEP3-EXECUTE.md` 第三阶段已完成，所有容器运行中。

```bash
cd /Users/dingsaier/Desktop/cw_cloud/yudao-cloud/yudao-module-detection
```

---

## 配置说明

| 参数 | 值 | 说明 |
|---|---|---|
| TaskManager slots | **20** | 4 Job x 5 并行度 = 20 slots |
| AMP_THRESHOLD | **0.009** | 峰值阈值（mean+3σ, GB/T 18182） |
| ENERGY_THRESHOLD | **0.005** | 能量阈值（mean+3σ） |
| COUNTS_THRESHOLD | **72** | 振铃计数阈值（mean+2σ） |
| 告警分级 | **3 级** | L1 关注(>3σ) / L2 预警(>4σ) / L3 报警(>5σ) |
| 特征计算 | **Flink 内** | 纯 Java 计算，零 HTTP 调用 |

---

## 1. 更新基础设施

```bash
docker compose -f docker-compose-infra.yml up -d flink-taskmanager
```

确认 TaskManager 重启并 slot 数更新为 20。

---

## 2. 确认 feature_data 表已创建

```bash
docker exec detection-tdengine taos -s \
  "USE yudao_detection; DESCRIBE feature_data;"
```

如未创建，执行建表：

```bash
docker cp sql/init_tdengine_v2.sql detection-tdengine:/tmp/init.sql
docker exec detection-tdengine taos -f /tmp/init.sql
```

---

## 3. 一键构建 + 部署 + 提交 4 个 Flink Job

```bash
bash scripts/deploy-flink-job.sh --step4
```

看到 4 个 Job 均为 `RUNNING` 即成功：
- signal-saveraw-flink-job
- signal-filter-flink-job
- signal-save-filtered-flink-job
- signal-anomaly-flink-job

调整异常阈值（可选）：

```bash
AMP_THRESHOLD=0.3 ENERGY_THRESHOLD=5.0 bash scripts/deploy-flink-job.sh --step4
```

---

## 4. 压测

```bash
python3 scripts/simulate_edge_device.py --interval 0 --burst 200
```

---

## 5. 验证

```bash
# feature_data 入库条数
docker exec detection-tdengine taos -s "USE yudao_detection; SELECT COUNT(*) FROM feature_data;"

# anomaly_topic 异常消息
docker exec detection-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic anomaly_topic \
  --from-beginning --timeout-ms 5000 2>/dev/null | head -3
```

波形监控：http://localhost:8083/

---

## 可观测

| 服务 | 地址 |
|------|------|
| Flink Dashboard | http://localhost:8081 |
| Kafka UI | http://localhost:8089 |
| TDengine Explorer | http://localhost:6060 (root/taosdata) |
| Kalman Service | http://localhost:8000/health |
| WebSocket 波形监控 | http://localhost:8083/ |

---

## 回退到第三阶段

```bash
bash scripts/deploy-flink-job.sh --step3
```
