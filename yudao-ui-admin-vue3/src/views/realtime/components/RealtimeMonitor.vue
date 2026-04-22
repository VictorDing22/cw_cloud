<template>
  <div class="realtime-monitor">
    <el-row :gutter="20">
      <!-- 左侧控制面板 -->
      <el-col :span="6">
        <!-- 数据源选择 -->
        <el-card shadow="hover" style="margin-bottom: 15px">
          <el-radio-group v-model="dataSource" size="large" @change="handleDataSourceChange" style="width: 100%">
            <el-radio-button value="realtime" style="width: 50%">
              <el-icon><Monitor /></el-icon>
              <span>实时数据</span>
            </el-radio-button>
            <el-radio-button value="history" style="width: 50%">
              <el-icon><FolderOpened /></el-icon>
              <span>历史文件</span>
            </el-radio-button>
          </el-radio-group>
        </el-card>

        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>控制面板</span>
              <el-tag v-if="dataSource === 'realtime'" :type="wsConnected ? 'success' : 'info'" size="small">
                {{ wsConnected ? '已连接' : '未连接' }}
              </el-tag>
              <el-tag v-else type="warning" size="small">历史数据</el-tag>
            </div>
          </template>

          <el-form label-width="90px">
            <!-- 实时数据源配置 -->
            <template v-if="dataSource === 'realtime'">
              <el-form-item label="设备选择">
                <el-select v-model="selectedDevice" placeholder="选择设备" @change="handleDeviceChange">
                  <el-option label="设备 001" value="DEVICE_001" />
                  <el-option label="设备 002" value="DEVICE_002" />
                  <el-option label="测试设备" value="DEVICE_TEST" />
                </el-select>
              </el-form-item>
            </template>

            <!-- 历史文件配置 -->
            <template v-else>
              <el-form-item label="历史文件">
                <el-select v-model="selectedFile" placeholder="选择文件">
                  <el-option label="Signal-1 (单文件)" value="signal-1" />
                  <el-option label="Signal-2 (多文件)" value="signal-2" />
                </el-select>
              </el-form-item>
            </template>

            <el-form-item label="滤波器类型">
              <el-select v-model="filterType">
                <el-option label="LMS" value="LMS" />
                <el-option label="NLMS" value="NLMS" />
              </el-select>
            </el-form-item>

            <el-form-item>
              <el-button 
                v-if="dataSource === 'realtime'"
                :type="wsConnected ? 'danger' : 'primary'"
                :icon="wsConnected ? 'Close' : 'Connection'" 
                @click="toggleConnection"
                style="width: 100%"
              >
                {{ wsConnected ? '断开连接' : '开始监控' }}
              </el-button>
              <div v-else style="display: flex; gap: 8px">
                <el-button 
                  type="primary" 
                  icon="FolderOpened"
                  @click="loadHistoryData"
                  :loading="loadingHistory"
                  :disabled="!selectedFile || isPlayingHistory"
                  style="flex: 1"
                >
                  {{ loadingHistory ? '加载中...' : '加载文件' }}
                </el-button>
                <el-button 
                  v-if="isPlayingHistory"
                  type="danger" 
                  icon="VideoPause"
                  @click="stopHistoryPlayback"
                  style="flex: 1"
                >
                  停止播放
                </el-button>
              </div>
            </el-form-item>
          </el-form>

          <el-divider />

          <!-- 实时统计 -->
          <div class="stats-panel">
            <h4>实时统计</h4>
            <el-row :gutter="10">
              <el-col :span="12">
                <div class="stat-item">
                  <div class="stat-label">处理速度</div>
                  <div class="stat-value">{{ stats.throughput }} samples/s</div>
                </div>
              </el-col>
              <el-col :span="12">
                <div class="stat-item">
                  <div class="stat-label">当前误差</div>
                  <div class="stat-value">{{ stats.error.toFixed(4) }}</div>
                </div>
              </el-col>
            </el-row>
            <el-row :gutter="10">
              <el-col :span="12">
                <div class="stat-item">
                  <div class="stat-label">SNR改善</div>
                  <div class="stat-value">{{ stats.snrImprovement.toFixed(2) }} dB</div>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { Monitor, FolderOpened, VideoPause } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import axios from 'axios'

// 定义emit事件
const emit = defineEmits(['statusChange'])

