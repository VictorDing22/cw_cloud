# 第二阶段：原始数据归档层

> 前置条件：`STARTUP-README.md` 第一阶段已完成，所有容器运行中。

```bash
cd /Users/dingsaier/Desktop/cw_cloud/yudao-cloud/yudao-module-detection
```

---

## 优化配置说明

| 参数 | 值 | 说明 |
|---|---|---|
| `raw_topic` partitions | **5** | 与 5 个保留设备一一对应，MD5 哈希零碰撞 |
| Flink 并行度 | **5** | 与 partition 数对齐，每个 subtask 处理一个分区 |
| TDengine batchSize | **16000** | 减少 REST 请求频次，提升写入吞吐 |
| Checkpoint 间隔 | **30s** | 保障 Kafka offset 正常提交，支持断点续跑 |

保留的 5 个测试设备及其分区映射：

| 设备 | Kafka Partition |
|---|---|
| DATA-15-RIGHT | 0 |
| DATA-10-LEFT | 1 |
| DATA-20-RIGHT | 2 |
| DATA2023-LEFT | 3 |
| DATA-10-RIGHT | 4 |

---

## 1. 一键构建 + 部署 + 提交 Flink Job

```bash
bash scripts/deploy-flink-job.sh
```

脚本自动完成：构建 fat JAR → 创建目录 → 复制到 Flink 容器 → 提交 Job → 打印运行状态。

看到 `signal-saveraw-flink-job (RUNNING)` 即成功，也可在 http://localhost:8081 确认。

---

## 2. 启动 8 MB/s 压测

```bash
python3 scripts/simulate_edge_device.py --interval 0 --burst 0
```

模拟器持续读取 `floatdata/data/` 下的 5 个设备数据并循环发送，稳定吞吐约 **8.4 MB/s**。按 `Ctrl+C` 停止。

若只需短时验证（约 26 秒）：

```bash
python3 scripts/simulate_edge_device.py --interval 0 --burst 200
```

---

## 3. 实时监控 Kafka lag

新开一个终端，每 10 秒采样一次：

```bash
watch -n 10 "docker exec detection-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group signal-saveraw-flink-job 2>/dev/null \
  | grep raw_topic | sort -k3 -n \
  | awk '{printf \"  P%s  end=%-8s committed=%-8s lag=%s\n\",\$3,\$5,\$4,\$6}'"
```

或单次查询：

```bash
docker exec detection-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group signal-saveraw-flink-job 2>/dev/null | grep raw_topic | sort -k3 -n
```

### 读懂监控数据：锯齿波是正常现象

lag 会呈现**先升后降**的周期性锯齿波：

```
lag
20k │    ▲         ▲
10k │   / \  ck   / \  ck
 0k │  /   ▼     /   ▼
    └─────────────────────→ 时间
      T+0 T+20 T+30 T+40 T+50
             ↑checkpoint 每 30s 提交一次
```

- **lag 上升**：两次 checkpoint 之间 `committed offset` 不动，Flink 正在处理但尚未提交
- **lag 骤降**：checkpoint 完成，`committed` 跳跃式更新，积压清除
- **波谷持续下降**：JVM JIT 热身后处理能力超过输入速率，系统在追消积压

### 冷启动（前 60 秒）

Flink 刚启动时 JVM 未经 JIT 编译，前 30-60 秒 lag 会快速积累至 2-3 万条，属正常现象，JVM 热身后自动收敛。

### 判断是否真的卡住

| 现象 | 判断 |
|---|---|
| lag 上升 10-20s 后骤降 | ✅ 正常，checkpoint 在工作 |
| lag 只升不降超过 3 分钟 | ❌ TDengine 写入异常，检查日志 |
| committed offset 超过 5 分钟不动 | ❌ checkpoint 超时，检查 Flink UI |
| 停止发送后 lag 在 30s 内清零 | ✅ 系统健康 |
| Flink UI Source 短暂 100% busy | ✅ 正常，checkpoint flush 期间现象 |

---

## 4. 验证 TDengine 写入

压测运行 1-2 分钟后：

```bash
# 查看总记录数
docker exec detection-tdengine taos -s \
  "USE yudao_detection; SELECT COUNT(*) FROM raw_data;"

# 查看子表列表
docker exec detection-tdengine taos -s \
  "USE yudao_detection; SHOW TABLES;"

# 查看最新 5 条
docker exec detection-tdengine taos -s \
  "USE yudao_detection; SELECT * FROM raw_data ORDER BY ts DESC LIMIT 5;"
```

| 检查项 | 期望 |
|---|---|
| 总记录数 | 随时间持续增长 |
| 子表 | `t_data_10_left_1/2/3`、`t_data_10_right_*` 等 |
| 时间戳间隔 | 500ns |
| 电压值 | 小数格式（非科学记号） |

---

## 回退到第一阶段

```bash
# 取消所有 Flink Job
docker exec detection-flink-jobmanager /opt/flink/bin/flink list 2>/dev/null \
  | grep RUNNING | awk '{print $4}' \
  | xargs -I{} docker exec detection-flink-jobmanager /opt/flink/bin/flink cancel {}

# 清理 Flink 容器内 JAR
docker exec detection-flink-jobmanager rm -rf /opt/flink/usrlib/
docker exec detection-flink-taskmanager rm -rf /opt/flink/usrlib/

# 清空 TDengine 数据（保留表结构）
docker exec detection-tdengine taos -s \
  "DROP DATABASE IF EXISTS yudao_detection; \
   CREATE DATABASE yudao_detection KEEP 3650 DURATION 30 BUFFER 256 WAL_LEVEL 1 PRECISION 'ns';"
docker cp sql/init_tdengine_v2.sql detection-tdengine:/tmp/init.sql
docker exec detection-tdengine taos -f /tmp/init.sql
```

回退完成后回到第一阶段状态，可重新执行本文档。
