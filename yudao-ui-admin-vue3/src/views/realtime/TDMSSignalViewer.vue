<template>
  <ContentWrap title="TDMS历史信号分析 - 实时回放">
    <el-row :gutter="20">
      <!-- 文件选择区域 -->
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>📁 选择信号文件</span>
              <el-button type="primary" text @click="refreshFileList">
                <el-icon><Refresh /></el-icon>
                刷新列表
              </el-button>
            </div>
          </template>

          <el-row :gutter="20">
            <!-- Signal-1 文件夹 -->
            <el-col :span="8">
              <el-card shadow="never" :class="{ 'selected-folder': selectedFile === 'signal-1' }">
                <template #header>
                  <span>Signal-1 文件夹</span>
                  <el-tag size="small" type="info" style="margin-left: 10px">单文件多通道</el-tag>
                </template>
                <div class="folder-item" @click="handleFolderSelect('signal-1')">
                  <el-icon><Folder /></el-icon>
                  <span style="margin-left: 10px">📁 signal-1</span>
                  <div class="folder-desc">点击选择此文件夹</div>
                </div>
              </el-card>
            </el-col>

            <!-- Signal-2 文件夹 -->
            <el-col :span="8">
              <el-card shadow="never" :class="{ 'selected-folder': selectedFile === 'signal-2' }">
                <template #header>
                  <span>Signal-2 文件夹</span>
                  <el-tag size="small" type="success" style="margin-left: 10px">多文件组合</el-tag>
                </template>
                <div class="folder-item" @click="handleFolderSelect('signal-2')">
                  <el-icon><Folder /></el-icon>
                  <span style="margin-left: 10px">📁 signal-2</span>
                  <div class="folder-desc">点击选择此文件夹（3个文件）</div>
                </div>
              </el-card>
            </el-col>

            <!-- 上传自定义文件 -->
            <el-col :span="8">
              <el-card shadow="never" :class="{ 'selected-folder': selectedFile === 'uploaded' }">
                <template #header>
                  <span>📤 上传TDMS文件</span>
                  <el-tag size="small" type="warning" style="margin-left: 10px">自定义</el-tag>
                </template>
                <el-upload
                  ref="uploadRef"
                  class="upload-area"
                  drag
                  :auto-upload="false"
                  :show-file-list="false"
                  accept=".tdms"
                  :on-change="handleFileChange"
                >
                  <el-icon class="el-icon--upload" :size="40"><UploadFilled /></el-icon>
                  <div class="el-upload__text">
                    拖拽TDMS文件到此处<br/>或 <em>点击选择</em>
                  </div>
                </el-upload>
                <div v-if="uploadedFileName" class="uploaded-file-info">
                  <el-icon><Document /></el-icon>
                  <span>{{ uploadedFileName }}</span>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <!-- 播放控制 -->
          <el-row :gutter="20" style="margin-top: 15px">
            <el-col :span="12">
              <div class="control-item">
                <span>播放速度: {{ playbackSpeed }}x</span>
                <el-slider
                  v-model="playbackSpeed"
                  :min="0.01"
                  :max="5"
                  :step="0.01"
                  :format-tooltip="(val: number) => `${val}x`"
                />
              </div>
            </el-col>
            <el-col :span="12">
              <div class="control-item">
                <span>显示窗口: {{ windowSize }} 点</span>
                <el-slider
                  v-model="windowSize"
                  :min="50"
                  :max="1000"
                  :step="100"
                  :format-tooltip="(val: number) => `${val} 点`"
                />
              </div>
            </el-col>
          </el-row>

          <!-- 文件信息 -->
          <el-descriptions v-if="fileInfo" :column="4" border style="margin-top: 20px">
            <el-descriptions-item label="文件名">{{ fileInfo.name }}</el-descriptions-item>
            <el-descriptions-item label="采样率">{{ fileInfo.sampleRate }} Hz</el-descriptions-item>
            <el-descriptions-item label="采样点数">{{ fileInfo.samples?.toLocaleString() }}</el-descriptions-item>
            <el-descriptions-item label="时长">{{ fileInfo.duration }} 秒</el-descriptions-item>
          </el-descriptions>

          <!-- 操作按钮 -->
          <div style="margin-top: 20px; text-align: center">
            <el-button
              type="primary"
              :loading="analyzing"
              :disabled="!selectedFile || isPlaying"
              @click="handleAnalyze"
              size="large"
            >
              <el-icon><DataAnalysis /></el-icon>
              {{ analyzing ? '加载数据中...' : '加载数据' }}
            </el-button>
            <el-button
              type="success"
              :disabled="!signalData"
              @click="togglePlayback"
              size="large"
            >
              <el-icon><VideoPlay v-if="!isPlaying" /><VideoPause v-else /></el-icon>
              {{ isPlaying ? '暂停播放' : '开始播放' }}
            </el-button>
            <el-button
              type="warning"
              :disabled="!signalData"
              @click="resetPlayback"
              size="large"
            >
              <el-icon><RefreshLeft /></el-icon>
              重置
            </el-button>
          </div>

          <!-- 播放进度 -->
          <div v-if="signalData" style="margin-top: 15px">
            <el-progress
              :percentage="playbackProgress"
              :format="() => `${currentTime.toFixed(3)}s / ${totalTime.toFixed(3)}s`"
              :stroke-width="20"
              striped
              :striped-flow="isPlaying"
            />
          </div>
        </el-card>
      </el-col>

      <!-- 信号可视化区域 -->
      <el-col :span="24" style="margin-top: 20px" v-if="signalData">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>📊 实时信号波形 - {{ isPlaying ? '播放中' : '已暂停' }}</span>
              <el-tag :type="isPlaying ? 'success' : 'info'">
                {{ currentSampleIndex.toLocaleString() }} / {{ totalSamples.toLocaleString() }} 采样点
              </el-tag>
            </div>
          </template>

          <el-row :gutter="20">
            <el-col :span="12">
              <div id="signal-chart-1" style="height: 350px"></div>
            </el-col>
            <el-col :span="12">
              <div id="signal-chart-2" style="height: 350px"></div>
            </el-col>
            <el-col :span="12" style="margin-top: 15px">
              <div id="signal-chart-3" style="height: 350px"></div>
            </el-col>
            <el-col :span="12" style="margin-top: 15px">
              <div id="signal-chart-4" style="height: 350px"></div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>

      <!-- 性能指标 -->
      <el-col :span="24" style="margin-top: 20px" v-if="metrics">
        <el-card shadow="hover">
          <template #header>
            <span>📈 滤波性能指标</span>
          </template>
          <el-row :gutter="20">
            <el-col :span="6">
              <el-statistic title="MSE 改善" :value="metrics.mseImprovement">
                <template #suffix>%</template>
              </el-statistic>
            </el-col>
            <el-col :span="6">
              <el-statistic title="滤波前 MSE" :value="metrics.mseBefore" :precision="6" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="滤波后 MSE" :value="metrics.mseAfter" :precision="6" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="相关系数" :value="metrics.correlation" :precision="4" />
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, DataAnalysis, Folder, VideoPlay, VideoPause, RefreshLeft, UploadFilled, Document } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import axios from 'axios'