// 数据源选择
const dataSource = ref('realtime')  // 'realtime' | 'history'
const selectedFile = ref('')        // 历史文件选择
const loadingHistory = ref(false)   // 历史文件加载状态
const isPlayingHistory = ref(false) // 历史数据是否正在播放

const wsConnected = ref(false)

// 监听连接状态变化，通知父组件
watch(wsConnected, (newVal) => {
  emit('statusChange', {
    connected: newVal,
    deviceCount: 1
  })
})
const selectedDevice = ref('DEVICE_001')
const filterType = ref('LMS')
const stats = ref({
  throughput: 0,
  error: 0,
  snrImprovement: 0,
  packetCount: 0
})

// 异常检测相关状态
const anomalyDetectionEnabled = ref(true)
const anomalyStats = ref({
  totalAnomalies: 0,
  residualAnomalies: 0,
  suddenChangeAnomalies: 0,
  uncertaintyAnomalies: 0
})

const recentAnomalies = ref([])

let ws: WebSocket | null = null
let waveformChart: echarts.ECharts | null = null
let spectrumChart: echarts.ECharts | null = null
let residualChart: echarts.ECharts | null = null

// WebSocket连接
const toggleConnection = () => {
  if (wsConnected.value) {
    disconnectWebSocket()
  } else {
    connectWebSocket()
  }
}

const connectWebSocket = () => {
  try {
    // 使用WebSocket Bridge服务
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

const disconnectWebSocket = () => {
  if (ws) {
    ws.close()
    ws = null
    wsConnected.value = false
  }
}

// 数据源切换处理
const handleDataSourceChange = () => {
  // 切换到实时模式时，断开可能存在的连接
  if (dataSource.value === 'realtime') {
    ElMessage.info('已切换到实时数据模式')
  } else {
    // 切换到历史模式时，断开WebSocket
    if (wsConnected.value) {
      disconnectWebSocket()
    }
    ElMessage.info('已切换到历史文件模式')
  }
}

// 存储历史数据推送的定时器
let historyDataInterval: any = null

// 加载历史数据（真实TDMS数据 + 动态显示）
const loadHistoryData = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请选择历史文件')
    return
  }

  // 清除之前的定时器
  if (historyDataInterval) {
    clearInterval(historyDataInterval)
    historyDataInterval = null
    isPlayingHistory.value = false
  }

  loadingHistory.value = true
  try {
    ElMessage.info(`正在加载 ${selectedFile.value}...`)
    
    // 调用TDMS API获取真实数据（使用analyze-folder但截止频率设为采样率/2，相当于不滤波）
    const response = await axios.post(`${window.location.origin}/api/tdms/analyze-folder`, {
      folder: selectedFile.value,
      sampleRate: 100000,
      cutoffFreq: 45000,  // 接近奈奎斯特频率，相当于不滤波
      filterOrder: 2       // 低阶数，减少影响
    })

    const tdmsData = response.data
    
    // 提取真实的TDMS信号数据
    const fullNoisySignal = tdmsData.signals.noisy || []
    const sampleRate = tdmsData.parameters.sampleRate || 100000
    
    // 每批次的采样点数（模拟实时数据的批次大小）
    const batchSize = 50
    const totalBatches = Math.ceil(fullNoisySignal.length / batchSize)
    let currentBatch = 0
    
    ElMessage.success(`✅ ${selectedFile.value} 加载成功！开始动态播放...`)
    ElNotification({
      title: '文件加载成功',
      message: `共 ${fullNoisySignal.length} 个采样点，将以 ${batchSize} 个点/批次动态显示`,
      type: 'success',
      duration: 3000
    })
    
    loadingHistory.value = false
    isPlayingHistory.value = true

    // 定时推送数据（模拟实时效果）
    historyDataInterval = setInterval(() => {
      if (currentBatch >= totalBatches) {
        clearInterval(historyDataInterval)
        historyDataInterval = null
        isPlayingHistory.value = false
        ElNotification({
          title: '播放完成',
          message: `${selectedFile.value} 的所有数据已播放完毕`,
          type: 'info',
          duration: 2000
        })
        return
      }

      // 获取当前批次的数据
      const start = currentBatch * batchSize
      const end = Math.min(start + batchSize, fullNoisySignal.length)
      const batchData = fullNoisySignal.slice(start, end)

      // 构造实时数据格式
      const historyBatch = {
        type: 'signal-data',
        deviceId: selectedFile.value,
        originalSamples: batchData,
        filteredSamples: batchData,  // 初始未滤波，等待LMS处理
        currentError: 0.05,
        anomalies: [],
        statistics: {
          avgResidual: 0.05,
          processedSamples: end
        }
      }

      // 使用统一的实时数据处理函数
      handleRealtimeData(historyBatch)

      currentBatch++
    }, 500)  // 每500ms推送一批，模拟实时效果

  } catch (error) {
    console.error('加载历史文件失败:', error)
    ElMessage.error('加载失败：' + (error.response?.data?.error || error.message))
    loadingHistory.value = false
  }
}

