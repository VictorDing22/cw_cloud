<template>
  <div class="vibration-page">
    <div class="page-header">
      <h2>📊 振动数据监控</h2>
      <p>实时监控设备振动数据，进行频谱分析和异常检测</p>
    </div>
    
    <!--筛选器 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="设备">
          <el-select v-model="queryForm.device" style="width: 200px">
            <el-option label="qc_raem1_4g_107" value="qc_raem1_4g_107" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="监测点">
          <el-select v-model="queryForm.point" style="width: 150px">
            <el-option label="X轴" value="x" />
            <el-option label="Y轴" value="y" />
            <el-option label="Z轴" value="z" />
          </el-select>
        </el-form-item>
        
        <el-form-item>
          <el-button-group>
            <el-button :type="viewMode === 'time' ? 'primary' : 'default'" @click="viewMode = 'time'">
              时域
            </el-button>
            <el-button :type="viewMode === 'freq' ? 'primary' : 'default'" @click="viewMode = 'freq'">
              频域
            </el-button>
          </el-button-group>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="loadData" :loading="loading">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <!-- 监控图表 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>{{ viewMode === 'time' ? '时域波形' : '频谱图' }}</span>
              <el-tag>{{ queryForm.point }}轴</el-tag>
            </div>
          </template>
          <div ref="mainChartRef" style="height: 400px"></div>
        </el-card>
        
        <el-card style="margin-top: 20px">
          <template #header>三轴振动对比</template>
          <div ref="compareChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      
      <el-col :span="8">
        <el-card>
          <template #header>振动统计</template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="RMS值">{{ vibrationStats.rms.toFixed(3) }} mm/s</el-descriptions-item>
            <el-descriptions-item label="峰值">{{ vibrationStats.peak.toFixed(3) }} mm/s</el-descriptions-item>
            <el-descriptions-item label="平均值">{{ vibrationStats.mean.toFixed(3) }} mm/s</el-descriptions-item>
            <el-descriptions-item label="标准差">{{ vibrationStats.std.toFixed(3) }} mm/s</el-descriptions-item>
          </el-descriptions>
        </el-card>
        
        <el-card style="margin-top: 20px">
          <template #header>频率分析</template>
          <div ref="freqDistChartRef" style="height: 200px"></div>
        </el-card>
        
        <el-card style="margin-top: 20px">
          <template #header>异常检测</template>
          <el-alert 
            :title="anomalyStatus.detected ? '检测到异常振动' : '振动正常'" 
            :type="anomalyStatus.detected ? 'error' : 'success'"
            :closable="false"
            show-icon
          >
            <template v-if="anomalyStatus.detected">
              <p>异常点数: {{ anomalyStatus.count }}</p>
              <p>最大异常值: {{ anomalyStatus.maxValue.toFixed(3) }} mm/s</p>
            </template>
          </el-alert>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'

defineOptions({ name: 'IotVibration' })

const loading = ref(false)
const viewMode = ref<'time' | 'freq'>('time')
const mainChartRef = ref<HTMLElement>()
const compareChartRef = ref<HTMLElement>()
const freqDistChartRef = ref<HTMLElement>()

let mainChart: echarts.ECharts | null = null
let compareChart: echarts.ECharts | null = null
let freqDistChart: echarts.ECharts | null = null

const queryForm = reactive({
  device: 'qc_raem1_4g_107',
  point: 'x'
})

// 振动数据
const vibrationData = ref<number[]>([])

// 振动统计
const vibrationStats = computed(() => {
  const data = vibrationData.value
  if (data.length === 0) return { rms: 0, peak: 0, mean: 0, std: 0 }
  
  const mean = data.reduce((sum, v) => sum + v, 0) / data.length
  const rms = Math.sqrt(data.reduce((sum, v) => sum + v * v, 0) / data.length)
  const peak = Math.max(...data.map(Math.abs))
  const variance = data.reduce((sum, v) => sum + Math.pow(v - mean, 2), 0) / data.length
  const std = Math.sqrt(variance)
  
  return { rms, peak, mean, std }
})

// 异常检测
const anomalyStatus = computed(() => {
  const threshold = 5.0 // 阈值
  const anomalies = vibrationData.value.filter(v => Math.abs(v) > threshold)
  
  return {
    detected: anomalies.length > 0,
    count: anomalies.length,
    maxValue: anomalies.length > 0 ? Math.max(...anomalies.map(Math.abs)) : 0
  }
})

