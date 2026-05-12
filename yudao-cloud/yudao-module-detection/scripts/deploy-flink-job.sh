#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

PARALLELISM="${PARALLELISM:-5}"
BATCH_SIZE="${BATCH_SIZE:-16}"
FILTER_TYPE="${FILTER_TYPE:-kalman}"
AMP_THRESHOLD="${AMP_THRESHOLD:-0.009}"
ENERGY_THRESHOLD="${ENERGY_THRESHOLD:-0.005}"
COUNTS_THRESHOLD="${COUNTS_THRESHOLD:-72}"

FLINK="docker exec detection-flink-jobmanager /opt/flink/bin/flink"
JOBS_DIR="signal-flink-jobs"

job_module() {
  case "$1" in
    saveraw)      echo "signal-saveraw-job" ;;
    filter)       echo "signal-filter-job" ;;
    savefiltered) echo "signal-savefiltered-job" ;;
    anomaly)      echo "signal-anomaly-job" ;;
    *) echo ""; return 1 ;;
  esac
}

job_class() {
  case "$1" in
    saveraw)      echo "cn.iocoder.yudao.detection.flink.job.SignalSaveRawJob" ;;
    filter)       echo "cn.iocoder.yudao.detection.flink.job.SignalFilterJob" ;;
    savefiltered) echo "cn.iocoder.yudao.detection.flink.job.SignalSaveFilteredJob" ;;
    anomaly)      echo "cn.iocoder.yudao.detection.flink.job.SignalAnomalyJob" ;;
  esac
}

job_flink_name() {
  case "$1" in
    saveraw)      echo "signal-saveraw-flink-job" ;;
    filter)       echo "signal-filter-flink-job" ;;
    savefiltered) echo "signal-save-filtered-flink-job" ;;
    anomaly)      echo "signal-anomaly-flink-job" ;;
  esac
}

job_args() {
  case "$1" in
    saveraw)
      echo "kafka:9092 jdbc:TAOS-RS://tdengine:6041/yudao_detection $BATCH_SIZE raw_topic exception_topic" ;;
    filter)
      echo "kafka:9092 raw_topic filtered_topic http://kalman-service:8000 $FILTER_TYPE exception_topic" ;;
    savefiltered)
      echo "kafka:9092 jdbc:TAOS-RS://tdengine:6041/yudao_detection $BATCH_SIZE filtered_topic exception_topic" ;;
    anomaly)
      echo "kafka:9092 jdbc:TAOS-RS://tdengine:6041/yudao_detection $BATCH_SIZE filtered_topic anomaly_topic exception_topic $AMP_THRESHOLD $ENERGY_THRESHOLD $COUNTS_THRESHOLD" ;;
  esac
}

usage() {
  cat <<'USAGE'
Usage: deploy-flink-job.sh <command> [options]

Commands:
  --step2              部署 saveraw
  --step3              部署 saveraw + filter + savefiltered
  --step4 | --all      部署全部 4 个 Job
  --job <name>         部署单个 Job (saveraw|filter|savefiltered|anomaly)
  --cancel <name>      取消单个 Job
  --cancel-all         取消全部 Job
  --status             查看运行状态
  --help               显示帮助

环境变量:
  PARALLELISM=5  BATCH_SIZE=16  FILTER_TYPE=kalman
  AMP_THRESHOLD=0.009  ENERGY_THRESHOLD=0.005  COUNTS_THRESHOLD=72
USAGE
  exit 0
}

find_job_id() {
  local flink_name="$1"
  $FLINK list 2>/dev/null | grep RUNNING | grep "$flink_name" | awk '{print $4}' || true
}

cancel_job_by_name() {
  local name="$1"
  local flink_name
  flink_name=$(job_flink_name "$name")
  local jid
  jid=$(find_job_id "$flink_name")
  if [[ -n "$jid" ]]; then
    echo "  ✕ cancel $flink_name ($jid)"
    $FLINK cancel "$jid" 2>/dev/null || true
    sleep 2
  else
    echo "  (未找到运行中的 $flink_name)"
  fi
}

build_jobs() {
  local modules=""
  for j in "$@"; do
    local m
    m=$(job_module "$j")
    modules+="$m,"
  done
  modules="${modules%,}"

  echo "=== 构建 [$modules] ==="
  (cd "$JOBS_DIR" && mvn -pl "$modules" -am package -q)

  for j in "$@"; do
    local m
    m=$(job_module "$j")
    echo "  ✓ $(ls -lh "$JOBS_DIR/$m/target/$m-1.0.0.jar" | awk '{print $5, $NF}')"
  done
}

deploy_jars() {
  echo "=== 部署 JAR 到 Flink 容器 ==="
  for j in "$@"; do
    local m
    m=$(job_module "$j")
    local jar="$JOBS_DIR/$m/target/$m-1.0.0.jar"
    local remote="/opt/flink/usrlib/$m-1.0.0.jar"
    for c in detection-flink-jobmanager detection-flink-taskmanager; do
      docker exec "$c" mkdir -p /opt/flink/usrlib /opt/flink/checkpoints
      docker exec "$c" chmod 777 /opt/flink/checkpoints
      docker cp "$jar" "$c:$remote"
    done
    echo "  ✓ $m → $remote"
  done
}

submit_job() {
  local name="$1"
  local m
  m=$(job_module "$name")
  local class
  class=$(job_class "$name")
  local remote="/opt/flink/usrlib/$m-1.0.0.jar"
  local args
  args=$(job_args "$name")

  echo "  → $class"
  $FLINK run -d -p "$PARALLELISM" -c "$class" "$remote" $args
}

deploy_specific_jobs() {
  build_jobs "$@"
  deploy_jars "$@"

  echo "=== 取消目标 Job ==="
  for j in "$@"; do
    cancel_job_by_name "$j"
  done

  echo "=== 提交 Flink Job（并行度: ${PARALLELISM}）==="
  for j in "$@"; do
    submit_job "$j"
  done

  echo ""
  echo "=== 运行状态 ==="
  $FLINK list
}

# --- Main ---

MODE="${1:---help}"

case "$MODE" in
  --help|-h)
    usage ;;
  --status)
    $FLINK list ;;
  --cancel-all)
    for j in saveraw filter savefiltered anomaly; do
      cancel_job_by_name "$j"
    done ;;
  --cancel)
    [[ -z "${2:-}" ]] && { echo "用法: --cancel <saveraw|filter|savefiltered|anomaly>"; exit 1; }
    cancel_job_by_name "$2" ;;
  --job)
    [[ -z "${2:-}" ]] && { echo "用法: --job <saveraw|filter|savefiltered|anomaly>"; exit 1; }
    job_module "$2" > /dev/null || { echo "未知 Job: $2"; exit 1; }
    deploy_specific_jobs "$2" ;;
  --step2)
    deploy_specific_jobs saveraw ;;
  --step3)
    deploy_specific_jobs saveraw filter savefiltered ;;
  --step4|--all)
    deploy_specific_jobs saveraw filter savefiltered anomaly ;;
  *)
    echo "未知参数: $MODE（使用 --help 查看用法）"
    exit 1 ;;
esac
