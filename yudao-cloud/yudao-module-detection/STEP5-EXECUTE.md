# 第五阶段：异常数据应用层 — 实时告警展示

> 前置条件：`STEP4-EXECUTE.md` 第四阶段已完成，4 个 Flink Job 均为 RUNNING。

```bash
cd /Users/dingsaier/Desktop/cw_cloud/yudao-cloud/yudao-module-detection
```

---

## 本阶段改动

| 组件 | 改动 | 说明 |
|------|------|------|
| websocket-bridge | 增加 `anomaly_topic` 消费 | 实时推送 `anomaly-alert` JSON 到前端 |
| docker-compose-infra.yml | ws-bridge 增加 `ANOMALY_TOPIC` 环境变量 | 告知 bridge 订阅该 topic |
| detection/index.vue | 新增「信息源检测」Tab | 连接 ws-bridge:8083，实时四图 + 告警栏 |
| AnomalyAlertBar.vue | 新建组件 | 展示 Step4 三级告警（关注/预警/报警） |

---

## 1. 重建 WebSocket Bridge

```bash
docker compose -f docker-compose-infra.yml up -d --build websocket-bridge
```

验证已订阅 anomaly_topic：

```bash
docker logs detection-ws-bridge --tail 5
# 应看到: topics=['filtered_topic', 'raw_topic', 'anomaly_topic']
```

---

## 2. 压测 + 验证全链路

```bash
python3 scripts/simulate_edge_device.py --filter "data-10-left" --burst 200
```

---

## 3. 验证告警数据

```bash
docker exec detection-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic anomaly_topic \
  --from-beginning --timeout-ms 5000 2>/dev/null | head -3
```

应看到包含 `errorType`、`alertLevel` 的 JSON 告警消息。

---

## 4. 前端验证

1. 启动前端 dev server：`npm run dev`（在 `yudao-ui-admin-vue3` 目录）
2. 打开 http://localhost:1888 → 侧边栏「信息源检测」
3. 切换到「信息源检测」Tab，应自动连接 WebSocket
4. 运行压测脚本后：
   - 告警栏：显示实时三级告警（关注/预警/报警），含 errorType + 能量值 + 监测值
   - 四图：时域波形、FFT、误差分布、直方图实时更新

---

## 可观测

| 服务 | 地址 |
|------|------|
| Flink Dashboard | http://localhost:8081 |
| Kafka UI | http://localhost:8089 |
| TDengine Explorer | http://localhost:6060 (root/taosdata) |
| WebSocket 波形监控 | http://localhost:8083/ |
| 前端 Detection 页面 | http://localhost:1888 → 信息源检测 |

---

## 回退到第四阶段

无需回退 Flink Job，仅重建 ws-bridge 即可：

```bash
docker compose -f docker-compose-infra.yml up -d --build websocket-bridge
```
