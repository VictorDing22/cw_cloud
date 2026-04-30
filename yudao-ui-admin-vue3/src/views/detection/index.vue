<template>
  <div class="detection-container">
    <el-tabs v-model="activeTab" type="border-card" class="detection-tabs">

      <!-- ==================== Tab 1: 文件检测 ==================== -->
      <el-tab-pane label="首页" name="file">
        <el-row :gutter="20" class="mb-4">
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="upload-card">
              <template #header>
                <div class="flex items-center justify-between">
                  <span class="text-lg font-bold">上传 TDMS 文件</span>
                  <el-tag v-if="currentTask.filename" type="info" size="small">{{ currentTask.filename }}</el-tag>
                </div>
              </template>
              <el-upload class="tdms-uploader" drag action="#" :auto-upload="false" :on-change="handleFileChange" accept=".tdms">
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
              <template #header><span class="text-lg font-bold">参数配置</span></template>
              <el-form :model="config" label-position="top" class="config-form">
                <el-row :gutter="20">
                  <el-col :span="8">
                    <el-form-item label="滤波算法">
                      <el-select v-model="config.algorithm" placeholder="选择算法" class="w-full">
                        <el-option v-for="algo in algorithms" :key="algo.id" :label="`${algo.id} - ${algo.name}`" :value="algo.id">
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
                  <el-button type="primary" size="large" :loading="processing" @click="startDetection" :disabled="!selectedFile" class="start-btn">
                    <Icon icon="ep:video-play" class="mr-1" />
                    {{ processing ? '正在处理数据...' : '触发检测流程' }}
                  </el-button>
                </div>
              </el-form>
            </el-card>
          </el-col>
        </el-row>

        <el-card shadow="never" class="mb-4 progress-card" v-if="currentTask.id">
          <template #header>
            <div class="flex justify-between items-center">
              <span class="text-lg font-bold">处理进度</span>
              <el-tag :type="statusTagType" size="small">{{ statusText }}</el-tag>
            </div>
          </template>
          <div class="progress-wrapper">
            <el-progress :percentage="progress" :stroke-width="20" :color="progressColors" striped striped-flow />
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

        <el-card shadow="never" class="viz-card">
          <template #header>
            <div class="flex justify-between items-center">
              <span class="text-lg font-bold">时序数据库信号回放</span>
              <div class="flex items-center gap-4">
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
          <AnomalyAlertBar :anomalies="anomalies" />
          <div class="charts-container">
            <AdvancedWaveformChart :original-data="displayRaw" :filtered-data="displayFiltered" :start-index="displayStartIndex"
              :accumulated-original-data="accumulatedRaw" :accumulated-filtered-data="accumulatedFiltered" />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- ==================== Tab 2: 信息源检测（实时流水线） ==================== -->
      <el-tab-pane label="信息源检测" name="realtime">
        <el-card shadow="never" class="mb-4">
          <template #header>
            <div class="flex justify-between items-center">
              <span class="text-lg font-bold">Flink 实时流水线监控</span>
              <div class="flex items-center gap-3">
                <el-tag :type="rtConnected ? 'success' : 'danger'" size="small" effect="dark">
                  {{ rtConnected ? '已连接' : '未连接' }}
                </el-tag>
                <el-select v-model="rtSelectedDevice" placeholder="选择设备" size="small" style="width: 180px" clearable>
                  <el-option v-for="d in rtDevices" :key="d" :label="d" :value="d" />
                </el-select>
                <el-select v-model="rtSelectedChannel" placeholder="通道" size="small" style="width: 100px" clearable>
                  <el-option :label="'全部'" :value="''" />
                  <el-option v-for="c in [1,2,3]" :key="c" :label="'CH' + c" :value="c" />
                </el-select>
                <el-button v-if="!rtConnected" type="primary" size="small" @click="connectRealtime">连接</el-button>
                <el-button v-else type="danger" size="small" @click="disconnectRealtime">断开</el-button>
              </div>
            </div>
          </template>

          <!-- 实时异常监测告警 -->
          <AnomalyAlertBar :anomalies="rtAnomalies" />

          <!-- 实时波形图 -->
          <div class="charts-container">
            <AdvancedWaveformChart :original-data="rtDisplayRaw" :filtered-data="rtDisplayFiltered" :start-index="0"
              :accumulated-original-data="rtAccRaw" :accumulated-filtered-data="rtAccFiltered" />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed, watch } from 'vue'
import { Icon } from '@iconify/vue'
import AdvancedWaveformChart from './components/AdvancedWaveformChart.vue'
import AnomalyAlertBar from './components/AnomalyAlertBar.vue'
import dayjs from 'dayjs'
import { useMessage } from '@/hooks/web/useMessage'
import { DetectionApi } from '@/api/monitor/detection'

