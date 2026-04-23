#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

JOB_CLASS="${1:-cn.iocoder.yudao.detection.flink.job.SignalSaveRawJob}"
BATCH_SIZE="${2:-2000}"
TOPIC="${3:-raw_topic}"
JAR_NAME="signal-flink-jobs-1.0.0.jar"
JAR_PATH="signal-flink-jobs/target/$JAR_NAME"
REMOTE_JAR="/opt/flink/usrlib/$JAR_NAME"

echo "=== [1/4] 构建 fat JAR ==="
(cd signal-flink-jobs && mvn clean package -q)
ls -lh "$JAR_PATH"

echo "=== [2/4] 部署到 Flink 容器 ==="
for c in detection-flink-jobmanager detection-flink-taskmanager; do
  docker exec "$c" mkdir -p /opt/flink/usrlib /opt/flink/checkpoints
  docker exec "$c" chmod 777 /opt/flink/checkpoints
  docker cp "$JAR_PATH" "$c:$REMOTE_JAR"
done

echo "=== [3/4] 提交 Flink Job ==="
docker exec detection-flink-jobmanager /opt/flink/bin/flink run -d \
  -c "$JOB_CLASS" \
  "$REMOTE_JAR" \
  kafka:9092 \
  "jdbc:TAOS-RS://tdengine:6041/yudao_detection" \
  "$BATCH_SIZE" \
  "$TOPIC"

echo ""
echo "=== [4/4] 查看状态 ==="
docker exec detection-flink-jobmanager /opt/flink/bin/flink list
