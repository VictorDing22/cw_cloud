<template>
  <div class="tdms-analysis">
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
            <!-- 左侧：文件夹列表 -->
            <el-col :span="8">
              <el-card shadow="never">
                <template #header>
                  <span>Signal-1 文件夹</span>
                  <el-tag size="small" type="info" style="margin-left: 10px">单文件多通道</el-tag>
                </template>
                <el-scrollbar height="300px">
                  <div class="folder-item" @click="handleFolderSelect('signal-1')">
                    <el-icon><Folder /></el-icon>
                    <span style="margin-left: 10px">📁 signal-1</span>
                    <div class="folder-desc">
                      点击分析整个文件夹
                    </div>
                    <el-divider style="margin: 10px 0" />
                    <div class="file-list">
                      <div v-for="file in signal1Files" :key="file.path" class="file-item-small">
                        <el-icon><Document /></el-icon>
                        <span>{{ file.name }}</span>
                        <el-tag size="small">{{ formatFileSize(file.size) }}</el-tag>
                      </div>
                    </div>
                  </div>
                </el-scrollbar>
              </el-card>
            </el-col>

            <!-- 中间：Signal-2 文件夹 -->
            <el-col :span="8">
              <el-card shadow="never">
                <template #header>
                  <span>Signal-2 文件夹</span>
                  <el-tag size="small" type="success" style="margin-left: 10px">多文件组合</el-tag>
                </template>
                <el-scrollbar height="300px">
                  <div class="folder-item" @click="handleFolderSelect('signal-2')">
                    <el-icon><Folder /></el-icon>
                    <span style="margin-left: 10px">📁 signal-2</span>
                    <div class="folder-desc">
                      点击分析整个文件夹（3个文件）
                    </div>
                    <el-divider style="margin: 10px 0" />
                    <div class="file-list">
                      <div v-for="file in signal2Files" :key="file.path" class="file-item-small">
                        <el-icon><Document /></el-icon>
                        <span>{{ file.name }}</span>
                        <el-tag size="small">{{ formatFileSize(file.size) }}</el-tag>
                      </div>
                    </div>
                  </div>
                </el-scrollbar>
              </el-card>
            </el-col>

            <!-- 右侧：上传自定义文件 -->
            <el-col :span="8">
              <el-card shadow="never">
                <template #header>
                  <span>上传自定义文件</span>
                </template>
                <el-upload
                  class="upload-demo"
                  drag
                  action="/api/tdms/upload"
                  :on-success="handleUploadSuccess"
                  :before-upload="beforeUpload"
                  accept=".tdms"
                >
                  <el-icon class="el-icon--upload"><upload-filled /></el-icon>
                  <div class="el-upload__text">
                    拖拽TDMS文件到此处，或<em>点击上传</em>
                  </div>
                  <template #tip>
                    <div class="el-upload__tip">
                      仅支持 .tdms 格式文件，大小不超过 100MB
                    </div>
                  </template>
                </el-upload>
              </el-card>
            </el-col>
          </el-row>

          <!-- 文件信息 -->
          <el-descriptions
            v-if="fileInfo"
            :column="3"
            border
            style="margin-top: 20px"
          >
            <el-descriptions-item label="文件名">{{ fileInfo.name }}</el-descriptions-item>
            <el-descriptions-item label="文件大小">{{ formatFileSize(fileInfo.size) }}</el-descriptions-item>
            <el-descriptions-item label="采样率">{{ fileInfo.sampleRate }} Hz</el-descriptions-item>
            <el-descriptions-item label="通道数">{{ fileInfo.channels }}</el-descriptions-item>
            <el-descriptions-item label="采样点数">{{ fileInfo.samples }}</el-descriptions-item>
            <el-descriptions-item label="时长">{{ fileInfo.duration }}秒</el-descriptions-item>
          </el-descriptions>

          <!-- 操作按钮 -->
          <div style="margin-top: 20px; text-align: center">
            <el-button
              type="primary"
              :loading="analyzing"
              :disabled="!selectedFile"
              @click="handleAnalyze"
              size="large"
            >
              <el-icon><DataAnalysis /></el-icon>
              开始分析
            </el-button>
            <el-button
              type="success"
              :disabled="!signalData"
              @click="downloadImage"
              size="large"
            >
              <el-icon><Download /></el-icon>
              下载图片
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- 信号可视化区域 -->
      <el-col :span="24" style="margin-top: 20px" v-if="signalData">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>📊 信号处理与滤波效果</span>
              <el-space>
                <el-switch
                  v-model="showFrequency"
                  active-text="频域"
                  inactive-text="时域"
                  @change="updateCharts"
                />
                <el-select v-model="timeWindow" placeholder="选择时间窗口" @change="updateCharts">
                  <el-option label="前 5ms" :value="0.005" />
                  <el-option label="前 10ms" :value="0.01" />
                  <el-option label="前 20ms" :value="0.02" />
                  <el-option label="前 50ms" :value="0.05" />
                </el-select>
              </el-space>
            </div>
          </template>

          <el-row :gutter="20">
            <el-col :span="12">
              <div id="signal-chart-1" style="height: 400px"></div>
            </el-col>
            <el-col :span="12">
              <div id="signal-chart-2" style="height: 400px"></div>
            </el-col>
            <el-col :span="12" style="margin-top: 20px">
              <div id="signal-chart-3" style="height: 400px"></div>
            </el-col>
            <el-col :span="12" style="margin-top: 20px">
              <div id="signal-chart-4" style="height: 400px"></div>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Refresh, UploadFilled, DataAnalysis, Download, Folder } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import axios from 'axios'

