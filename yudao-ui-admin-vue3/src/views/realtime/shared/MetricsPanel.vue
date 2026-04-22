<template>
  <el-card shadow="hover">
    <template #header>
      <div class="card-header">
        <el-icon><DataAnalysis /></el-icon>
        <span>性能指标</span>
        <el-tag v-if="realtime" type="success" effect="plain" size="small" style="margin-left: auto">
          实时更新
        </el-tag>
      </div>
    </template>

    <el-row :gutter="16">
      <!-- MSE改善 -->
      <el-col :span="6">
        <div class="metric-item" :class="{ 'metric-highlight': metrics.mseImprovement > 50 }">
          <div class="metric-label">MSE改善</div>
          <div class="metric-value">
            {{ metrics.mseImprovement?.toFixed(2) ?? '--' }}
            <span class="metric-unit">%</span>
          </div>
          <el-progress
            :percentage="Math.min(100, Math.max(0, metrics.mseImprovement || 0))"
            :color="getProgressColor(metrics.mseImprovement || 0)"
            :show-text="false"
          />
        </div>
      </el-col>

      <!-- 相关系数 -->
      <el-col :span="6">
        <div class="metric-item" :class="{ 'metric-highlight': (metrics.correlationAfter || 0) > 0.9 }">
          <div class="metric-label">相关系数</div>
          <div class="metric-value">
            {{ metrics.correlationAfter?.toFixed(4) ?? '--' }}
          </div>
          <div class="metric-sub">
            滤波前: {{ metrics.correlationBefore?.toFixed(4) ?? '--' }}
          </div>
        </div>
      </el-col>

      <!-- SNR改善 -->
      <el-col :span="6" v-if="metrics.snrImprovement !== undefined">
        <div class="metric-item" :class="{ 'metric-highlight': metrics.snrImprovement > 10 }">
          <div class="metric-label">SNR改善</div>
          <div class="metric-value">
            {{ metrics.snrImprovement?.toFixed(2) ?? '--' }}
            <span class="metric-unit">dB</span>
          </div>
          <div class="metric-sub">
            前: {{ metrics.snrBefore?.toFixed(2) ?? '--' }} dB → 
            后: {{ metrics.snrAfter?.toFixed(2) ?? '--' }} dB
          </div>
        </div>
      </el-col>

      <!-- 处理时间 -->
      <el-col :span="6" v-if="metrics.processingTime !== undefined">
        <div class="metric-item">
          <div class="metric-label">处理时间</div>
          <div class="metric-value">
            {{ metrics.processingTime?.toFixed(2) ?? '--' }}
            <span class="metric-unit">ms</span>
          </div>
        </div>
      </el-col>

      <!-- 处理速度 -->
      <el-col :span="6" v-if="metrics.throughput !== undefined">
        <div class="metric-item">
          <div class="metric-label">处理速度</div>
          <div class="metric-value">
            {{ formatThroughput(metrics.throughput) }}
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 详细指标（可展开） -->
    <el-divider />
    
    <el-collapse v-model="activeCollapse">
      <el-collapse-item title="详细指标" name="details">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="MSE (滤波前)">
            {{ metrics.mseBefore?.toFixed(6) ?? '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="MSE (滤波后)">
            {{ metrics.mseAfter?.toFixed(6) ?? '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="相关系数 (滤波前)">
            {{ metrics.correlationBefore?.toFixed(4) ?? '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="相关系数 (滤波后)">
            {{ metrics.correlationAfter?.toFixed(4) ?? '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="异常检测" v-if="metrics.anomalyCount !== undefined">
            {{ metrics.anomalyCount }} 次
          </el-descriptions-item>
          <el-descriptions-item label="残差异常" v-if="metrics.residualAnomalies !== undefined">
            {{ metrics.residualAnomalies }} 次
          </el-descriptions-item>
        </el-descriptions>
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { DataAnalysis } from '@element-plus/icons-vue'
import type { SignalMetrics } from '@/utils/signal/metricsCalculator'

interface Props {
  metrics: Partial<SignalMetrics>
  realtime?: boolean
}

withDefaults(defineProps<Props>(), {
  realtime: false
})

const activeCollapse = ref<string[]>([])

// 根据值获取进度条颜色
const getProgressColor = (value: number) => {
  if (value < 30) return '#f56c6c'
  if (value < 70) return '#e6a23c'
  return '#67c23a'
}

// 格式化吞吐量
const formatThroughput = (throughput: number) => {
  if (throughput >= 1000000) {
    return `${(throughput / 1000000).toFixed(2)} M samples/s`
  } else if (throughput >= 1000) {
    return `${(throughput / 1000).toFixed(2)} K samples/s`
  }
  return `${throughput.toFixed(0)} samples/s`
}
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.metric-item {
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  transition: all 0.3s;

  &.metric-highlight {
    background: linear-gradient(135deg, #e8f5e9 0%, #c8e6c9 100%);
    border: 1px solid #4caf50;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }
}

.metric-label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

.metric-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;

  .metric-unit {
    font-size: 14px;
    color: #909399;
    margin-left: 4px;
  }
}

.metric-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

:deep(.el-progress) {
  margin-top: 8px;
}

:deep(.el-descriptions__label) {
  width: 140px;
}
</style>