// 停止历史数据播放
const stopHistoryPlayback = () => {
  if (historyDataInterval) {
    clearInterval(historyDataInterval)
    historyDataInterval = null
    isPlayingHistory.value = false
    ElMessage.info('已停止历史数据播放')
  }
}

// 处理实时数据
const handleRealtimeData = (data: any) => {
  console.log('处理实时数据:', {
    type: data.type,
    deviceId: data.deviceId,
    originalSamplesLength: data.originalSamples?.length,
    filteredSamplesLength: data.filteredSamples?.length,
    anomaliesCount: data.anomalies?.length || 0,
    currentError: data.currentError
  })
  
  // 更新统计信息
  stats.value.packetCount++
  stats.value.error = data.currentError || 0
  
  // 处理卡尔曼滤波数据
  if (data.statistics) {
    stats.value.snrImprovement = data.statistics.avgResidual ? (1 / data.statistics.avgResidual * 10) : calculateSNR(data)
  } else {
    stats.value.snrImprovement = calculateSNR(data)
  }
  
  // 计算处理速度 (samples/second)
  const samplesReceived = data.originalSamples?.length || data.statistics?.processedSamples || 0
  stats.value.throughput = samplesReceived * 2 // 500ms间隔 = 2Hz
  
  // 处理异常检测结果
  if (anomalyDetectionEnabled.value && data.anomalies && data.anomalies.length > 0) {
    processAnomalyResults(data.anomalies)
  }
  
  console.log('统计更新:', stats.value, '异常统计:', anomalyStats.value)

  // 更新波形图
  updateWaveformChart(data)
  
  // 更新残差图
  updateResidualChart(data)
  
  // 更新频谱图  
  updateSpectrumChart(data)
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

// 计算SNR改善
const calculateSNR = (data: any): number => {
  if (!data.originalSamples || !data.filteredSamples) return 0
  
  // 简化计算：比较原始信号和滤波后信号的方差
  const originalVar = calculateVariance(data.originalSamples)
  const filteredVar = calculateVariance(data.filteredSamples)
  
  if (filteredVar === 0) return 0
  return 10 * Math.log10(originalVar / filteredVar)
}

const calculateVariance = (samples: number[]): number => {
  if (!samples || samples.length === 0) return 0
  const mean = samples.reduce((sum, val) => sum + val, 0) / samples.length
  const variance = samples.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) / samples.length
  return variance
}