const message = useMessage()
const activeTab = ref('realtime')

// ===================== Tab 1: 文件检测（原逻辑保留） =====================
const selectedFile = ref<File | null>(null)
const processing = ref(false)
const progress = ref(0)
const isPlaying = ref(false)
const playbackIndex = ref(0)
const totalPoints = ref(0)
let fileSocket: WebSocket | null = null

const config = reactive({ algorithm: 'KALMAN', processNoise: 0.001, measurementNoise: 0.05 })
const algorithms = [
  { id: 'KALMAN', name: '卡尔曼滤波', desc: '最优估计' },
  { id: 'RLS', name: 'RLS递推', desc: '高精度' },
  { id: 'BUTTERWORTH', name: '巴特沃斯', desc: '频率选择' },
  { id: 'FIR', name: 'FIR滤波', desc: '线性相位' },
  { id: 'WAVELET', name: '小波滤波', desc: '多尺度' },
]

const currentTask = reactive({ id: '', filename: '', algorithm: '', size: '', speed: '', status: '' })
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

const allRawData = ref<any[]>([])
const allFilteredData = ref<any[]>([])
const displayRaw = ref<any[]>([])
const displayFiltered = ref<any[]>([])
const displayStartIndex = ref(0)
const accumulatedRaw = ref<any[]>([])
const accumulatedFiltered = ref<any[]>([])
const anomalies = ref<any[]>([])
let lastAccumulateUpdateMs = 0

const progressColors = [
  { color: '#f56c6c', percentage: 20 }, { color: '#e6a23c', percentage: 40 },
  { color: '#5cb87a', percentage: 60 }, { color: '#1989fa', percentage: 80 },
  { color: '#6f7ad3', percentage: 100 },
]

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
    Object.assign(currentTask, { id: res.id, algorithm: res.algorithm, status: res.status, size: res.size })
    const timer = setInterval(async () => {
      try {
        const s = await DetectionApi.getTaskStatus(currentTask.id)
        progress.value = s.progress; currentTask.status = s.status; currentTask.speed = s.speed
        if (s.status === 'COMPLETED') { clearInterval(timer); processing.value = false; message.success('处理完毕'); fetchTaskResults(currentTask.id) }
        else if (s.status === 'FAILED') { clearInterval(timer); processing.value = false; message.error('处理失败') }
      } catch { clearInterval(timer); processing.value = false }
    }, 1000)
  } catch { processing.value = false; message.error('上传失败') }
}

const fetchTaskResults = async (taskId: string) => {
  try {
    const data = await DetectionApi.getTaskResults(taskId)
    const raw: any[] = [], filtered: any[] = []
    data.forEach((r: any) => { raw.push([r.timestamp, r.originalValue]); filtered.push([r.timestamp, r.filteredValue]) })
    allRawData.value = raw; allFilteredData.value = filtered
    totalPoints.value = data.length; playbackIndex.value = 0; startPlayback()
  } catch { message.error('获取回放数据失败') }
}

let playbackTimer: any = null
const startPlayback = () => { if (playbackTimer) clearInterval(playbackTimer); isPlaying.value = true; playbackTimer = setInterval(() => { if (playbackIndex.value < totalPoints.value) { playbackIndex.value += 2; updateDisplayData() } else pausePlayback() }, 50) }
const pausePlayback = () => { isPlaying.value = false; if (playbackTimer) clearInterval(playbackTimer) }
const togglePlayback = () => { isPlaying.value ? pausePlayback() : startPlayback() }
const resetPlayback = () => { playbackIndex.value = 0; updateDisplayData(); pausePlayback() }
const onPlaybackChange = () => updateDisplayData()
const updateDisplayData = () => {
  const windowSize = 1000, end = playbackIndex.value, start = Math.max(0, end - windowSize)
  displayRaw.value = allRawData.value.slice(start, end)
  displayFiltered.value = allFilteredData.value.slice(start, end)
  displayStartIndex.value = start
  const now = Date.now()
  if (now - lastAccumulateUpdateMs > 200 || end >= totalPoints.value) {
    accumulatedRaw.value = allRawData.value.slice(0, end)
    accumulatedFiltered.value = allFilteredData.value.slice(0, end)
    lastAccumulateUpdateMs = now
  }
}
const formatTime = (ts: number) => dayjs(ts).format('HH:mm:ss.SSS')

// ===================== Tab 2: 实时流水线监控 =====================
const rtConnected = ref(false)
let rtSocket: WebSocket | null = null
const rtSelectedDevice = ref('')
const rtSelectedChannel = ref<number | string>('')
const rtDevices = ref<string[]>([])
const rtAnomalies = ref<any[]>([])