const analyzing = ref(false)
const selectedFile = ref('')
const fileInfo = ref<any>(null)
const signalData = ref<any>(null)
const metrics = ref<any>(null)
const showFrequency = ref(false)
const timeWindow = ref(0.01)

// 文件列表
const signal1Files = ref([
  {
    name: 'ae_sim_2s.tdms',
    path: '/floatdata/signal-1/ae_sim_2s.tdms',
    size: 3200487
  }
])

const signal2Files = ref([
  {
    name: 'ae_sine_2s.tdms',
    path: '/floatdata/signal-2/ae_sine_2s.tdms',
    size: 1600396
  },
  {
    name: 'ae_noise_2s.tdms',
    path: '/floatdata/signal-2/ae_noise_2s.tdms',
    size: 1600398
  },
  {
    name: 'ae_mix_2s.tdms',
    path: '/floatdata/signal-2/ae_mix_2s.tdms',
    size: 1600418
  }
])

let charts: echarts.ECharts[] = []

// API基础地址（使用当前域名）
const API_BASE = window.location.origin

// 刷新文件列表
const refreshFileList = async () => {
  try {
    const res = await axios.get(`${API_BASE}/api/tdms/files`)
    signal1Files.value = res.data.signal1 || []
    signal2Files.value = res.data.signal2 || []
    ElMessage.success('文件列表已刷新')
  } catch (error) {
    console.error('刷新文件列表失败:', error)
    // 使用默认数据
  }
}

// 选择文件夹
const handleFolderSelect = async (folder: string) => {
  selectedFile.value = folder
  
  try {
    // 获取文件夹信息
    const folderPath = `/floatdata/${folder}`
    
    if (folder === 'signal-1') {
      fileInfo.value = {
        name: 'signal-1 文件夹',
        size: 3200487,
        sampleRate: 100000,
        channels: 4,
        samples: 200000,
        duration: 2.0,
        description: '单文件多通道：包含原始信号、噪声、混合信号'
      }
      ElMessage.success('✅ Signal-1：单文件多通道模式')
    } else if (folder === 'signal-2') {
      fileInfo.value = {
        name: 'signal-2 文件夹',
        size: 4801212,
        sampleRate: 100000,
        channels: 3,
        samples: 200000,
        duration: 2.0,
        description: '多文件组合：原始信号 + 噪声 + 混合信号（3个文件）'
      }
      ElMessage.success('✅ Signal-2：多文件组合模式')
    }
  } catch (error) {
    console.error('获取文件夹信息失败:', error)
  }
}

