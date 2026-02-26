<template>
  <div class="detection-container dark-theme">
    <!-- 1. 顶部：上传文件区 -->
    <el-card shadow="never" class="mb-4 upload-card">
      <template #header>
        <div class="flex items-center justify-between">
          <span>传文件</span>
          <el-tag v-if="currentTask.filename" type="info" size="small">{{ currentTask.filename }}</el-tag>
        </div>
      </template>
      <el-upload
        class="tdms-uploader"
        drag
        action="#"
        :auto-upload="false"
        :on-change="handleFileChange"
        accept=".tdms"
      >
        <Icon icon="ep:upload-filled" class="upload-icon" />
        <div class="el-upload__text">
          拖拽文件到此处或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip text-center">仅支持 .tdms 文件</div>
        </template>
      </el-upload>
    </el-card>

    <!-- 2. 中间：算法配置区 -->
    <el-card shadow="never" class="mb-4 config-card">
      <template #header>选择滤波算法</template>
      <el-form :model="config" label-position="top" class="config-form">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="滤波算法">
              <el-select v-model="config.algorithm" placeholder="选择算法" class="w-full">
                <el-option
                  v-for="algo in algorithms"
                  :key="algo.id"
                  :label="`${algo.id} - ${algo.name}`"
                  :value="algo.id"
                >
                  <span style="float: left">{{ algo.id }} - {{ algo.name }}</span>
                  <span style="float: right; color: #8492a6; font-size: 13px; margin-left: 10px">{{ algo.desc }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="过程噪声">
              <el-input-number v-model="config.processNoise" :precision="4" :step="0.001" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="测量噪声">
              <el-input-number v-model="config.measurementNoise" :precision="4" :step="0.01" class="w-full" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="flex justify-center mt-4 gap-4">
          <el-button type="info" plain icon="ep:setting">告警配置</el-button>
          <el-button 
            type="primary" 
            :loading="processing" 
            @click="startDetection"
            :disabled="!selectedFile"
          >
            <Icon icon="ep:video-play" class="mr-1" />
            {{ processing ? '处理中...' : '开始处理' }}
          </el-button>
        </div>
      </el-form>
    </el-card>

    <!-- 3. 下部：处理进度与详情 -->
    <el-card shadow="never" class="mb-4 progress-card">
      <template #header>
        <div class="flex justify-between items-center">
          <span>处理进度</span>
          <el-tag v-if="processing" type="warning" size="small">处理中</el-tag>
        </div>
      </template>
      <div class="progress-wrapper">
        <el-progress 
          :percentage="progress" 
          :stroke-width="15" 
          :color="progressColors"
          striped
          striped-flow
        />
        <div class="task-details mt-4">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="任务ID">{{ currentTask.id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="文件名">{{ currentTask.filename || '-' }}</el-descriptions-item>
            <el-descriptions-item label="滤波算法">{{ currentTask.algorithm || '-' }}</el-descriptions-item>
            <el-descriptions-item label="文件大小">{{ currentTask.size || '0 MB' }}</el-descriptions-item>
            <el-descriptions-item label="已处理通道">{{ currentTask.channels || '0/0' }}</el-descriptions-item>
            <el-descriptions-item label="已用时间">{{ currentTask.duration || '0s' }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </el-card>

    <!-- 4. 可视化：波形展示（支持回放）与 异常信息 -->
    <el-row :gutter="20">
      <el-col :span="16">
        <el-card shadow="never" class="viz-card">
          <template #header>
            <div class="flex justify-between items-center">
              <span>信号可视化回放</span>
              <div class="flex items-center gap-2">
                <el-button-group size="small">
                  <el-button type="primary" :icon="isPlaying ? 'ep:video-pause' : 'ep:video-play'" @click="togglePlayback">
                    {{ isPlaying ? '暂停' : '播放' }}
                  </el-button>
                  <el-button icon="ep:refresh" @click="resetPlayback">重置</el-button>
                </el-button-group>
                <el-slider v-model="playbackIndex" :max="totalPoints" :show-tooltip="false" class="playback-slider" @change="onPlaybackChange" />
              </div>
            </div>
          </template>
          <WaveformChart :data="displayRaw" title="原始信号" height="220px" color="#909399" />
          <WaveformChart :data="displayFiltered" title="滤波后信号" height="220px" color="#409EFF" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="anomaly-card">
          <template #header>
            <div class="flex justify-between items-center">
              <span>实时异常告警</span>
              <el-badge :value="anomalies.length" type="danger" />
            </div>
          </template>
          <div class="anomaly-list">
            <el-timeline>
              <el-timeline-item
                v-for="(item, index) in anomalies"
                :key="index"
                :timestamp="formatTime(item.timestamp)"
                :type="item.severity === 'high' ? 'danger' : 'warning'"
              >
                <div class="anomaly-content">
                  <div class="font-bold">能量异常: {{ item.energy.toFixed(2) }}</div>
                  <div class="text-xs text-gray-500">检测值: {{ item.value.toFixed(3) }}</div>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-if="anomalies.length === 0" description="暂无异常数据" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { Icon } from '@iconify/vue'
import WaveformChart from './components/WaveformChart.vue'
import dayjs from 'dayjs'
import { useMessage } from '@/hooks/web/useMessage'
import { DetectionApi } from '@/api/monitor/detection'

// --- 状态定义 ---
const message = useMessage()
const selectedFile = ref<File | null>(null)
const processing = ref(false)
const progress = ref(0)
const isPlaying = ref(false)
const playbackIndex = ref(0)
const totalPoints = ref(1000)

const config = reactive({
  algorithm: 'KALMAN',
  processNoise: 0.01,
  measurementNoise: 0.1
})

const algorithms = [
  { id: 'KALMAN', name: '卡尔曼滤波', desc: '最优估计' },
  { id: 'LMS', name: 'LMS自适应', desc: '快速收敛' },
  { id: 'NLMS', name: 'NLMS归一化', desc: '稳定性好' },
  { id: 'RLS', name: 'RLS递推', desc: '高精度' },
  { id: 'MEAN', name: '均值滤波', desc: '简单高效' },
  { id: 'MEDIAN', name: '中值滤波', desc: '抗脉冲' },
  { id: 'GAUSSIAN', name: '高斯滤波', desc: '平滑效果' },
  { id: 'BUTTERWORTH', name: '巴特沃斯', desc: '频率选择' },
  { id: 'CHEBYSHEV', name: '切比雪夫', desc: '陡峭过渡' },
  { id: 'FIR', name: 'FIR滤波', desc: '线性相位' },
  { id: 'IIR', name: 'IIR滤波', desc: '高效实现' },
  { id: 'WIENER', name: '维纳滤波', desc: '最优线性' },
  { id: 'WAVELET', name: '小波滤波', desc: '多尺度' },
  { id: 'MORPHOLOGY', name: '形态学', desc: '形状保持' },
  { id: 'BILATERAL', name: '双边滤波', desc: '边缘保护' },
  { id: 'SG', name: 'SG平滑', desc: '多项式' },
  { id: 'PARTICLE', name: '粒子滤波', desc: '非线性' },
  { id: 'EKF', name: '扩展卡尔曼', desc: 'EKF' },
  { id: 'UKF', name: '无损卡尔曼', desc: 'UKF' },
  { id: 'NOTCH', name: '自适应陷波', desc: '频率消除' }
]

const currentTask = reactive({
  id: '',
  filename: '',
  algorithm: 'kalman',
  size: '0 MB',
  channels: '0/0',
  duration: '0s',
  status: ''
})

// 原始全量数据
const allRawData = ref<any[]>([])
const allFilteredData = ref<any[]>([])

// 当前显示的切片数据（用于回放）
const displayRaw = ref<any[]>([])
const displayFiltered = ref<any[]>([])
const anomalies = ref<any[]>([])

const progressColors = [
  { color: '#f56c6c', percentage: 20 },
  { color: '#e6a23c', percentage: 40 },
  { color: '#5cb87a', percentage: 60 },
  { color: '#1989fa', percentage: 80 },
  { color: '#6f7ad3', percentage: 100 },
]

// --- 方法 ---

const handleFileChange = (file: any) => {
  selectedFile.value = file.raw
  currentTask.filename = file.name
  currentTask.size = (file.size / 1024 / 1024).toFixed(2) + ' MB'
}

let socket: WebSocket | null = null
const isConnected = ref(false)

const startDetection = async () => {
  if (!selectedFile.value) return
  
  processing.value = true
  progress.value = 0
  anomalies.value = [] // 清空旧异常
  
  try {
    const res = await DetectionApi.uploadAndDetect(selectedFile.value, config.algorithm)
    currentTask.id = res.id
    currentTask.algorithm = res.algorithm
    currentTask.status = res.status
    
    // 启动 WebSocket 监听实时异常
    connectWebSocket()
    
    // 轮询任务状态
    const statusInterval = setInterval(async () => {
      const statusRes = await DetectionApi.getTaskStatus(currentTask.id)
      progress.value = statusRes.progress
      currentTask.status = statusRes.status
      
      if (statusRes.status === 'COMPLETED') {
        clearInterval(statusInterval)
        processing.value = false
        message.success('处理完成！')
        fetchTaskResults(currentTask.id) // 任务完成后加载全量数据进行回放
      } else if (statusRes.status === 'FAILED') {
        clearInterval(statusInterval)
        processing.value = false
        message.error('处理失败')
      }
    }, 1000)
  } catch (e) {
    processing.value = false
    message.error('任务启动失败')
  }
}

const fetchTaskResults = async (taskId: string) => {
  try {
    const data = await DetectionApi.getTaskResults(taskId)
    const raw: any[] = []
    const filtered: any[] = []
    
    data.forEach((r: any) => {
      raw.push([r.timestamp, r.originalValue])
      filtered.push([r.timestamp, r.filteredValue])
    })
    
    allRawData.value = raw
    allFilteredData.value = filtered
    totalPoints.value = data.length
    playbackIndex.value = 0
    startPlayback()
  } catch (e) {
    message.error('加载检测结果失败')
  }
}

// 这里的 connectWebSocket 逻辑需要稍微调整：
// 如果已经连接则不重连，且接收到数据后只更新 anomalies 列表（实时展示）
const connectWebSocket = () => {
  if (socket && socket.readyState === WebSocket.OPEN) return

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.hostname}:48080/ws/detection`
  
  socket = new WebSocket(wsUrl)
  
  socket.onopen = () => {
    isConnected.value = true
    console.log('Detection WebSocket Connected')
  }
  
  socket.onmessage = (event) => {
    const data = JSON.parse(event.data)
    
    // 实时更新异常列表
    if (data.anomaly) {
      anomalies.value.unshift({
        timestamp: data.timestamp,
        energy: data.energy || 0,
        value: data.originalValue || 0,
        severity: (data.energy > 50) ? 'high' : 'medium'
      })
      if (anomalies.value.length > 50) anomalies.value.pop()
    }
  }
  
  socket.onclose = () => {
    isConnected.value = false
  }
}

let playbackTimer: any = null
const startPlayback = () => {
  if (playbackTimer) clearInterval(playbackTimer)
  isPlaying.ref = true
  
  playbackTimer = setInterval(() => {
    if (playbackIndex.value < totalPoints.value) {
      playbackIndex.value += 5
      updateDisplayData()
    } else {
      pausePlayback()
    }
  }, 100)
}

const pausePlayback = () => {
  isPlaying.value = false
  if (playbackTimer) clearInterval(playbackTimer)
}

const togglePlayback = () => {
  if (isPlaying.value) pausePlayback()
  else startPlayback()
}

const resetPlayback = () => {
  playbackIndex.value = 0
  updateDisplayData()
  pausePlayback()
}

const onPlaybackChange = () => {
  updateDisplayData()
}

const updateDisplayData = () => {
  const windowSize = 100 // 显示窗口大小
  const end = playbackIndex.value
  const start = Math.max(0, end - windowSize)
  
  displayRaw.value = allRawData.value.slice(start, end)
  displayFiltered.value = allFilteredData.value.slice(start, end)
}

const formatTime = (ts: number) => dayjs(ts).format('HH:mm:ss.SSS')

onMounted(() => {
  // 初始页面为空，等待用户上传文件
})

onUnmounted(() => {
  if (playbackTimer) clearInterval(playbackTimer)
})
</script>

<style scoped>
.detection-container {
  padding: 20px;
  background: #0d1117;
  min-height: calc(100vh - 84px);
  color: #c9d1d9;
}

:deep(.el-card) {
  background: #161b22 !important;
  border: 1px solid #30363d !important;
  color: #c9d1d9 !important;
}

:deep(.el-card__header) {
  border-bottom: 1px solid #30363d !important;
  font-weight: bold;
  color: #f0f6fc;
}

/* 上传区样式 */
.tdms-uploader :deep(.el-upload-dragger) {
  background-color: #0d1117;
  border: 2px dashed #30363d;
}
.tdms-uploader :deep(.el-upload-dragger:hover) {
  border-color: #58a6ff;
}
.upload-icon {
  font-size: 48px;
  color: #8b949e;
  margin-bottom: 10px;
}

/* 进度条样式 */
.progress-wrapper {
  padding: 10px 0;
}
:deep(.el-progress-bar__outer) {
  background-color: #30363d !important;
}

/* 播放控制条样式 */
.playback-slider {
  width: 200px;
  margin-left: 15px;
}

/* 异常列表样式 */
.anomaly-list {
  height: 480px;
  overflow-y: auto;
  padding: 10px;
}
.anomaly-content {
  background: rgba(248, 81, 73, 0.1);
  padding: 8px;
  border-radius: 4px;
}

/* 布局调整 */
.w-full { width: 100%; }
.gap-4 { gap: 1rem; }
.mb-4 { margin-bottom: 1rem; }
.mt-4 { margin-top: 1rem; }
.mr-1 { margin-right: 0.25rem; }

:deep(.el-form-item__label) {
  color: #8b949e !important;
}

:deep(.el-descriptions__label) {
  background: #0d1117 !important;
  color: #8b949e !important;
}
:deep(.el-descriptions__content) {
  background: #161b22 !important;
  color: #c9d1d9 !important;
}
</style>
