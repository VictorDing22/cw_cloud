<template>
  <ContentWrap title="实时滤波监控">
    <el-row :gutter="20">
      <!-- 左侧控制面板 -->
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>控制面板</span>
            </div>
          </template>

          <el-form label-width="90px">
            <el-form-item label="连接状态">
              <el-tag :type="wsConnected ? 'success' : 'danger'" size="large">
                {{ wsConnected ? '已连接' : '未连接' }}
              </el-tag>
            </el-form-item>

            <el-form-item label="数据源">
              <el-select v-model="selectedDevice" placeholder="选择数据源" @change="handleDeviceChange">
                <el-option-group label="实时数据">
                  <el-option label="🔴 实时设备 (WebSocket)" value="REALTIME" />
                </el-option-group>
                <el-option-group label="TDMS真实数据">
                  <el-option label="📁 Signal-1 (单文件多通道)" value="TDMS_SIGNAL_1" />
                  <el-option label="📁 Signal-2 (多文件组合)" value="TDMS_SIGNAL_2" />
                </el-option-group>
                <el-option-group label="模拟数据">
                  <el-option label="📊 测试设备1 - 正弦波" value="SIM_SINE" />
                  <el-option label="📊 测试设备2 - 方波" value="SIM_SQUARE" />
                  <el-option label="📊 测试设备3 - 噪声信号" value="SIM_NOISE" />
                  <el-option label="📊 测试设备4 - 声发射模拟" value="SIM_AE" />
                </el-option-group>
              </el-select>
            </el-form-item>

            <el-form-item label="滤波器类型">
              <el-select v-model="filterType">
                <el-option label="LMS" value="LMS" />
                <el-option label="NLMS" value="NLMS" />
              </el-select>
            </el-form-item>

            <el-form-item>
              <el-button 
                type="primary" 
                :icon="wsConnected ? 'Close' : 'Connection'" 
                @click="toggleConnection"
                style="width: 100%"
              >
                {{ wsConnected ? '断开连接' : '开始监控' }}
              </el-button>
            </el-form-item>
          </el-form>

          <el-divider />

          <!-- 实时统计 -->
          <div class="stats-panel">
            <h4>📊 实时统计</h4>
            
            <!-- 处理性能 - 突出显示 -->
            <div class="performance-card">
              <div class="perf-item">
                <div class="perf-label">⚡ 处理速度</div>
                <div class="perf-value" :style="{ color: stats.throughput > 1000 ? '#67C23A' : '#E6A23C' }">
                  {{ formatThroughput(stats.throughput) }}
                </div>
              </div>
              <div class="perf-item">
                <div class="perf-label">⏱️ 处理延迟</div>
                <div class="perf-value">{{ stats.processingTime.toFixed(1) }} ms</div>
              </div>
            </div>

            <!-- 信噪比 - 突出显示 -->
            <div class="snr-card">
              <div class="snr-header">📈 信噪比分析</div>
              <el-row :gutter="10">
                <el-col :span="12">
                  <div class="snr-item">
                    <div class="snr-label">滤波前 SNR</div>
                    <div class="snr-value" style="color: #F56C6C;">{{ stats.snrBefore.toFixed(2) }} dB</div>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="snr-item">
                    <div class="snr-label">滤波后 SNR</div>
                    <div class="snr-value" style="color: #67C23A;">{{ stats.snrAfter.toFixed(2) }} dB</div>
                  </div>
                </el-col>
              </el-row>
              <div class="snr-improvement">
                <span>SNR 改善:</span>
                <span class="improvement-value" :style="{ color: stats.snrImprovement > 0 ? '#67C23A' : '#F56C6C' }">
                  {{ stats.snrImprovement > 0 ? '+' : '' }}{{ stats.snrImprovement.toFixed(2) }} dB
                </span>
              </div>
            </div>

            <el-row :gutter="10">
              <el-col :span="12">
                <div class="stat-item">
                  <div class="stat-label">当前误差</div>
                  <div class="stat-value">{{ stats.error.toFixed(4) }}</div>
                </div>
              </el-col>
              <el-col :span="12">
                <div class="stat-item">
                  <div class="stat-label">数据包数</div>
                  <div class="stat-value">{{ stats.packetCount }}</div>
                </div>
              </el-col>
            </el-row>
            
            <!-- 异常检测统计 -->
            <el-divider><span style="color: #E6A23C; font-weight: bold;">🚨 异常检测</span></el-divider>
            <el-row :gutter="10">
              <el-col :span="12">
                <div class="stat-item">
                  <div class="stat-label">异常检测</div>
                  <div class="stat-value">
                    <el-switch v-model="anomalyDetectionEnabled" @change="toggleAnomalyDetection" />
                  </div>
                </div>
              </el-col>
              <el-col :span="12">
                <div class="stat-item">
                  <div class="stat-label">检测总数</div>
                  <div class="stat-value" style="color: #E6A23C;">{{ anomalyStats.totalAnomalies }}</div>
                </div>
              </el-col>
            </el-row>
            <el-row :gutter="10">
              <el-col :span="12">
                <div class="stat-item">
                  <div class="stat-label">残差异常</div>
                  <div class="stat-value" style="color: #F56C6C;">{{ anomalyStats.residualAnomalies }}</div>
                </div>
              </el-col>
              <el-col :span="12">
                <div class="stat-item">
                  <div class="stat-label">突变异常</div>
                  <div class="stat-value" style="color: #F56C6C;">{{ anomalyStats.suddenChangeAnomalies }}</div>
                </div>
              </el-col>
            </el-row>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧图表区域 -->
      <el-col :span="18">
        <!-- 实时波形图 -->
        <el-card shadow="hover" style="margin-bottom: 20px">
          <template #header>
            <div class="card-header">
              <span>实时波形对比</span>
              <el-button text @click="clearChart">清除</el-button>
            </div>
          </template>
          <div id="waveform-chart" style="height: 400px"></div>
        </el-card>

        <!-- 卡尔曼滤波残差图 -->
        <el-card shadow="hover" style="margin-bottom: 20px">
          <template #header>
            <div class="card-header">
              <span>🔬 卡尔曼滤波残差分析</span>
            </div>
          </template>
          <div id="residual-chart" style="height: 280px"></div>
        </el-card>
        
        <!-- 异常检测结果 -->
        <el-card shadow="hover" v-if="anomalyDetectionEnabled">
          <template #header>
            <div class="card-header">
              <span>🚨 异常检测结果 (最近20条)</span>
              <el-button text @click="clearAnomalies" style="color: #E6A23C;">清除</el-button>
            </div>
          </template>
          
          <el-table 
            :data="recentAnomalies.slice(0, 20)" 
            height="300" 
            size="small"
            :default-sort="{ prop: 'timestamp', order: 'descending' }"
          >
            <el-table-column prop="timestamp" label="时间" width="100">
              <template #default="scope">
                {{ formatTime(scope.row.timestamp) }}
              </template>
            </el-table-column>
            
            <el-table-column prop="type" label="类型" width="120">
              <template #default="scope">
                <el-tag :type="getAnomalyTagType(scope.row.type)" size="small">
                  {{ getAnomalyTypeName(scope.row.type) }}
                </el-tag>
              </template>
            </el-table-column>
            
            <el-table-column prop="alertLevel" label="级别" width="80">
              <template #default="scope">
                <el-tag :type="getAlertLevelTagType(scope.row.alertLevel)" size="small">
                  {{ scope.row.alertLevel }}
                </el-tag>
              </template>
            </el-table-column>
            
            <el-table-column prop="score" label="分数" width="80">
              <template #default="scope">
                <span :style="{ color: getScoreColor(scope.row.score) }">
                  {{ (scope.row.score * 100).toFixed(0) }}%
                </span>
              </template>
            </el-table-column>
            
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
          </el-table>
        </el-card>
        
        <!-- 频谱分析 -->
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>频谱分析</span>
            </div>
          </template>
          <div id="spectrum-chart" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import * as echarts from 'echarts'

