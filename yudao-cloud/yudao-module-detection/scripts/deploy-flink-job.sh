#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

PARALLELISM="${PARALLELISM:-5}"
BATCH_SIZE="${BATCH_SIZE:-16}"
FILTER_TYPE="${FILTER_TYPE:-kalman}"
JAR_NAME="signal-flink-jobs-1.0.0.jar"
JAR_PATH="signal-flink-jobs/target/$JAR_NAME"
REMOTE_JAR="/opt/flink/usrlib/$JAR_NAME"
MODE="${1:---step2}"
if [[ "$MODE" == "--help" || "$MODE" == "-h" ]]; then
  echo "Usage: $0 [--step2|--step3]"
  echo "  --step2  提交 SaveRaw（默认）"
  echo "  --step3  提交 SaveRaw + Filter + SaveFiltered"
  echo ""
  echo "环境变量: PARALLELISM=5  BATCH_SIZE=16  FILTER_TYPE=kalman"
  exit 0
fi

FLINK="docker exec detection-flink-jobmanager /opt/flink/bin/flink"

submit_job() {
  local class="$1"; shift
  echo "  → $class"
  $FLINK run -d -p "$PARALLELISM" -c "$class" "$REMOTE_JAR" "$@"
}

echo "=== [1/3] 构建 fat JAR ==="
(cd signal-flink-jobs && mvn clean package -q)
ls -lh "$JAR_PATH"

echo "=== [2/3] 部署到 Flink 容器 ==="
for c in detection-flink-jobmanager detection-flink-taskmanager; do
  docker exec "$c" mkdir -p /opt/flink/usrlib /opt/flink/checkpoints
  docker exec "$c" chmod 777 /opt/flink/checkpoints
  docker cp "$JAR_PATH" "$c:$REMOTE_JAR"
done

echo "=== [3/3] 提交 Flink Job（并行度: ${PARALLELISM}）==="

case "$MODE" in
  --all|--step3)
    submit_job cn.iocoder.yudao.detection.flink.job.SignalSaveRawJob \
      kafka:9092 "jdbc:TAOS-RS://tdengine:6041/yudao_detection" "$BATCH_SIZE" raw_topic exception_topic

    submit_job cn.iocoder.yudao.detection.flink.job.SignalFilterJob \
      kafka:9092 raw_topic filtered_topic http://filter-gateway:8010 "$FILTER_TYPE" exception_topic

    submit_job cn.iocoder.yudao.detection.flink.job.SignalSaveFilteredJob \
      kafka:9092 "jdbc:TAOS-RS://tdengine:6041/yudao_detection" "$BATCH_SIZE" filtered_topic exception_topic
    ;;
  --step2|"")
    submit_job cn.iocoder.yudao.detection.flink.job.SignalSaveRawJob \
      kafka:9092 "jdbc:TAOS-RS://tdengine:6041/yudao_detection" "$BATCH_SIZE" raw_topic exception_topic
    ;;
  *)
    echo "未知参数: $MODE（使用 --help 查看用法）"
    exit 1
    ;;
esac

echo ""
echo "=== 运行状态 ==="
$FLINK list
