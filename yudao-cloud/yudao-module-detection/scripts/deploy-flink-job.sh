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
AMP_THRESHOLD="${AMP_THRESHOLD:-0.01}"
ENERGY_THRESHOLD="${ENERGY_THRESHOLD:-0.1}"
COUNTS_THRESHOLD="${COUNTS_THRESHOLD:-3}"

if [[ "$MODE" == "--help" || "$MODE" == "-h" ]]; then
  echo "Usage: $0 [--step2|--step3|--step4]"
  echo "  --step2  提交 SaveRaw（默认）"
  echo "  --step3  提交 SaveRaw + Filter + SaveFiltered"
  echo "  --step4  提交 SaveRaw + Filter + SaveFiltered + Anomaly"
  echo ""
  echo "环境变量: PARALLELISM=5  BATCH_SIZE=16  FILTER_TYPE=kalman"
  echo "         AMP_THRESHOLD=0.5  ENERGY_THRESHOLD=10.0  COUNTS_THRESHOLD=5"
  exit 0
fi

FLINK="docker exec detection-flink-jobmanager /opt/flink/bin/flink"

submit_job() {
  local class="$1"; shift
  echo "  → $class"
  $FLINK run -d -p "$PARALLELISM" -c "$class" "$REMOTE_JAR" "$@"
}

echo "=== [1/4] 构建 fat JAR ==="
(cd signal-flink-jobs && mvn clean package -q)
ls -lh "$JAR_PATH"

echo "=== [2/4] 部署到 Flink 容器 ==="
for c in detection-flink-jobmanager detection-flink-taskmanager; do
  docker exec "$c" mkdir -p /opt/flink/usrlib /opt/flink/checkpoints
  docker exec "$c" chmod 777 /opt/flink/checkpoints
  docker cp "$JAR_PATH" "$c:$REMOTE_JAR"
done

echo "=== [3/4] 取消已有 Job ==="
for attempt in 1 2; do
  RUNNING_JOBS=$($FLINK list 2>/dev/null | grep RUNNING | awk '{print $4}' || true)
  if [[ -z "$RUNNING_JOBS" ]]; then
    [[ $attempt -eq 1 ]] && echo "  (无运行中的 Job)"
    break
  fi
  for jid in $RUNNING_JOBS; do
    echo "  ✕ cancel $jid"
    $FLINK cancel "$jid" 2>/dev/null || true
  done
  sleep 3
done

echo "=== [4/4] 提交 Flink Job（并行度: ${PARALLELISM}）==="

submit_saveraw() {
  submit_job cn.iocoder.yudao.detection.flink.job.SignalSaveRawJob \
    kafka:9092 "jdbc:TAOS-RS://tdengine:6041/yudao_detection" "$BATCH_SIZE" raw_topic exception_topic
}
submit_filter() {
  submit_job cn.iocoder.yudao.detection.flink.job.SignalFilterJob \
    kafka:9092 raw_topic filtered_topic http://kalman-service:8000 "$FILTER_TYPE" exception_topic
}
submit_savefiltered() {
  submit_job cn.iocoder.yudao.detection.flink.job.SignalSaveFilteredJob \
    kafka:9092 "jdbc:TAOS-RS://tdengine:6041/yudao_detection" "$BATCH_SIZE" filtered_topic exception_topic
}
submit_anomaly() {
  submit_job cn.iocoder.yudao.detection.flink.job.SignalAnomalyJob \
    kafka:9092 "jdbc:TAOS-RS://tdengine:6041/yudao_detection" "$BATCH_SIZE" \
    filtered_topic anomaly_topic exception_topic \
    "$AMP_THRESHOLD" "$ENERGY_THRESHOLD" "$COUNTS_THRESHOLD"
}

case "$MODE" in
  --step4|--all)
    submit_saveraw; submit_filter; submit_savefiltered; submit_anomaly ;;
  --step3)
    submit_saveraw; submit_filter; submit_savefiltered ;;
  --step2|"")
    submit_saveraw ;;
  *)
    echo "未知参数: $MODE（使用 --help 查看用法）"
    exit 1 ;;
esac

echo ""
echo "=== 运行状态 ==="
$FLINK list