const wsConnected = ref(false)
const selectedDevice = ref('REALTIME')
const filterType = ref('LMS')
const isSimulationMode = ref(false)
const isTdmsMode = ref(false)
let simulationTimer: number | null = null
const stats = ref({
  throughput: 0,
  error: 0,
  snrBefore: 0,
  snrAfter: 0,
  snrImprovement: 0,
  processingTime: 0,
  packetCount: 0
})

// 格式化处理速度显示
const formatThroughput = (value: number): string => {
  if (value >= 1000000) return `${(value / 1000000).toFixed(1)} M/s`
  if (value >= 1000) return `${(value / 1000).toFixed(1)} K/s`
  return `${value} /s`
}

// 异常检测相关状态
const anomalyDetectionEnabled = ref(true)
const anomalyStats = ref({
  totalAnomalies: 0,
  residualAnomalies: 0,
  suddenChangeAnomalies: 0,
  uncertaintyAnomalies: 0
})

const recentAnomalies = ref([])

// 用于计算真实处理速度的变量
let totalSamplesProcessed = 0
let lastThroughputUpdate = Date.now()
let throughputHistory: number[] = []

let ws: WebSocket | null = null
let waveformChart: echarts.ECharts | null = null
let spectrumChart: echarts.ECharts | null = null
let residualChart: echarts.ECharts | null = null