// 状态
const analyzing = ref(false)
const selectedFile = ref('')
const fileInfo = ref<any>(null)
const signalData = ref<any>(null)
const metrics = ref<any>(null)
const uploadedFileName = ref('')
const uploadedFile = ref<File | null>(null)

// 播放控制
const isPlaying = ref(false)
const playbackSpeed = ref(0.1) // 播放速度倍数
const windowSize = ref(300) // 显示窗口大小（采样点数）
const currentSampleIndex = ref(0) // 当前播放位置

// 图表实例
let charts: echarts.ECharts[] = []
let animationFrameId: number | null = null
let lastUpdateTime = 0

// API基础地址
const API_BASE = window.location.origin

// 计算属性
const totalSamples = computed(() => signalData.value?.time?.length || 0)
const totalTime = computed(() => totalSamples.value / 100000) // 假设采样率100kHz
const currentTime = computed(() => currentSampleIndex.value / 100000)
const playbackProgress = computed(() => 
  totalSamples.value > 0 ? (currentSampleIndex.value / totalSamples.value) * 100 : 0
)

// 刷新文件列表
const refreshFileList = async () => {
  try {
    await axios.get(`${API_BASE}/api/tdms/files`)
    ElMessage.success('文件列表已刷新')
  } catch (error) {
    console.error('刷新文件列表失败:', error)
  }
}