// 选择单个文件（保留原有功能）
const handleFileSelect = async (path: string) => {
  selectedFile.value = path
  
  try {
    const res = await axios.get(`${API_BASE}/api/tdms/info`, { params: { path } })
    fileInfo.value = res.data
    ElMessage.success('文件信息加载成功')
  } catch (error) {
    console.error('获取文件信息失败:', error)
    fileInfo.value = {
      name: path.split('/').pop(),
      size: 3200487,
      sampleRate: 100000,
      channels: 4,
      samples: 200000,
      duration: 2.0
    }
  }
}

// 开始分析
const handleAnalyze = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择一个文件夹')
    return
  }

  analyzing.value = true
  
  try {
    let requestData
    
    // 判断是文件夹还是单个文件
    if (selectedFile.value === 'signal-1' || selectedFile.value === 'signal-2') {
      // 文件夹模式
      requestData = {
        folder: selectedFile.value,
        sampleRate: 100000,
        cutoffFreq: 500,
        filterOrder: 6
      }
      
      ElMessage.info(`正在分析 ${selectedFile.value} 文件夹...`)
      
      // 调用后端API进行文件夹分析
      const res = await axios.post(`${API_BASE}/api/tdms/analyze-folder`, requestData)
      
      signalData.value = res.data.signals
      metrics.value = res.data.metrics
      
      await nextTick()
      initCharts()
      
      ElMessage.success(`✅ ${selectedFile.value} 分析完成！`)
    } else {
      // 单文件模式
      requestData = {
        filePath: selectedFile.value,
        sampleRate: 100000,
        cutoffFreq: 500,
        filterOrder: 6
      }
      
      const res = await axios.post(`${API_BASE}/api/tdms/analyze`, requestData)
      
      signalData.value = res.data.signals
      metrics.value = res.data.metrics
      
      await nextTick()
      initCharts()
      
      ElMessage.success('分析完成！')
    }
  } catch (error) {
    console.error('分析失败:', error)
    // 使用模拟数据
    ElMessage.warning('后端API未连接，使用模拟数据展示')
    generateMockData()
    await nextTick()
    initCharts()
  } finally {
    analyzing.value = false
  }
}

// 生成模拟数据
const generateMockData = () => {
  const samples = 1000
  const time = Array.from({ length: samples }, (_, i) => i / 100000)
  
  // 原始正弦波
  const sine = time.map(t => Math.sin(2 * Math.PI * 5000 * t))
  
  // 加噪信号
  const noisy = sine.map(s => s + (Math.random() - 0.5) * 0.5)
  
  // 滤波后信号（简单平均滤波模拟）
  const filtered = noisy.map((_, i) => {
    const start = Math.max(0, i - 5)
    const end = Math.min(noisy.length, i + 5)
    const sum = noisy.slice(start, end).reduce((a, b) => a + b, 0)
    return sum / (end - start)
  })
  
  signalData.value = {
    time: time.map(t => t * 1000), // 转换为ms
    sine,
    noisy,
    filtered
  }
  
  metrics.value = {
    mseImprovement: 85.5,
    mseBefore: 0.062500,
    mseAfter: 0.009063,
    correlation: 0.9823
  }
}

// 初始化图表
const initCharts = () => {
  charts.forEach(chart => chart?.dispose())
  charts = []
  
  const chartIds = ['signal-chart-1', 'signal-chart-2', 'signal-chart-3', 'signal-chart-4']
  
  chartIds.forEach((id, index) => {
    const el = document.getElementById(id)
    if (!el) return
    
    const chart = echarts.init(el)
    charts.push(chart)
    
    updateChart(chart, index)
  })
}