// 实时滚动波形缓冲区
const MAX_DISPLAY_POINTS = 500  // 显示最近500个点
let originalBuffer: number[] = []
let filteredBuffer: number[] = []
let timeBuffer: number[] = []
let timeCounter = 0

// WebSocket连接
const toggleConnection = () => {
  if (wsConnected.value) {
    stopDataStream()
  } else {
    startDataStream()
  }
}

// 开始数据流（实时或模拟）
const startDataStream = () => {
  if (selectedDevice.value.startsWith('SIM_')) {
    // 模拟模式
    startSimulation()
  } else if (selectedDevice.value.startsWith('TDMS_')) {
    // TDMS真实数据模式
    startTdmsPlayback()
  } else {
    // 实时模式
    connectWebSocket()
  }
}

// 停止数据流 - 统一清理所有状态
const stopDataStream = () => {
  console.log('停止数据流, 当前状态:', { 
    isSimulation: isSimulationMode.value, 
    isTdms: isTdmsMode.value, 
    wsConnected: wsConnected.value 
  })
  
  // 停止模拟定时器
  if (simulationTimer) {
    clearInterval(simulationTimer)
    simulationTimer = null
  }
  
  // 停止TDMS数据源
  if (isTdmsMode.value) {
    const apiBase = `${window.location.origin}/api/signal-producer`
    fetch(`${apiBase}/stop`, { method: 'POST' }).catch(e => console.error('停止数据源失败:', e))
  }
  
  // 断开WebSocket
  if (ws) {
    ws.close(1000, 'User disconnected')
    ws = null
  }
  
  // 重置所有状态
  isSimulationMode.value = false
  isTdmsMode.value = false
  wsConnected.value = false
  
  ElMessage.info('已断开连接')
}

// 开始模拟数据
const startSimulation = () => {
  isSimulationMode.value = true
  wsConnected.value = true
  ElMessage.success(`已启动模拟数据: ${getDeviceName(selectedDevice.value)}`)
  
  // 每500ms生成一次模拟数据
  simulationTimer = window.setInterval(() => {
    const simData = generateSimulationData(selectedDevice.value)
    handleRealtimeData(simData)
  }, 500)
}

// 停止模拟 (保留兼容性，实际由 stopDataStream 统一处理)
const stopSimulation = () => {
  stopDataStream()
}