// 选择文件夹
const handleFolderSelect = (folder: string) => {
  selectedFile.value = folder
  uploadedFile.value = null
  uploadedFileName.value = ''
  
  if (folder === 'signal-1') {
    fileInfo.value = {
      name: 'signal-1 (ae_sim_2s.tdms)',
      sampleRate: 100000,
      samples: 200000,
      duration: 2.0
    }
  } else if (folder === 'signal-2') {
    fileInfo.value = {
      name: 'signal-2 (3个文件组合)',
      sampleRate: 100000,
      samples: 200000,
      duration: 2.0
    }
  }
  
  ElMessage.success(`已选择 ${folder}`)
}

// 处理文件选择
const handleFileChange = (file: any) => {
  if (!file.raw.name.endsWith('.tdms')) {
    ElMessage.error('请选择 .tdms 格式的文件')
    return
  }
  
  uploadedFile.value = file.raw
  uploadedFileName.value = file.raw.name
  selectedFile.value = 'uploaded'
  
  fileInfo.value = {
    name: file.raw.name,
    sampleRate: 100000,
    samples: '待分析',
    duration: '待分析'
  }
  
  ElMessage.success(`已选择文件: ${file.raw.name}`)
}

// 加载数据
const handleAnalyze = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择一个文件夹或上传文件')
    return
  }

  analyzing.value = true
  stopPlayback()
  
  try {
    let res
    
    if (selectedFile.value === 'uploaded' && uploadedFile.value) {
      // 上传文件模式
      const formData = new FormData()
      formData.append('file', uploadedFile.value)
      formData.append('sampleRate', '100000')
      formData.append('cutoffFreq', '10000')
      formData.append('filterOrder', '6')
      
      res = await axios.post(`${API_BASE}/api/tdms/analyze-upload`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
    } else {
      // 文件夹模式
      res = await axios.post(`${API_BASE}/api/tdms/analyze-folder`, {
        folder: selectedFile.value,
        sampleRate: 100000,
        cutoffFreq: 10000,
        filterOrder: 6
      })
    }
    
    signalData.value = res.data.signals
    metrics.value = res.data.metrics
    currentSampleIndex.value = 0
    
    // 更新文件信息
    if (res.data.parameters) {
      fileInfo.value = {
        ...fileInfo.value,
        samples: res.data.parameters.totalSamples,
        duration: (res.data.parameters.totalSamples / 100000).toFixed(2)
      }
    }
    
    await nextTick()
    initCharts()
    
    ElMessage.success(`✅ 数据加载完成！共 ${totalSamples.value.toLocaleString()} 个采样点，点击"开始播放"查看实时波形`)
  } catch (error: any) {
    console.error('加载失败:', error)
    ElMessage.error(error.response?.data?.error || '数据加载失败，请检查后端服务')
  } finally {
    analyzing.value = false
  }
}