// 更新残差图
const updateResidualChart = (data: any) => {
  if (!residualChart) return

  // 计算残差：原始信号 - 滤波后信号
  let residuals = data.residuals || []
  let uncertainties = data.uncertainties || []
  
  // 如果没有提供残差数据，则自己计算
  if (residuals.length === 0 && data.originalSamples && data.filteredSamples) {
    const original = data.originalSamples
    const filtered = data.filteredSamples
    const minLength = Math.min(original.length, filtered.length)
    
    residuals = []
    for (let i = 0; i < minLength; i++) {
      residuals.push(original[i] - filtered[i])
    }
    
    // 计算滑动标准差作为不确定性
    uncertainties = residuals.map((val, idx, arr) => {
      const window = 10
      const start = Math.max(0, idx - window)
      const end = Math.min(arr.length, idx + window)
      const slice = arr.slice(start, end)
      const mean = slice.reduce((a, b) => a + b, 0) / slice.length
      const variance = slice.reduce((sum, v) => sum + Math.pow(v - mean, 2), 0) / slice.length
      return Math.sqrt(variance)
    })
  }

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

  // 降采样：只显示500个点，让图表更清晰
  const maxDisplayPoints = 500
  const downsampleFactor = Math.max(1, Math.floor(originalSamples.length / maxDisplayPoints))
  
  const downsampledOriginal = downsampleFactor > 1 
    ? originalSamples.filter((_, index) => index % downsampleFactor === 0)
    : originalSamples
  
  const downsampledFiltered = downsampleFactor > 1
    ? filteredSamples.filter((_, index) => index % downsampleFactor === 0)
    : filteredSamples

  const option = {
    title: {
      text: `设备: ${data.deviceId || selectedDevice.value}`,
      subtext: downsampleFactor > 1 ? `显示 ${downsampledOriginal.length}/${originalSamples.length} 点 (降采样 1:${downsampleFactor})` : `${originalSamples.length} 个采样点`,
      left: 'center'
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      }
    },
    legend: {
      data: ['原始信号', '滤波后信号'],
      bottom: 10
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
      boundaryGap: false
    },
    yAxis: {
      type: 'value',
      name: '幅值'
    },
    dataZoom: [
      {
        type: 'inside',
        start: 0,
        end: 100
      },
      {
        start: 0,
        end: 100
      }
    ],
    series: [
      {
        name: '原始信号',
        type: 'line',
        data: downsampledOriginal,
        sampling: 'lttb', // 大数据量优化
        lineStyle: {
          width: 1.5,
          color: '#5470c6'
        },
        showSymbol: false
      },
      {
        name: '滤波后信号',
        type: 'line',
        data: downsampledFiltered,
        sampling: 'lttb',
        lineStyle: {
          width: 1.5,
          color: '#91cc75'
        },
        showSymbol: false
      }
    ]
  }

  waveformChart.setOption(option)
}

// 更新频谱图
const updateSpectrumChart = (data: any) => {
  if (!spectrumChart) return
  
  const samples = data.filteredSamples || data.originalSamples
  if (!samples || samples.length === 0) return

  // 简化的频谱显示（基于信号幅值的频域近似）
  // 对采样数据进行分组，计算每组的平均幅值
  const numBins = Math.min(50, Math.floor(samples.length / 10))
  const binSize = Math.floor(samples.length / numBins)
  
  const spectrumData = []
  for (let i = 0; i < numBins; i++) {
    const start = i * binSize
    const end = start + binSize
    const binSamples = samples.slice(start, end)
    const avgAmplitude = binSamples.reduce((sum, val) => sum + Math.abs(val), 0) / binSamples.length
    spectrumData.push({
      value: avgAmplitude,
      name: `${Math.round(i * 100)}Hz`
    })
  }

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
  ElMessage.info(`切换到设备: ${value}`)
  // 可以发送消息到backend.jar切换设备
  if (ws && wsConnected.value) {
    ws.send(JSON.stringify({
      action: 'changeDevice',
      deviceId: value
    }))
  }
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
    // 设置初始占位数据
    residualChart.setOption({
      title: { text: '卡尔曼滤波残差分析', left: 'center' },
      xAxis: { type: 'category', name: '采样点', data: [] },
      yAxis: { type: 'value', name: '残差' },
      series: [{ type: 'line', data: [], smooth: true }],
      grid: { left: '10%', right: '10%', bottom: '15%' }
    })
  }

  // 初始化频谱图
  const spectrumEl = document.getElementById('spectrum-chart')
  if (spectrumEl) {
    spectrumChart = echarts.init(spectrumEl)
    // 设置初始占位数据
    spectrumChart.setOption({
      title: { text: '频谱分析', left: 'center' },
      xAxis: { type: 'category', name: '频率 (Hz)', data: [] },
      yAxis: { type: 'value', name: '幅值' },
      series: [{ type: 'bar', data: [] }],
      grid: { left: '10%', right: '10%', bottom: '15%' }
    })
  }

  // 窗口大小变化时重绘
  window.addEventListener('resize', () => {
    waveformChart?.resize()
    residualChart?.resize()
    spectrumChart?.resize()
  })
})

onUnmounted(() => {
  disconnectWebSocket()
  // 清理历史数据播放定时器
  if (historyDataInterval) {
    clearInterval(historyDataInterval)
    historyDataInterval = null
    isPlayingHistory.value = false
  }
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
}
</style>