// 开始TDMS数据播放 - 通过真实Kafka数据流
const startTdmsPlayback = async () => {
  const source = selectedDevice.value === 'TDMS_SIGNAL_1' ? 'signal-1' : 'signal-2'
  
  ElMessage.info(`正在启动 ${source} 真实数据流...`)
  
  try {
    // 1. 调用后端API启动对应的数据源
    const apiBase = `${window.location.origin}/api/signal-producer`
    const response = await fetch(`${apiBase}/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ source })
    })
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    
    const result = await response.json()
    console.log('数据源启动结果:', result)
    
    // 2. 连接WebSocket接收真实滤波数据
    isTdmsMode.value = true
    connectWebSocket()
    
    ElMessage.success(`已启动 ${source} 真实数据流，目标速率 2M/s`)
    
  } catch (error) {
    console.error('启动数据流失败:', error)
    ElMessage.error('启动数据流失败，请确保 tdms-signal-producer 服务已启动')
  }
}

// 停止TDMS数据流 (保留兼容性，实际由 stopDataStream 统一处理)
const stopTdmsPlayback = () => {
  stopDataStream()
}

// 获取设备名称
const getDeviceName = (deviceId: string): string => {
  const names: Record<string, string> = {
    'REALTIME': '实时设备',
    'TDMS_SIGNAL_1': 'Signal-1 (单文件多通道)',
    'TDMS_SIGNAL_2': 'Signal-2 (多文件组合)',
    'SIM_SINE': '测试设备1 - 正弦波',
    'SIM_SQUARE': '测试设备2 - 方波',
    'SIM_NOISE': '测试设备3 - 噪声信号',
    'SIM_AE': '测试设备4 - 声发射模拟'
  }
  return names[deviceId] || deviceId
}

// 生成模拟数据
const generateSimulationData = (deviceType: string) => {
  const sampleCount = 500
  const sampleRate = 100000
  const t = Date.now() / 1000
  
  let originalSamples: number[] = []
  let filteredSamples: number[] = []
  
  switch (deviceType) {
    case 'SIM_SINE':
      // 正弦波 + 噪声
      originalSamples = Array.from({ length: sampleCount }, (_, i) => {
        const time = t + i / sampleRate
        return Math.sin(2 * Math.PI * 1000 * time) + (Math.random() - 0.5) * 0.3
      })
      filteredSamples = originalSamples.map((v, i) => {
        const time = t + i / sampleRate
        return Math.sin(2 * Math.PI * 1000 * time) * 0.95
      })
      break
      
    case 'SIM_SQUARE':
      // 方波 + 噪声
      originalSamples = Array.from({ length: sampleCount }, (_, i) => {
        const time = t + i / sampleRate
        const square = Math.sign(Math.sin(2 * Math.PI * 500 * time))
        return square + (Math.random() - 0.5) * 0.4
      })
      filteredSamples = originalSamples.map((v, i) => {
        const time = t + i / sampleRate
        return Math.sign(Math.sin(2 * Math.PI * 500 * time)) * 0.9
      })
      break
      
    case 'SIM_NOISE':
      // 纯噪声信号
      originalSamples = Array.from({ length: sampleCount }, () => (Math.random() - 0.5) * 2)
      // 简单低通滤波
      filteredSamples = originalSamples.map((_, i) => {
        const start = Math.max(0, i - 5)
        const end = Math.min(sampleCount, i + 5)
        return originalSamples.slice(start, end).reduce((a, b) => a + b, 0) / (end - start)
      })
      break
      
    case 'SIM_AE':
      // 声发射模拟：衰减正弦波 + 噪声
      originalSamples = Array.from({ length: sampleCount }, (_, i) => {
        const time = i / sampleRate
        const burst = Math.exp(-time * 50) * Math.sin(2 * Math.PI * 100000 * time)
        return burst + (Math.random() - 0.5) * 0.1
      })
      filteredSamples = originalSamples.map((_, i) => {
        const time = i / sampleRate
        return Math.exp(-time * 50) * Math.sin(2 * Math.PI * 100000 * time) * 0.85
      })
      break
      
    default:
      originalSamples = Array.from({ length: sampleCount }, () => Math.random() - 0.5)
      filteredSamples = originalSamples.map(v => v * 0.8)
  }
  
  // 计算统计信息
  const calcRMS = (arr: number[]) => Math.sqrt(arr.reduce((s, v) => s + v * v, 0) / arr.length)
  const originalRMS = calcRMS(originalSamples)
  const filteredRMS = calcRMS(filteredSamples)
  const residuals = originalSamples.map((v, i) => v - filteredSamples[i])
  
  return {
    type: 'signal-data',
    deviceId: getDeviceName(deviceType),
    timestamp: Date.now(),
    sampleRate: sampleRate,
    sampleCount: sampleCount,
    originalSamples: originalSamples,
    filteredSamples: filteredSamples,
    residuals: residuals.slice(0, 100),
    currentError: Math.abs(residuals[0]),
    snrBefore: 10 + Math.random() * 5,
    snrAfter: 25 + Math.random() * 10,
    snrImprovement: 15 + Math.random() * 10,
    statistics: {
      min: Math.min(...originalSamples),
      max: Math.max(...originalSamples),
      avg: originalSamples.reduce((a, b) => a + b, 0) / sampleCount,
      rms: originalRMS,
      filteredRms: filteredRMS,
      processedSamples: sampleCount
    },
    mode: 'simulation'
  }
}

const connectWebSocket = () => {
  try {
    // 使用WebSocket Bridge服务（通过Nginx代理）
    const wsUrl = `ws://${window.location.host}/realtime`
    console.log('连接到WebSocket Bridge:', wsUrl)
    ws = new WebSocket(wsUrl)

    ws.onopen = () => {
      wsConnected.value = true
      ElMessage.success('WebSocket连接成功')
      ElNotification({
        title: '连接成功',
        message: '已连接到实时滤波服务',
        type: 'success',
        duration: 2000
      })
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        console.log('收到WebSocket消息:', data.type, data)
        
        // 只处理信号数据类型的消息
        if (data.type === 'signal-data') {
          handleRealtimeData(data)
        } else if (data.type === 'welcome') {
          console.log('WebSocket欢迎消息:', data.message)
        }
      } catch (error) {
        console.error('数据解析失败:', error)
      }
    }

    ws.onerror = (error) => {
      console.error('WebSocket错误:', error)
      console.log('当前尝试的URL:', wsUrl)
      ElMessage.error('WebSocket连接错误')
    }

    ws.onclose = (event) => {
      console.log('WebSocket关闭:', event.code, event.reason)
      wsConnected.value = false
      if (event.code !== 1000) {
        ElMessage.warning('WebSocket连接已断开，可能需要检查后端服务')
      } else {
        ElMessage.warning('WebSocket连接已断开')
      }
    }
  } catch (error) {
    ElMessage.error('连接失败，请确保backend.jar已启动')
    console.error('WebSocket连接失败:', error)
  }
}