const RT_WINDOW = 500
const rtRawBuffer = ref<number[]>([])
const rtFilteredBuffer = ref<number[]>([])
const rtDisplayRaw = ref<any[]>([])
const rtDisplayFiltered = ref<any[]>([])
const rtAccRaw = ref<any[]>([])
const rtAccFiltered = ref<any[]>([])
let rtGlobalIdx = 0

const connectRealtime = () => {
  if (rtSocket) rtSocket.close()
  const wsUrl = `ws://${window.location.hostname}:8083`
  rtSocket = new WebSocket(wsUrl)
  rtSocket.onopen = () => { rtConnected.value = true; message.success('已连接实时流水线') }
  rtSocket.onclose = () => { rtConnected.value = false }
  rtSocket.onerror = () => { rtConnected.value = false; message.error('WebSocket 连接失败') }
  rtSocket.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'welcome') return

      if (data.type === 'anomaly-alert') {
        rtAnomalies.value.unshift(data)
        if (rtAnomalies.value.length > 30) rtAnomalies.value.pop()
        if (data.deviceId && !rtDevices.value.includes(data.deviceId)) rtDevices.value.push(data.deviceId)
        return
      }

      if (data.deviceId && !rtDevices.value.includes(data.deviceId)) rtDevices.value.push(data.deviceId)

      if (rtSelectedDevice.value && data.deviceId !== rtSelectedDevice.value) return
      if (rtSelectedChannel.value && data.channelId !== rtSelectedChannel.value) return

      const samples: number[] = data.samples || []
      if (samples.length === 0) return

      if (data.type === 'raw-signal') {
        rtRawBuffer.value.push(...samples)
        if (rtRawBuffer.value.length > 5000) rtRawBuffer.value = rtRawBuffer.value.slice(-5000)
      } else if (data.type === 'filtered-signal') {
        rtFilteredBuffer.value.push(...samples)
        if (rtFilteredBuffer.value.length > 5000) rtFilteredBuffer.value = rtFilteredBuffer.value.slice(-5000)
      }

      updateRealtimeDisplay()
    } catch {}
  }
}

const updateRealtimeDisplay = () => {
  const rawSlice = rtRawBuffer.value.slice(-RT_WINDOW)
  const filtSlice = rtFilteredBuffer.value.slice(-RT_WINDOW)
  rtDisplayRaw.value = rawSlice.map((v, i) => [i, v])
  rtDisplayFiltered.value = filtSlice.map((v, i) => [i, v])
  rtAccRaw.value = rtRawBuffer.value.map((v, i) => [i, v])
  rtAccFiltered.value = rtFilteredBuffer.value.map((v, i) => [i, v])
}

const disconnectRealtime = () => {
  if (rtSocket) { rtSocket.close(); rtSocket = null }
  rtConnected.value = false
}

watch(activeTab, (val) => {
  if (val === 'realtime' && !rtConnected.value) connectRealtime()
})

onMounted(() => {
  if (activeTab.value === 'realtime') connectRealtime()
})

onUnmounted(() => {
  if (playbackTimer) clearInterval(playbackTimer)
  if (fileSocket) fileSocket.close()
  disconnectRealtime()
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

.detection-tabs {
  border-radius: 10px;
  overflow: hidden;
}

:deep(.el-tabs__content) {
  padding: 16px;
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

.tdms-uploader :deep(.el-upload-dragger) { background-color: #fafafa; border: 2px dashed #dcdfe6; }
.tdms-uploader :deep(.el-upload-dragger:hover) { border-color: #409eff; }
.upload-icon { font-size: 64px; color: #909399; margin-bottom: 16px; }
.start-btn { padding: 12px 40px; font-size: 16px; }
.progress-wrapper { padding: 20px 0; }
:deep(.el-progress-bar__outer) { background-color: #ebeef5 !important; }
.playback-slider { width: 300px; }

@media (max-width: 768px) { .playback-slider { width: 200px; } }

.w-full { width: 100%; }
.gap-4 { gap: 1rem; }
.gap-3 { gap: 0.75rem; }
.mb-4 { margin-bottom: 1rem; }
.mt-4 { margin-top: 1rem; }
.mr-1 { margin-right: 0.25rem; }

:deep(.el-form-item__label) { color: #606266 !important; font-weight: bold; }
:deep(.el-descriptions__label) { background: #fafafa !important; color: #606266 !important; }
:deep(.el-descriptions__content) { background: #ffffff !important; color: #303133 !important; }
</style>
