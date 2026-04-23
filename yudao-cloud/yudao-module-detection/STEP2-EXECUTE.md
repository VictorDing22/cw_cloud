# 第二阶段：原始数据归档层

> 前置条件：`STARTUP-README.md` 第一阶段已完成，所有容器运行中。

```bash
cd /Users/dingsaier/Desktop/cw_cloud/yudao-cloud/yudao-module-detection
```

---

## 1. 一键构建 + 部署 + 提交 Flink Job

```bash
bash scripts/deploy-flink-job.sh
```

脚本自动完成：构建 fat JAR → 创建目录 → 复制到 Flink 容器 → 提交 Job → 打印运行状态。

看到 `Job has been submitted with JobID xxx` 和状态 **RUNNING** 即成功。

也可在 http://localhost:8081 确认。

---

## 2. 发送测试数据

```bash
python3 scripts/simulate_edge_device.py --filter "data-10-left" --burst 50 --frag-size 1000
```

发送 3 通道 × 50 片段 × 1000 样本 ≈ 150,000 条记录。

---

## 3. 验证写入

等 5-10 秒后：

```bash
docker exec detection-tdengine taos -s "USE yudao_detection; SELECT COUNT(*) FROM raw_data;"
docker exec detection-tdengine taos -s "USE yudao_detection; SELECT * FROM raw_data LIMIT 5;"
```

| 检查项 | 期望 |
|--------|------|
| 总记录数 | ~148,000-150,000 |
| 子表 | `t_data_10_left_1`、`t_data_10_left_2`、`t_data_10_left_3` |
| 时间戳间隔 | 500ns |
| 电压值 | 小数格式（非科学记号） |

---

## 回退到第一阶段

如果第二阶段出现问题需要回退：

```bash
# 1. 取消所有 Flink Job
docker exec detection-flink-jobmanager /opt/flink/bin/flink list 2>/dev/null \
  | grep RUNNING | awk '{print $4}' \
  | xargs -I{} docker exec detection-flink-jobmanager /opt/flink/bin/flink cancel {}

# 2. 清除 Flink 容器内的 JAR
docker exec detection-flink-jobmanager rm -rf /opt/flink/usrlib/
docker exec detection-flink-taskmanager rm -rf /opt/flink/usrlib/

# 3. 重建 TDengine（清空数据，保留表结构）
docker exec detection-tdengine taos -s "DROP DATABASE IF EXISTS yudao_detection; CREATE DATABASE yudao_detection KEEP 3650 DURATION 30 BUFFER 256 WAL_LEVEL 1 PRECISION 'ns';"
docker cp sql/init_tdengine_v2.sql detection-tdengine:/tmp/init.sql
docker exec detection-tdengine taos -f /tmp/init.sql
```

回退完成后回到第一阶段状态，可以重新执行本文档。