// 断开WebSocket (保留兼容性，实际由 stopDataStream 统一处理)
const disconnectWebSocket = () => {
  stopDataStream()
}

// 处理实时数据
const handleRealtimeData = (data: any) => {
  const startTime = performance.now()
  
  // 更新统计信息
  stats.value.packetCount++
  stats.value.error = data.currentError || 0
  
  // 计算信噪比
  const snrResult = calculateDetailedSNR(data)
  stats.value.snrBefore = snrResult.before
  stats.value.snrAfter = snrResult.after
  stats.value.snrImprovement = snrResult.improvement
  
  // 计算真实处理速度
  // 方法1: 使用后端发送的采样率（代表数据源的采样率）
  // 方法2: 基于实际收到的样本数计算
  const sampleCount = data.sampleCount || data.statistics?.processedSamples || data.originalSamples?.length || 0
  const backendSampleRate = data.sampleRate || 50000
  
  // 累计总样本数
  totalSamplesProcessed += sampleCount
  
  // 计算从开始到现在的平均处理速度
  const now = Date.now()
  const timeDelta = (now - lastThroughputUpdate) / 1000
  
  if (timeDelta > 0.1) {
    // 使用滑动窗口计算瞬时速率
    const instantRate = sampleCount / timeDelta
    throughputHistory.push(instantRate)
    if (throughputHistory.length > 5) throughputHistory.shift()
    
    // 平均速率
    stats.value.throughput = Math.round(
      throughputHistory.reduce((a, b) => a + b, 0) / throughputHistory.length
    )
    lastThroughputUpdate = now
  }
  
  // 处理异常检测结果
  if (anomalyDetectionEnabled.value && data.anomalies && data.anomalies.length > 0) {
    processAnomalyResults(data.anomalies)
  }

  // 更新波形图
  updateWaveformChart(data)
  
  // 更新残差图
  updateResidualChart(data)
  
  // 更新频谱图  
  updateSpectrumChart(data)
  
  // 计算处理延迟
  stats.value.processingTime = performance.now() - startTime
}

// 处理异常检测结果
const processAnomalyResults = (anomalies: any[]) => {
  anomalies.forEach(anomaly => {
    // 更新异常统计
    anomalyStats.value.totalAnomalies++
    
    switch (anomaly.type) {
      case 'RESIDUAL_ANOMALY':
        anomalyStats.value.residualAnomalies++
        break
      case 'SUDDEN_CHANGE':
        anomalyStats.value.suddenChangeAnomalies++
        break
      case 'UNCERTAINTY_HIGH':
        anomalyStats.value.uncertaintyAnomalies++
        break
    }
    
    // 添加到最近异常列表
    recentAnomalies.value.unshift({
      ...anomaly,
      id: Date.now() + Math.random(),
      timestamp: Date.now()
    })
    
    // 保持列表长度
    if (recentAnomalies.value.length > 100) {
      recentAnomalies.value = recentAnomalies.value.slice(0, 50)
    }
    
    // 显示异常通知
    if (anomaly.alertLevel === 'ERROR' || anomaly.alertLevel === 'WARN') {
      ElNotification({
        title: `🚨 ${getAnomalyTypeName(anomaly.type)}`,
        message: anomaly.description || '检测到信号异常',
        type: anomaly.alertLevel === 'ERROR' ? 'error' : 'warning',
        duration: 3000
      })
    }
  })
}