// 生成模拟振动数据
const generateVibrationData = (samples: number = 1000) => {
  const data: number[] = []
  for (let i = 0; i < samples; i++) {
    // 组合多个频率的正弦波 + 噪声
    const t = i / samples
    const signal = 
      2 * Math.sin(2 * Math.PI * 10 * t) +   // 10Hz
      1.5 * Math.sin(2 * Math.PI * 25 * t) + // 25Hz
      0.8 * Math.sin(2 * Math.PI * 50 * t) + // 50Hz
      (Math.random() - 0.5) * 0.5            // 噪声
    
    // 偶尔添加异常值
    const anomaly = Math.random() > 0.98 ? (Math.random() - 0.5) * 10 : 0
    data.push(signal + anomaly)
  }
  return data
}

// 计算FFT（简化版）
const calculateFFT = (data: number[]) => {
  // 简化的频谱计算（实际应使用FFT算法）
  const freqData: [number, number][] = []
  for (let freq = 0; freq <= 100; freq += 2) {
    const amplitude = Math.random() * 2 + (freq === 10 || freq === 25 || freq === 50 ? 3 : 0)
    freqData.push([freq, amplitude])
  }
  return freqData
}

// 初始化图表
const initCharts = () => {
  vibrationData.value = generateVibrationData(1000)
  
  // 主图表（时域/频域）
  if (mainChartRef.value) {
    mainChart = echarts.init(mainChartRef.value)
    updateMainChart()
  }
  
  // 三轴对比
  if (compareChartRef.value) {
    compareChart = echarts.init(compareChartRef.value)
    const xData = generateVibrationData(200)
    const yData = generateVibrationData(200)
    const zData = generateVibrationData(200)
    
    compareChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['X轴', 'Y轴', 'Z轴'] },
      xAxis: { type: 'category' },
      yAxis: { type: 'value', name: 'mm/s' },
      series: [
        { name: 'X轴', type: 'line', data: xData.slice(0, 200), smooth: true, lineStyle: { width: 1 }, symbol: 'none' },
        { name: 'Y轴', type: 'line', data: yData.slice(0, 200), smooth: true, lineStyle: { width: 1 }, symbol: 'none' },
        { name: 'Z轴', type: 'line', data: zData.slice(0, 200), smooth: true, lineStyle: { width: 1 }, symbol: 'none' }
      ]
    })
  }
  
  // 频率分布
  if (freqDistChartRef.value) {
    freqDistChart = echarts.init(freqDistChartRef.value)
    freqDistChart.setOption({
      tooltip: {},
      xAxis: { type: 'category', data: ['0-20Hz', '20-40Hz', '40-60Hz', '60-80Hz', '80-100Hz'] },
      yAxis: { type: 'value', name: '能量' },
      series: [{
        type: 'bar',
        data: [45, 68, 35, 22, 15],
        itemStyle: { color: '#409eff' }
      }]
    })
  }
}

const updateMainChart = () => {
  if (!mainChart) return
  
  if (viewMode.value === 'time') {
    // 时域波形
    mainChart.setOption({
      title: { text: '时域波形', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', name: '采样点' },
      yAxis: { type: 'value', name: '振幅 (mm/s)' },
      dataZoom: [{ type: 'inside' }, { type: 'slider', height: 20 }],
      series: [{
        type: 'line',
        data: vibrationData.value,
        smooth: false,
        symbol: 'none',
        lineStyle: { color: '#00d4d4', width: 1 },
        areaStyle: { color: 'rgba(0, 212, 212, 0.2)' }
      }]
    })
  } else {
    // 频域图
    const freqData = calculateFFT(vibrationData.value)
    mainChart.setOption({
      title: { text: '频谱图', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'value', name: '频率 (Hz)' },
      yAxis: { type: 'value', name: '幅值' },
      series: [{
        type: 'line',
        data: freqData,
        smooth: false,
        lineStyle: { color: '#409eff', width: 2 },
        areaStyle: { color: 'rgba(64, 158, 255, 0.3)' }
      }]
    })
  }
}

const loadData = () => {
  loading.value = true
  setTimeout(() => {
    vibrationData.value = generateVibrationData(1000)
    updateMainChart()
    loading.value = false
    ElMessage.success('数据已刷新')
  }, 300)
}

watch(viewMode, () => {
  updateMainChart()
})

watch(() => queryForm.point, () => {
  loadData()
})

onMounted(() => {
  initCharts()
})

onUnmounted(() => {
  mainChart?.dispose()
  compareChart?.dispose()
  freqDistChart?.dispose()
})
</script>

<style scoped>
.vibration-page {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  color: #303133;
}

.page-header p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.filter-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
