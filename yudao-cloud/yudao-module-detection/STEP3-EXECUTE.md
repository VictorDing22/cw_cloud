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
| filter-gateway 算法 | **kalman** | 可选 `rls` / `ls`，启动参数切换 |
| SaveFiltered batchSize | **16** (消息数) | 与 SaveRaw 对齐，≈16K 样本/flush |
| WebSocket 节流 | **200ms** | 每 device:channel 每秒 5 帧推送 |

---

## 1. 启动 filter-gateway + websocket-bridge

```bash
docker compose -f docker-compose-infra.yml up -d --build filter-gateway websocket-bridge
```

确认 filter-gateway 可用：

```bash
curl http://localhost:8010/health
```

应返回 `{"status":"healthy",...}`。

---

## 2. 一键构建 + 部署 + 提交 3 个 Flink Job

```bash
bash scripts/deploy-flink-job.sh --step3
```

脚本自动完成：构建 fat JAR → 部署到 Flink 容器 → 提交 SaveRaw + Filter + SaveFiltered 三个 Job → 打印运行状态。

看到 3 个 Job 均为 `RUNNING` 即成功，也可在 http://localhost:8081 确认。

切换滤波算法（默认 kalman）：

```bash
FILTER_TYPE=rls bash scripts/deploy-flink-job.sh --step3
```

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

### filter-gateway 调用统计

```bash
curl http://localhost:8010/stats
```

`total_requests` 应持续增长，`errors` 为 0。

### TDengine 写入

```bash
docker exec detection-tdengine taos -s \
  "USE yudao_detection; SELECT COUNT(*) FROM filtered_data;"
```

### exception_topic 检查

```bash
# 正常情况应无消息（盛老师 API 不可达时会有 timeout 消息）
docker exec detection-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic exception_topic \
  --from-beginning --timeout-ms 3000 2>/dev/null | head -3
```

### WebSocket 波形推送

```bash
pip3 install websockets -q 2>/dev/null
python3 -c "
import asyncio, websockets, json
async def test():
    async with websockets.connect('ws://localhost:8083/realtime') as ws:
        for i in range(5):
            msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=10))
            print(f'{msg[\"type\"]}  device={msg.get(\"deviceId\")}  ch={msg.get(\"channelId\")}  samples={msg.get(\"displayCount\")}')
asyncio.run(test())
"
```

应看到 `filtered-signal` 和 `raw-signal` 交替输出。

---

## 可观测

| 服务 | 地址 |
|------|------|
| Flink Dashboard | http://localhost:8081 |
| Kafka UI | http://localhost:8089 |
| TDengine Explorer | http://localhost:6060 (root/taosdata) |
| Filter Gateway | http://localhost:8010/health |
| WebSocket 波形 | ws://localhost:8083/realtime |

---

## 回退到第二阶段

```bash
# 取消第三步的 Job（保留 signal-saveraw-flink-job）
docker exec detection-flink-jobmanager /opt/flink/bin/flink list 2>/dev/null \
  | grep -E "signal-filter|signal-save-filtered" | awk '{print $4}' \
  | xargs -I{} docker exec detection-flink-jobmanager /opt/flink/bin/flink cancel {}

# 停止 filter-gateway + websocket-bridge
docker compose -f docker-compose-infra.yml stop filter-gateway websocket-bridge
```