// 异常类型名称映射
const getAnomalyTypeName = (type: string): string => {
  const names = {
    'RESIDUAL_ANOMALY': '残差异常',
    'SUDDEN_CHANGE': '突变异常',
    'UNCERTAINTY_HIGH': '高不确定性异常'
  }
  return names[type] || type
}

// 切换异常检测功能
const toggleAnomalyDetection = (enabled: boolean) => {
  if (enabled) {
    ElMessage.success('异常检测已启用')
  } else {
    ElMessage.info('异常检测已关闭')
  }
}

// 计算详细的信噪比
const calculateDetailedSNR = (data: any): { before: number; after: number; improvement: number } => {
  if (!data.originalSamples || !data.filteredSamples) {
    return { before: 0, after: 0, improvement: 0 }
  }
  
  const original = data.originalSamples
  const filtered = data.filteredSamples
  
  // 估算信号功率（使用均值作为信号估计）
  const signalPower = calculateSignalPower(filtered)
  
  // 估算噪声功率（原始信号与滤波信号的差异）
  const noiseBefore = calculateNoisePower(original, filtered)
  const noiseAfter = calculateResidualPower(original, filtered)
  
  // 计算SNR (dB)
  const snrBefore = noiseBefore > 0 ? 10 * Math.log10(signalPower / noiseBefore) : 0
  const snrAfter = noiseAfter > 0 ? 10 * Math.log10(signalPower / noiseAfter) : 20
  
  return {
    before: Math.max(-10, Math.min(30, snrBefore)),
    after: Math.max(-10, Math.min(40, snrAfter)),
    improvement: snrAfter - snrBefore
  }
}

// 计算信号功率
const calculateSignalPower = (samples: number[]): number => {
  if (!samples || samples.length === 0) return 0
  return samples.reduce((sum, val) => sum + val * val, 0) / samples.length
}

// 计算噪声功率（原始信号的高频分量）
const calculateNoisePower = (original: number[], filtered: number[]): number => {
  if (!original || !filtered || original.length === 0) return 0
  const minLen = Math.min(original.length, filtered.length)
  let sum = 0
  for (let i = 0; i < minLen; i++) {
    const diff = original[i] - filtered[i]
    sum += diff * diff
  }
  return sum / minLen
}

// 计算残差功率
const calculateResidualPower = (original: number[], filtered: number[]): number => {
  if (!filtered || filtered.length < 2) return 0
  // 使用滤波后信号的一阶差分作为残差估计
  let sum = 0
  for (let i = 1; i < filtered.length; i++) {
    const diff = filtered[i] - filtered[i - 1]
    sum += diff * diff
  }
  return sum / (filtered.length - 1) * 0.1 // 缩放因子
}

// 更新残差图
const updateResidualChart = (data: any) => {
  if (!residualChart) return

  const residuals = data.residuals || []
  const uncertainties = data.uncertainties || []

  if (residuals.length === 0) return

  const option = {
    title: {
      text: '卡尔曼滤波残差分析',
      left: 'center'
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['滤波残差', '不确定性'],
      bottom: 5
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      name: '采样点',
      data: residuals.map((_, i) => i)
    },
    yAxis: {
      type: 'value',
      name: '残差值'
    },
    series: [
      {
        name: '滤波残差',
        type: 'line',
        data: residuals,
        lineStyle: { color: '#E6A23C', width: 1 },
        symbol: 'none',
        animation: false
      },
      {
        name: '不确定性',
        type: 'line',
        data: uncertainties,
        lineStyle: { color: '#F56C6C', width: 1 },
        symbol: 'none',
        animation: false
      }
    ]
  }

  residualChart.setOption(option)
}

