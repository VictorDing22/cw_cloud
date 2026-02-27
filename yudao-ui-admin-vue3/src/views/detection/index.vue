<template>
  <div class="detection-container">
    <!-- 顶部：上传 + 配置（桌面端两列，移动端一列） -->
    <el-row :gutter="20" class="mb-4">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="upload-card">
      <template #header>
        <div class="flex items-center justify-between">
              <span class="text-lg font-bold">上传 TDMS 文件</span>
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
            <div class="el-upload__text">拖拽文件到此处或 <em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip text-center">仅支持 .tdms 文件</div>
        </template>
      </el-upload>
    </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="config-card">
      <template #header>
            <span class="text-lg font-bold">参数配置</span>
      </template>
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
                      <span style="float: right; color: #909399; font-size: 13px; margin-left: 10px">{{ algo.desc }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="过程噪声 (Q)">
              <el-input-number v-model="config.processNoise" :precision="4" :step="0.001" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="测量噪声 (R)">
              <el-input-number v-model="config.measurementNoise" :precision="4" :step="0.01" class="w-full" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="flex justify-center mt-4 gap-4">
          <el-button type="info" plain icon="ep:setting">告警阈值配置</el-button>
          <el-button 
            type="primary" 
            size="large"
            :loading="processing" 
            @click="startDetection"
            :disabled="!selectedFile"
            class="start-btn"
          >
            <Icon icon="ep:video-play" class="mr-1" />
            {{ processing ? '正在处理数据...' : '触发检测流程' }}
          </el-button>
        </div>
      </el-form>
    </el-card>
      </el-col>
    </el-row>

    <!-- 3. 下部：处理进度与详情 -->
    <el-card shadow="never" class="mb-4 progress-card" v-if="currentTask.id">
      <template #header>
        <div class="flex justify-between items-center">
          <span class="text-lg font-bold">处理进度</span>
          <el-tag :type="statusTagType" size="small">{{ statusText }}</el-tag>
        </div>
      </template>
      <div class="progress-wrapper">
        <el-progress 
          :percentage="progress" 
          :stroke-width="20" 
          :color="progressColors"
          striped
          striped-flow
        />
        <div class="task-details mt-4">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="任务ID">{{ currentTask.id }}</el-descriptions-item>
            <el-descriptions-item label="信号源">{{ currentTask.filename }}</el-descriptions-item>
            <el-descriptions-item label="滤波算法">{{ currentTask.algorithm }}</el-descriptions-item>
            <el-descriptions-item label="文件大小">{{ currentTask.size }}</el-descriptions-item>
            <el-descriptions-item label="当前状态">{{ statusText }}</el-descriptions-item>
            <el-descriptions-item label="处理速度">
              {{ currentTask.speed ? `${Number.parseFloat(String(currentTask.speed)).toFixed(2)} MB/s` : '计算中...' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </el-card>

    <!-- 4. 可视化：波形展示（支持回放）与 异常信息 -->
    <el-row :gutter="20">
      <el-col :span="16">
        <el-card shadow="never" class="viz-card h-full">
          <template #header>
            <div class="flex justify-between items-center">
              <span class="text-lg font-bold">面向客户展示：时序数据库信号回放</span>
              <div class="flex items-center gap-4">
                <el-button-group size="small">
                  <el-button type="primary" :icon="isPlaying ? 'ep:video-pause' : 'ep:video-play'" @click="togglePlayback">
                    {{ isPlaying ? '暂停' : '播放' }}
                  </el-button>
                  <el-button icon="ep:refresh" @click="resetPlayback">重置</el-button>
                </el-button-group>
                <el-slider 
                  v-model="playbackIndex" 
                  :max="totalPoints" 
                  :show-tooltip="false" 
                  class="playback-slider" 
                  @change="onPlaybackChange" 
                />
              </div>
            </div>
          </template>
          <div class="charts-container">
            <WaveformChart :data="displayRaw" title="原始声发射信号 (TDengine)" height="250px" color="#909399" />
            <div class="h-4"></div>
            <WaveformChart :data="displayFiltered" title="滤波后平滑信号" height="250px" color="#409EFF" />
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="anomaly-card h-full">
          <template #header>
            <div class="flex justify-between items-center">
              <span class="text-lg font-bold">实时异常监测告警</span>
              <el-badge :value="anomalies.length" type="danger" v-if="anomalies.length > 0" />
            </div>
          </template>
          <div class="anomaly-list">
            <el-timeline>
              <el-timeline-item
                v-for="(item, index) in anomalies"
                :key="index"
                :timestamp="formatTime(item.timestamp)"
                :type="item.severity === 'high' ? 'danger' : 'warning'"
                :hollow="index > 0"
              >
                <div class="anomaly-content" :class="{ 'new-anomaly': index === 0 }">
                  <div class="flex justify-between items-center">
                    <span class="font-bold">能量异常告警</span>
                    <el-tag size="mini" :type="item.severity === 'high' ? 'danger' : 'warning'">{{ item.severity.toUpperCase() }}</el-tag>
                  </div>
                  <div class="mt-2 text-sm">
                    能量值: <span class="text-red-400 font-mono">{{ item.energy.toFixed(2) }}</span>
                  </div>
                  <div class="text-xs text-gray-500 mt-1">
                    监测数值: {{ item.value.toFixed(4) }}
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-if="anomalies.length === 0" description="监测中，暂无异常数据" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { Icon } from '@iconify/vue'
import WaveformChart from './components/WaveformChart.vue'
import dayjs from 'dayjs'
import { useMessage } from '@/hooks/web/useMessage'
import { DetectionApi } from '@/api/monitor/detection'

// --- 状态定义 ---
const message = useMessage() // 消息弹窗
const selectedFile = ref<File | null>(null)
const processing = ref(false)
const progress = ref(0)
const isPlaying = ref(false)
const playbackIndex = ref(0)
const totalPoints = ref(0)
let socket: WebSocket | null = null

const config = reactive({
  algorithm: 'KALMAN',
  processNoise: 0.001,
  measurementNoise: 0.05
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
  algorithm: '',
  size: '',
  speed: '',
  status: ''
})

// 状态文本转换
const statusText = computed(() => {
  switch (currentTask.status) {
    case 'PROCESSING': return '处理中...'
    case 'COMPLETED': return '已完成'
    case 'FAILED': return '处理失败'
    default: return '等待开始'
  }
})

const statusTagType = computed(() => {
  switch (currentTask.status) {
    case 'PROCESSING': return 'warning'
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    default: return 'info'
  }
})

// 原始全量数据 (从 TDengine 获取)
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

const startDetection = async () => {
  if (!selectedFile.value) return
  
  processing.value = true
  progress.value = 0
  anomalies.value = []
  resetPlayback()
  
  try {
    const res = await DetectionApi.uploadAndDetect(selectedFile.value, config.algorithm)
    currentTask.id = res.id
    currentTask.algorithm = res.algorithm
    currentTask.status = res.status
    currentTask.size = res.size
    
    // 建立 WebSocket 接收实时异常
    connectWebSocket()
    
    // 轮询进度
    const timer = setInterval(async () => {
      try {
        const statusRes = await DetectionApi.getTaskStatus(currentTask.id)
        progress.value = statusRes.progress
        currentTask.status = statusRes.status
        currentTask.speed = statusRes.speed
        
        if (statusRes.status === 'COMPLETED') {
          clearInterval(timer)
          processing.value = false
          message.success('数据处理完毕，正在从时序数据库加载结果...')
          fetchTaskResults(currentTask.id)
        } else if (statusRes.status === 'FAILED') {
          clearInterval(timer)
          processing.value = false
          message.error('处理过程出现错误')
        }
      } catch (err) {
        clearInterval(timer)
        processing.value = false
      }
    }, 1000)
    
  } catch (e) {
    processing.value = false
    message.error('上传失败，请检查后端服务')
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
    message.error('获取回放数据失败')
  }
}

// WebSocket 实时通信
const connectWebSocket = () => {
  if (socket) socket.close()
  
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  // 根据后端 DetectionController 所在的模块端口决定，这里假设是 48083 (或者是网关 48080)
  const wsUrl = `${protocol}//${window.location.hostname}:48080/ws/detection`
  
  socket = new WebSocket(wsUrl)
  
  socket.onmessage = (event) => {
    const data = JSON.parse(event.data)
    if (data.anomaly) {
      anomalies.value.unshift({
        timestamp: data.timestamp,
        energy: data.energy || 0,
        value: data.originalValue || 0,
        severity: data.energy > 70 ? 'high' : 'medium'
      })
      if (anomalies.value.length > 30) anomalies.value.pop()
    }
  }
}

// 回放控制逻辑
let playbackTimer: any = null
const startPlayback = () => {
  if (playbackTimer) clearInterval(playbackTimer)
  isPlaying.value = true
  
  playbackTimer = setInterval(() => {
    if (playbackIndex.value < totalPoints.value) {
      playbackIndex.value += 2
      updateDisplayData()
    } else {
      pausePlayback()
    }
  }, 50)
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
  const windowSize = 100 
  const end = playbackIndex.value
  const start = Math.max(0, end - windowSize)
  
  displayRaw.value = allRawData.value.slice(start, end)
  displayFiltered.value = allFilteredData.value.slice(start, end)
}

const formatTime = (ts: number) => dayjs(ts).format('HH:mm:ss.SSS')

onUnmounted(() => {
  if (playbackTimer) clearInterval(playbackTimer)
  if (socket) socket.close()
})
</script>

<style scoped>
.detection-container {
  padding: 24px;
  background: #f5f7fa;
  min-height: calc(100vh - 84px);
  color: #303133;
  max-width: 1400px;
  margin: 0 auto;
}

:deep(.el-card) {
  background: #ffffff !important;
  border: 1px solid #ebeef5 !important;
  color: #303133 !important;
  border-radius: 10px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

:deep(.el-card__header) {
  border-bottom: 1px solid #ebeef5 !important;
  color: #303133;
}

/* 上传区 */
.tdms-uploader :deep(.el-upload-dragger) {
  background-color: #fafafa;
  border: 2px dashed #dcdfe6;
}
.tdms-uploader :deep(.el-upload-dragger:hover) {
  border-color: #409eff;
}
.upload-icon {
  font-size: 64px;
  color: #909399;
  margin-bottom: 16px;
}

/* 按钮样式 */
.start-btn {
  padding: 12px 40px;
  font-size: 16px;
}

/* 进度条 */
.progress-wrapper {
  padding: 20px 0;
}
:deep(.el-progress-bar__outer) {
  background-color: #ebeef5 !important;
}

/* 回放控制 */
.playback-slider {
  width: 300px;
}

@media (max-width: 768px) {
  .playback-slider {
    width: 200px;
  }
}

/* 异常列表 */
.anomaly-list {
  height: 520px;
  overflow-y: auto;
  padding: 10px;
}
.anomaly-content {
  background: rgba(245, 108, 108, 0.06);
  padding: 12px;
  border-radius: 6px;
  border-left: 4px solid #f56c6c;
  transition: all 0.3s;
}
.new-anomaly {
  background: rgba(245, 108, 108, 0.12);
  box-shadow: 0 0 10px rgba(245, 108, 108, 0.18);
}

/* 通用工具类 */
.w-full { width: 100%; }
.gap-4 { gap: 1rem; }
.mb-4 { margin-bottom: 1rem; }
.mt-4 { margin-top: 1rem; }
.mr-1 { margin-right: 0.25rem; }
.h-full { height: 100%; }

:deep(.el-form-item__label) {
  color: #606266 !important;
  font-weight: bold;
}

:deep(.el-descriptions__label) {
  background: #fafafa !important;
  color: #606266 !important;
}
:deep(.el-descriptions__content) {
  background: #ffffff !important;
  color: #303133 !important;
}
</style>