// 初始化图表
const initCharts = () => {
  charts.forEach(chart => chart?.dispose())
  charts = []
  
  const chartIds = ['signal-chart-1', 'signal-chart-2', 'signal-chart-3', 'signal-chart-4']
  const titles = ['① 原始信号 (Sine)', '② 加噪信号 (Noisy)', '③ 滤波后信号 (Filtered)', '④ 效果对比']
  
  chartIds.forEach((id, index) => {
    const el = document.getElementById(id)
    if (!el) return
    
    const chart = echarts.init(el)
    charts.push(chart)
    
    // 初始化空图表
    chart.setOption({
      title: { text: titles[index], left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
      legend: { bottom: 0, data: index === 3 ? ['原始信号', '滤波后'] : [] },
      xAxis: { type: 'value', name: '时间 (ms)', min: 0, max: windowSize.value / 100 },
      yAxis: { type: 'value', name: '幅值', min: -1.5, max: 1.5 },
      series: [],
      grid: { left: '12%', right: '5%', bottom: '18%', top: '15%' },
      animation: false // 关闭动画提高性能
    })
  })
  
  // 初始显示第一帧
  updateChartsFrame()
}

// 更新图表帧
const updateChartsFrame = () => {
  if (!signalData.value || charts.length === 0) return
  
  const { time, sine, noisy, filtered } = signalData.value
  const startIdx = currentSampleIndex.value
  const endIdx = Math.min(startIdx + windowSize.value, totalSamples.value)
  
  // 提取当前窗口数据
  const windowTime = time.slice(startIdx, endIdx)
  const windowSine = sine.slice(startIdx, endIdx)
  const windowNoisy = noisy.slice(startIdx, endIdx)
  const windowFiltered = filtered.slice(startIdx, endIdx)
  
  // 转换为 [x, y] 格式
  const sineData = windowTime.map((t: number, i: number) => [t, windowSine[i]])
  const noisyData = windowTime.map((t: number, i: number) => [t, windowNoisy[i]])
  const filteredData = windowTime.map((t: number, i: number) => [t, windowFiltered[i]])
  
  const xMin = windowTime[0] || 0
  const xMax = windowTime[windowTime.length - 1] || xMin + windowSize.value / 100
  
  // 更新各图表
  const seriesConfigs = [
    [{ name: '原始信号', data: sineData, color: '#5470c6' }],
    [{ name: '加噪信号', data: noisyData, color: '#ee6666' }],
    [{ name: '滤波后', data: filteredData, color: '#91cc75' }],
    [
      { name: '原始信号', data: sineData, color: '#5470c6' },
      { name: '滤波后', data: filteredData, color: '#91cc75', lineStyle: { type: 'dashed' } }
    ]
  ]
  
  charts.forEach((chart, index) => {
    if (!chart) return
    
    chart.setOption({
      xAxis: { min: xMin, max: xMax },
      series: seriesConfigs[index].map(cfg => ({
        name: cfg.name,
        type: 'line',
        data: cfg.data,
        showSymbol: false,
        itemStyle: { color: cfg.color },
        lineStyle: { width: 1.5, ...(cfg.lineStyle || {}) }
      }))
    })
  })
}

// 播放动画循环
const playbackLoop = (timestamp: number) => {
  if (!isPlaying.value) return
  
  const deltaTime = timestamp - lastUpdateTime
  const updateInterval = 1000 / 60 // 60fps
  
  if (deltaTime >= updateInterval) {
    // 根据播放速度计算步进
    const samplesPerFrame = Math.floor(100000 * playbackSpeed.value / 60) // 采样率 * 速度 / fps
    currentSampleIndex.value += samplesPerFrame
    
    // 循环播放
    if (currentSampleIndex.value >= totalSamples.value - windowSize.value) {
      currentSampleIndex.value = 0
    }
    
    updateChartsFrame()
    lastUpdateTime = timestamp
  }
  
  animationFrameId = requestAnimationFrame(playbackLoop)
}

// 开始/暂停播放
const togglePlayback = () => {
  if (isPlaying.value) {
    stopPlayback()
  } else {
    startPlayback()
  }
}

const startPlayback = () => {
  if (!signalData.value) return
  isPlaying.value = true
  lastUpdateTime = performance.now()
  animationFrameId = requestAnimationFrame(playbackLoop)
  ElMessage.success('▶️ 开始播放')
}

const stopPlayback = () => {
  isPlaying.value = false
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
    animationFrameId = null
  }
}

// 重置播放
const resetPlayback = () => {
  stopPlayback()
  currentSampleIndex.value = 0
  updateChartsFrame()
  ElMessage.info('已重置到起始位置')
}

// 生命周期
onMounted(() => {
  refreshFileList()
  window.addEventListener('resize', () => charts.forEach(c => c?.resize()))
})

onUnmounted(() => {
  stopPlayback()
  charts.forEach(chart => chart?.dispose())
})
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.folder-item {
  padding: 20px;
  cursor: pointer;
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  transition: all 0.3s;
  text-align: center;
  
  &:hover {
    border-color: #409eff;
    background-color: #ecf5ff;
  }
  
  .folder-desc {
    margin-top: 10px;
    font-size: 12px;
    color: #909399;
  }
}

.selected-folder {
  :deep(.el-card__body) {
    .folder-item, .upload-area {
      border-color: #67c23a;
      background-color: #f0f9eb;
    }
  }
}

.upload-area {
  :deep(.el-upload-dragger) {
    padding: 15px;
    border-radius: 8px;
  }
  
  .el-icon--upload {
    color: #409eff;
    margin-bottom: 10px;
  }
}

.uploaded-file-info {
  margin-top: 10px;
  padding: 8px;
  background: #f0f9eb;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #67c23a;
}

.control-item {
  span {
    display: block;
    margin-bottom: 8px;
    font-size: 13px;
    color: #606266;
  }
}

.playback-controls {
  padding: 10px;
}
</style>