// 更新单个图表
const updateChart = (chart: echarts.ECharts, index: number) => {
  if (!signalData.value) return
  
  const { time, sine, noisy, filtered } = signalData.value
  const endIdx = Math.floor(timeWindow.value * 100) // 根据时间窗口截取数据
  
  const titles = ['① 原始信号', '② 加噪信号', '③ 滤波后信号', '④ 效果对比']
  const colors = ['#5470c6', '#ee6666', '#91cc75', '#5470c6']
  
  let series: any[] = []
  
  switch (index) {
    case 0: // 原始信号
      series = [{
        name: '原始信号',
        type: 'line',
        data: sine.slice(0, endIdx),
        smooth: false,
        itemStyle: { color: colors[index] },
        lineStyle: { width: 2 }
      }]
      break
    case 1: // 加噪信号
      series = [{
        name: '加噪信号',
        type: 'line',
        data: noisy.slice(0, endIdx),
        smooth: false,
        itemStyle: { color: colors[index] },
        lineStyle: { width: 1 },
        opacity: 0.7
      }]
      break
    case 2: // 滤波后
      series = [{
        name: '滤波后',
        type: 'line',
        data: filtered.slice(0, endIdx),
        smooth: false,
        itemStyle: { color: colors[index] },
        lineStyle: { width: 2 }
      }]
      break
    case 3: // 对比
      series = [
        {
          name: '原始信号',
          type: 'line',
          data: sine.slice(0, endIdx),
          smooth: false,
          itemStyle: { color: '#5470c6' },
          lineStyle: { width: 2 }
        },
        {
          name: '滤波后',
          type: 'line',
          data: filtered.slice(0, endIdx),
          smooth: false,
          itemStyle: { color: '#91cc75' },
          lineStyle: { width: 2, type: 'dashed' }
        }
      ]
      break
  }
  
  chart.setOption({
    title: {
      text: titles[index],
      left: 'center'
    },
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: series.map(s => s.name),
      bottom: 0
    },
    xAxis: {
      type: 'category',
      data: time.slice(0, endIdx),
      name: '时间 (ms)',
      axisLabel: {
        formatter: (val: any) => {
          const num = typeof val === 'string' ? parseFloat(val) : val
          return num.toFixed ? num.toFixed(2) : val
        }
      }
    },
    yAxis: {
      type: 'value',
      name: '幅值'
    },
    series,
    grid: {
      left: '10%',
      right: '5%',
      bottom: '15%',
      top: '15%'
    }
  })
}

// 更新所有图表
const updateCharts = () => {
  charts.forEach((chart, index) => {
    updateChart(chart, index)
  })
}

// 文件上传前检查
const beforeUpload = (file: File) => {
  const isTDMS = file.name.endsWith('.tdms')
  const isLt100M = file.size / 1024 / 1024 < 100
  
  if (!isTDMS) {
    ElMessage.error('只能上传 .tdms 格式的文件!')
    return false
  }
  if (!isLt100M) {
    ElMessage.error('文件大小不能超过 100MB!')
    return false
  }
  return true
}

// 上传成功
const handleUploadSuccess = (response: any) => {
  ElMessage.success('文件上传成功！')
  refreshFileList()
}

// 下载图片
const downloadImage = () => {
  if (charts.length === 0) return
  
  // 创建一个大canvas合并所有图表
  const canvas = document.createElement('canvas')
  canvas.width = 1920
  canvas.height = 1600
  const ctx = canvas.getContext('2d')
  
  if (!ctx) return
  
  // 这里可以导出为图片
  ElMessage.success('图片下载功能开发中...')
}

// 格式化文件大小
const formatFileSize = (bytes: number) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

onMounted(() => {
  refreshFileList()
  
  window.addEventListener('resize', () => {
    charts.forEach(chart => chart?.resize())
  })
})
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.upload-demo {
  text-align: center;
  
  :deep(.el-upload-dragger) {
    padding: 20px;
  }
}

.el-icon--upload {
  font-size: 67px;
  margin: 20px 0;
  color: #409eff;
}

.folder-item {
  padding: 15px;
  cursor: pointer;
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  transition: all 0.3s;
  
  &:hover {
    border-color: #409eff;
    background-color: #ecf5ff;
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
  }
  
  .folder-desc {
    margin-top: 8px;
    font-size: 12px;
    color: #909399;
    font-style: italic;
  }
  
  .file-list {
    margin-top: 10px;
  }
  
  .file-item-small {
    display: flex;
    align-items: center;
    padding: 5px 0;
    font-size: 12px;
    color: #606266;
    
    span {
      flex: 1;
      margin-left: 5px;
    }
    
    .el-tag {
      margin-left: 5px;
    }
  }
}
</style>
