<template>
  <div class="anomaly-bar-container mb-4">
    <div class="flex items-center justify-between mb-2">
      <span class="text-base font-bold">实时异常监测告警</span>
      <el-badge :value="anomalies.length" type="danger" v-if="anomalies.length > 0" />
    </div>
    <div class="anomaly-bar-wrapper">
      <div v-if="anomalies.length === 0" class="anomaly-empty">
        <el-empty description="监测中，暂无异常数据" :image-size="80" />
      </div>
      <div v-else class="anomaly-bar-list">
        <div v-for="(item, index) in anomalies.slice(0, 10)" :key="index"
          class="anomaly-bar-item" :class="{ 'new-anomaly': index === 0 }">
          <div class="anomaly-bar-content">
            <div class="flex items-center gap-3">
              <el-tag size="small" :type="alertTagType(item)" effect="dark">
                {{ alertLabel(item) }}
              </el-tag>
              <span class="anomaly-time">{{ formatTs(item.timestamp) }}</span>
              <span class="anomaly-label">{{ errorTypeLabel(item) }}</span>
              <span class="anomaly-value">能量值: <strong>{{ fmtNum(item.energy) }}</strong></span>
              <span class="anomaly-value">监测值: <strong>{{ fmtNum(item.amplitude) }}</strong></span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'

defineProps<{ anomalies: any[] }>()

const alertTagType = (item: any) => {
  const level = item.alertLevel ?? item.severity
  if (level === 3 || level === 'high') return 'danger'
  if (level === 2) return 'warning'
  return 'info'
}

const alertLabel = (item: any) => {
  const level = item.alertLevel ?? 0
  if (level === 3) return '报警'
  if (level === 2) return '预警'
  if (level === 1) return '关注'
  return item.severity === 'high' ? '高危' : '中危'
}

const errorTypeLabel = (item: any) => {
  const t = item.errorType || ''
  if (t === 'amplitude') return '幅值异常告警'
  if (t === 'energy') return '能量异常告警'
  if (t === 'counts') return '振铃计数告警'
  return '能量异常告警'
}

const formatTs = (ts: number) => {
  if (!ts) return '--'
  if (ts > 1e15) return dayjs(ts / 1e6).format('HH:mm:ss.SSS')
  return dayjs(ts).format('HH:mm:ss.SSS')
}

const fmtNum = (v: any) => {
  if (v == null) return '--'
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(4) : '--'
}
</script>

<style scoped>
.anomaly-bar-container {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}
.anomaly-bar-wrapper { width: 100%; }
.anomaly-empty { padding: 20px; text-align: center; }
.anomaly-bar-list {
  display: flex; flex-direction: column; gap: 8px;
  max-height: 200px; overflow-y: auto;
}
.anomaly-bar-item {
  background: #ffffff; border: 1px solid #ebeef5;
  border-radius: 6px; padding: 12px 16px; transition: all 0.3s;
}
.anomaly-bar-item:hover { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1); }
.anomaly-bar-item.new-anomaly {
  background: rgba(245, 108, 108, 0.08);
  border-color: #f56c6c; border-left-width: 4px;
  animation: pulse 2s infinite;
}
@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.4); }
  50% { box-shadow: 0 0 0 8px rgba(245, 108, 108, 0); }
}
.anomaly-time { color: #909399; font-size: 12px; font-family: monospace; }
.anomaly-label { color: #303133; font-weight: 500; margin-left: 8px; }
.anomaly-value { color: #606266; font-size: 13px; margin-left: 16px; }
.anomaly-value strong { color: #f56c6c; font-weight: 600; }
.mb-4 { margin-bottom: 1rem; }
.mb-2 { margin-bottom: 0.5rem; }
.gap-3 { gap: 0.75rem; }
</style>