// 更新波形图
const updateWaveformChart = (data: any) => {
  if (!waveformChart) return

  const originalSamples = data.originalSamples || []
  const filteredSamples = data.filteredSamples || []

  if (originalSamples.length === 0) return

  // 降采样：只显示200个点
  const maxDisplayPoints = 200
  const downsampleFactor = Math.max(1, Math.floor(originalSamples.length / maxDisplayPoints))
  
  const downsampledOriginal = downsampleFactor > 1 
    ? originalSamples.filter((_, index) => index % downsampleFactor === 0)
    : originalSamples
  
  const downsampledFiltered = downsampleFactor > 1
    ? filteredSamples.filter((_, index) => index % downsampleFactor === 0)
    : filteredSamples

  // 生成时间轴标签（毫秒）
  const timeLabels = downsampledOriginal.map((_, i) => (i * downsampleFactor / 100).toFixed(1))

  const option = {
    title: {
      text: `设备: ${data.deviceId || selectedDevice.value}`,
      subtext: `SNR改善: ${stats.value.snrImprovement > 0 ? '+' : ''}${stats.value.snrImprovement.toFixed(1)}dB | 处理速度: ${formatThroughput(stats.value.throughput)}`,
      left: 'center',
      subtextStyle: {
        color: stats.value.snrImprovement > 0 ? '#67C23A' : '#909399',
        fontSize: 13,
        fontWeight: 'bold'
      }
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      },
      formatter: (params: any) => {
        const time = params[0]?.axisValue || ''
        let html = `<div style="font-weight:bold">时间: ${time} ms</div>`
        params.forEach((p: any) => {
          html += `<div>${p.marker} ${p.seriesName}: ${p.value?.toFixed(4) || '-'}</div>`
        })
        return html
      }
    },
    legend: {
      data: ['原始信号', '滤波后信号'],
      bottom: 5
    },
    grid: {
      left: '8%',
      right: '5%',
      bottom: '18%',
      top: '18%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      name: '时间 (ms)',
      data: timeLabels,
      boundaryGap: false,
      axisLabel: {
        interval: Math.floor(timeLabels.length / 10)
      }
    },
    yAxis: {
      type: 'value',
      name: '幅值',
      splitNumber: 5
    },
    dataZoom: [
      {
        type: 'inside',
        start: 0,
        end: 100
      },
      {
        start: 0,
        end: 100,
        height: 20,
        bottom: 25
      }
    ],
    series: [
      {
        name: '原始信号',
        type: 'line',
        data: downsampledOriginal,
        smooth: false,
        lineStyle: {
          width: 1.5,
          color: '#5470c6'
        },
        showSymbol: false,
        itemStyle: {
          color: '#5470c6'
        }
      },
      {
        name: '滤波后信号',
        type: 'line',
        data: downsampledFiltered,
        smooth: false,
        lineStyle: {
          width: 2,
          color: '#91cc75'
        },
        showSymbol: false,
        itemStyle: {
          color: '#91cc75'
        }
      }
    ],
    animation: false
  }

  waveformChart.setOption(option)
}

// 更新频谱图
const updateSpectrumChart = (data: any) => {
  if (!spectrumChart || !data.filteredSamples) return

  // 简化的频谱显示（实际应该用FFT）
  const samples = data.filteredSamples
  const spectrumData = samples.slice(0, 100).map((val, idx) => ({
    value: Math.abs(val),
    name: `${idx * 10}Hz`
  }))

  const option = {
    title: {
      text: '频谱分析',
      left: 'center'
    },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      name: '频率 (Hz)',
      data: spectrumData.map(item => item.name)
    },
    yAxis: {
      type: 'value',
      name: '幅值'
    },
    series: [
      {
        type: 'bar',
        data: spectrumData.map(item => item.value),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#83bff6' },
            { offset: 0.5, color: '#188df0' },
            { offset: 1, color: '#188df0' }
          ])
        }
      }
    ]
  }

  spectrumChart.setOption(option)
}

// 清除图表
const clearChart = () => {
  if (waveformChart) {
    waveformChart.clear()
  }
  if (spectrumChart) {
    spectrumChart.clear()
  }
  // 重置波形缓冲区
  originalBuffer = []
  filteredBuffer = []
  timeBuffer = []
  timeCounter = 0
  
  stats.value = {
    throughput: 0,
    error: 0,
    snrImprovement: 0,
    packetCount: 0
  }
}

// 格式化时间
const formatTime = (timestamp: number): string => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString()
}

