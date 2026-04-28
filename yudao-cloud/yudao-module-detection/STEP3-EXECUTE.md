# 第三阶段：滤波预处理层

> 前置条件：`STEP2-EXECUTE.md` 第二阶段已完成，所有容器运行中。

```bash
cd /Users/dingsaier/Desktop/cw_cloud/yudao-cloud/yudao-module-detection
```

---

## 优化配置说明

| 参数 | 值 | 说明 |
|---|---|---|
| `filtered_topic` partitions | **5** | 与 raw_topic 对齐，每设备一个分区，无空分区 |
| TaskManager slots | **16** | 支撑 3 个 Job × 5 并行度 = 15 slots |
| kalman-service workers | **8** | Flink 直连，无 gateway 中间层 |
| SaveFiltered batchSize | **16** (消息数) | 与 SaveRaw 对齐，≈16K 样本/flush |
| WebSocket 节流 | **200ms** | 每 device:channel 每秒 5 帧推送 |

---

## 1. 启动微服务

```bash
docker compose -f docker-compose-infra.yml up -d --build kalman-service websocket-bridge
```

确认服务可用：

```bash
curl http://localhost:8000/health
# 返回 {"status":"healthy"} 即可
```

---

## 2. 一键构建 + 部署 + 提交 3 个 Flink Job

```bash
bash scripts/deploy-flink-job.sh --step3
```

脚本自动完成：构建 fat JAR → 部署到 Flink 容器 → 提交 SaveRaw + Filter + SaveFiltered 三个 Job → 打印运行状态。

看到 3 个 Job 均为 `RUNNING` 即成功，也可在 http://localhost:8081 确认。

Flink 现在直连 `kalman-service:8000`（跳过 filter-gateway），减少一次网络跳转和两次 JSON 序列化。

---

## 3. 启动压测

```bash
python3 scripts/simulate_edge_device.py --interval 0 --burst 200
```

持续运行（按 `Ctrl+C` 停止）：

```bash
python3 scripts/simulate_edge_device.py --interval 0 --burst 0
```

---

## 4. 验证

```bash
# filtered_data 入库条数
docker exec detection-tdengine taos -s "USE yudao_detection; SELECT COUNT(*) FROM filtered_data;"

# exception_topic 应无消息
docker exec detection-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic exception_topic \
  --from-beginning --timeout-ms 3000 2>/dev/null | head -3
```

波形监控：浏览器打开 http://localhost:8083/

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

## 回退到第二阶段

```bash
# 取消第三步的 Job（保留 signal-saveraw-flink-job）
docker exec detection-flink-jobmanager /opt/flink/bin/flink list 2>/dev/null \
  | grep -E "signal-filter|signal-save-filtered" | awk '{print $4}' \
  | xargs -I{} docker exec detection-flink-jobmanager /opt/flink/bin/flink cancel {}

# 停止微服务
docker compose -f docker-compose-infra.yml stop kalman-service websocket-bridge
```