// 获取异常类型标签颜色
const getAnomalyTagType = (type: string): string => {
  const types = {
    'RESIDUAL_ANOMALY': 'warning',
    'SUDDEN_CHANGE': 'danger',
    'UNCERTAINTY_HIGH': 'info'
  }
  return types[type] || 'info'
}

// 获取告警级别标签颜色
const getAlertLevelTagType = (level: string): string => {
  const levels = {
    'ERROR': 'danger',
    'WARN': 'warning', 
    'INFO': 'info'
  }
  return levels[level] || 'info'
}

// 获取分数颜色
const getScoreColor = (score: number): string => {
  if (score > 0.8) return '#F56C6C'
  if (score > 0.5) return '#E6A23C'
  return '#67C23A'
}

// 清除异常记录
const clearAnomalies = () => {
  recentAnomalies.value = []
  anomalyStats.value = {
    totalAnomalies: 0,
    residualAnomalies: 0,
    suddenChangeAnomalies: 0,
    uncertaintyAnomalies: 0
  }
  ElMessage.success('异常记录已清除')
}

// 设备切换
const handleDeviceChange = (value: string) => {
  // 如果正在运行，先停止
  if (wsConnected.value) {
    stopDataStream()
  }
  
  ElMessage.info(`切换到: ${getDeviceName(value)}`)
  
  // 重置波形缓冲区
  originalBuffer = []
  filteredBuffer = []
  timeBuffer = []
  timeCounter = 0
  
  // 重置统计
  stats.value = {
    throughput: 0,
    error: 0,
    snrBefore: 0,
    snrAfter: 0,
    snrImprovement: 0,
    processingTime: 0,
    packetCount: 0
  }
  throughputHistory = []
  totalSamplesProcessed = 0
}

// 初始化图表
onMounted(async () => {
  await nextTick()
  
  // 初始化波形图
  const waveformEl = document.getElementById('waveform-chart')
  if (waveformEl) {
    waveformChart = echarts.init(waveformEl)
  }

  // 初始化残差图
  const residualEl = document.getElementById('residual-chart')
  if (residualEl) {
    residualChart = echarts.init(residualEl)
  }

  // 初始化频谱图
  const spectrumEl = document.getElementById('spectrum-chart')
  if (spectrumEl) {
    spectrumChart = echarts.init(spectrumEl)
  }

  // 窗口大小变化时重绘
  window.addEventListener('resize', () => {
    waveformChart?.resize()
    residualChart?.resize()
    spectrumChart?.resize()
  })
})

onUnmounted(() => {
  stopDataStream()
  waveformChart?.dispose()
  residualChart?.dispose()
  spectrumChart?.dispose()
})
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-panel {
  margin-top: 20px;
  
  h4 {
    margin-bottom: 15px;
    color: #303133;
  }
}

.performance-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  padding: 15px;
  margin-bottom: 15px;
  display: flex;
  justify-content: space-around;
  
  .perf-item {
    text-align: center;
    
    .perf-label {
      font-size: 12px;
      color: rgba(255, 255, 255, 0.8);
      margin-bottom: 5px;
    }
    
    .perf-value {
      font-size: 18px;
      font-weight: bold;
      color: #fff;
    }
  }
}

.snr-card {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 15px;
  border: 1px solid #e4e7ed;
  
  .snr-header {
    font-size: 13px;
    font-weight: bold;
    color: #606266;
    margin-bottom: 10px;
  }
  
  .snr-item {
    text-align: center;
    padding: 8px 0;
    
    .snr-label {
      font-size: 11px;
      color: #909399;
    }
    
    .snr-value {
      font-size: 16px;
      font-weight: bold;
      margin-top: 3px;
    }
  }
  
  .snr-improvement {
    text-align: center;
    padding-top: 10px;
    border-top: 1px dashed #dcdfe6;
    margin-top: 8px;
    font-size: 13px;
    color: #606266;
    
    .improvement-value {
      font-size: 18px;
      font-weight: bold;
      margin-left: 8px;
    }
  }
}

.stat-item {
  background: #fafafa;
  border-radius: 6px;
  padding: 10px;
  text-align: center;
  margin-bottom: 10px;
  
  .stat-label {
    font-size: 11px;
    color: #909399;
  }
  
  .stat-value {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    margin-top: 3px;
  }
}
</style>
