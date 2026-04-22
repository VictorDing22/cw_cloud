<template>
  <ContentWrap title="TDMS信号文件分析">
    <div class="upload-panels">
      <el-card class="upload-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>Signal-1 文件夹</span>
            <span class="sub-title">（单文件或多文件组合）</span>
          </div>
        </template>
        <el-upload
          drag
          multiple
          :http-request="(opt) => handleCustomUpload(opt, 'signal1')"
          :show-file-list="false"
          accept=".tdms"
        >
          <el-icon class="upload-icon"><upload-filled /></el-icon>
          <div class="el-upload__text">
            将 TDMS 文件或文件夹拖拽到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">仅支持 .tdms 文件，单个文件 ≤ 100MB</div>
          </template>
        </el-upload>
        <el-scrollbar v-if="signal1Files.length" class="file-list">
          <div v-for="f in signal1Files" :key="f.uid" class="file-item">
            <span class="name">{{ f.name }}</span>
            <span class="size">{{ formatSize(f.size) }}</span>
          </div>
        </el-scrollbar>
      </el-card>

      <el-card class="upload-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>Signal-2 文件夹</span>
            <span class="sub-title">（多文件组合对比）</span>
          </div>
        </template>
        <el-upload
          drag
          multiple
          :http-request="(opt) => handleCustomUpload(opt, 'signal2')"
          :show-file-list="false"
          accept=".tdms"
        >
          <el-icon class="upload-icon"><upload-filled /></el-icon>
          <div class="el-upload__text">
            将 TDMS 文件拖拽到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">可选择多个文件作为一组进行对比分析</div>
          </template>
        </el-upload>
        <el-scrollbar v-if="signal2Files.length" class="file-list">
          <div v-for="f in signal2Files" :key="f.uid" class="file-item">
            <span class="name">{{ f.name }}</span>
            <span class="size">{{ formatSize(f.size) }}</span>
          </div>
        </el-scrollbar>
      </el-card>

      <el-card class="upload-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>上传自定义文件</span>
          </div>
        </template>
        <el-upload
          drag
          :http-request="(opt) => handleCustomUpload(opt, 'single')"
          :show-file-list="false"
          accept=".tdms"
        >
          <el-icon class="upload-icon"><upload-filled /></el-icon>
          <div class="el-upload__text">
            拖拽 TDMS 文件到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">仅支持 .tdms 格式，大小不超过 100MB</div>
          </template>
        </el-upload>
        <div v-if="singleFile" class="file-item">
          <span class="name">{{ singleFile.name }}</span>
          <span class="size">{{ formatSize(singleFile.size) }}</span>
        </div>
      </el-card>
    </div>

    <!-- 配置面板 -->
    <el-card class="config-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>分析配置</span>
        </div>
      </template>
      <div class="config-content">
        <div class="config-row">
          <div class="config-item">
            <span class="config-label">异常检测算法：</span>
            <el-select v-model="anomalyAlgorithm" size="default" style="width: 280px" placeholder="选择异常检测算法">
              <el-option label="3σ 动态阈值法（推荐）" value="3sigma">
                <div class="algorithm-option">
                  <span class="algorithm-name">3σ 动态阈值法</span>
                  <el-tag type="success" size="small" effect="plain">推荐</el-tag>
                </div>
              </el-option>
              <el-option label="分位数阈值法" value="quantile">
                <div class="algorithm-option">
                  <span class="algorithm-name">分位数阈值法</span>
                </div>
              </el-option>
            </el-select>
            <el-tooltip content="3σ动态阈值法：基于统计学的三倍标准差方法，自动计算动态阈值" placement="top">
              <el-icon class="info-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
          <div class="config-item" v-if="anomalyAlgorithm === 'quantile'">
            <span class="config-label">异常阈值系数：</span>
            <el-slider
              v-model="thresholdFactor"
              :min="0.5"
              :max="2"
              :step="0.05"
              show-input
              style="width: 260px"
            />
            <span class="threshold-text">当前：{{ thresholdFactor.toFixed(2) }} × 95% 分位数</span>
          </div>
          <div class="config-item" v-if="anomalyAlgorithm === '3sigma'">
            <span class="config-label">标准差倍数：</span>
            <el-slider
              v-model="sigmaMultiplier"
              :min="2"
              :max="4"
              :step="0.1"
              show-input
              style="width: 260px"
            />
            <span class="threshold-text">当前：{{ sigmaMultiplier.toFixed(1) }}σ</span>
          </div>
        </div>
        <el-divider />
        <div class="config-row">
          <div class="config-item">
            <span class="config-label">滤波器：</span>
            <el-select v-model="filterType" size="default" style="width: 220px">
              <el-option label="卡尔曼滤波 (Kalman)" value="KALMAN" />
              <el-option label="LMS 自适应滤波" value="LMS" />
            </el-select>
          </div>
          <template v-if="filterType === 'KALMAN'">
            <div class="config-item">
              <span class="config-label">Q：</span>
              <el-input-number v-model="kalmanParams.kalmanQ" :min="0" :step="1e-5" size="default" style="width: 120px" />
            </div>
            <div class="config-item">
              <span class="config-label">R：</span>
              <el-input-number v-model="kalmanParams.kalmanR" :min="0" :step="0.01" size="default" style="width: 120px" />
            </div>
            <div class="config-item">
              <span class="config-label">P0：</span>
              <el-input-number v-model="kalmanParams.kalmanP0" :min="0" :step="0.1" size="default" style="width: 120px" />
            </div>
            <div class="config-item">
              <span class="config-label">x0-N：</span>
              <el-input-number v-model="kalmanParams.kalmanX0N" :min="1" :max="100" :step="1" size="default" style="width: 120px" />
            </div>
          </template>
        </div>
      </div>
    </el-card>

    <!-- 操作工具栏 -->
    <div class="toolbar">
      <div class="anomaly-summary" v-if="analysisResult">
        <el-tag type="info" size="large">
          <span class="summary-label">异常点数量：</span>
          <span class="count">{{ analysisResult.anomalyCount }}</span>
        </el-tag>
      </div>
      <div class="controls">
        <el-button type="primary" size="default" :disabled="!canAnalyze || analyzing" :loading="analyzing" @click="handleAnalyze">
          <el-icon><VideoPlay /></el-icon>
          <span>开始分析</span>
        </el-button>
        <el-button type="danger" size="default" :disabled="!analysisResult || analyzing" @click="handleDetectAnomaly">
          <el-icon><Warning /></el-icon>
          <span>重新检测异常</span>
        </el-button>
        <el-button type="success" size="default" :disabled="!analysisResult" @click="downloadPng">
          <el-icon><Download /></el-icon>
          <span>下载图片</span>
        </el-button>
      </div>
    </div>

    <div class="playback-top" v-if="analysisResult && analysisResult.points.length">
      <div class="playback-main">
        <el-button
          type="primary"
          size="small"
          :icon="playing ? VideoPause : VideoPlay"
          @click="togglePlay"
        >
          {{ playing ? '暂停' : '播放' }}
        </el-button>
      </div>
    </div>

    <el-row :gutter="12" class="chart-grid" v-loading="analyzing">
      <el-col :span="12">
        <div class="chart-title">(1) 原始信号 Raw</div>
        <div ref="chartRawRef" class="chart-panel"></div>
      </el-col>
      <el-col :span="12">
        <div class="chart-title">(2) 滤波信号 Filtered</div>
        <div ref="chartFilteredRef" class="chart-panel"></div>
      </el-col>
      <el-col :span="12">
        <div class="chart-title">(3) 残差信号 Residual</div>
        <div ref="chartResidualRef" class="chart-panel"></div>
      </el-col>
      <el-col :span="12">
        <div class="chart-title">(4) 滤波效果对比</div>
        <div ref="chartCompareRef" class="chart-panel"></div>
      </el-col>
    </el-row>

    <div class="playback-bar" v-if="analysisResult && analysisResult.points.length">
      <div class="playback-controls">
        <span class="label">进度：</span>
        <el-slider
          v-model="playIndex"
          :min="0"
          :max="maxIndex"
          :step="1"
          @change="applyProgress"
          class="progress-slider"
        />
        
        <span class="label" style="margin-left: 20px">速度：</span>
        <el-slider
          v-model="playSpeed"
          :min="1"
          :max="20"
          :step="1"
          class="speed-slider"
        />
        <span class="time" style="min-width: 30px; text-align: right;">{{ playSpeed }}x</span>

        <span class="time" style="margin-left: 20px">
          {{ currentTime.toFixed(3) }} s / {{ totalTime.toFixed(3) }} s
        </span>
      </div>
    </div>
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadRequestOptions } from 'element-plus'
import { analyzeTdmsHistory, type HistoryAnalysisResult, type FilterType, type KalmanParams } from '@/api/monitor'
import { UploadFilled, VideoPlay, VideoPause, QuestionFilled, Warning, Download } from '@element-plus/icons-vue'

const signal1Files = ref<UploadFile[]>([])
const signal2Files = ref<UploadFile[]>([])
const singleFile = ref<UploadFile | null>(null)

const analyzing = ref(false)
const thresholdFactor = ref(1.5)
const analysisResult = ref<HistoryAnalysisResult | null>(null)

// 异常检测算法选择
const anomalyAlgorithm = ref<'3sigma' | 'quantile'>('3sigma')
const sigmaMultiplier = ref(3.0) // 3σ倍数，默认3.0

// 滤波器选择：默认卡尔曼；切换后会重新触发分析/异常检测
const filterType = ref<FilterType>('KALMAN')
const kalmanParams = reactive<KalmanParams>({
  kalmanQ: 1e-5,
  kalmanR: 0.1,
  kalmanP0: 1.0,
  kalmanX0N: 10
})

const chartRawRef = ref<HTMLDivElement | null>(null)
const chartFilteredRef = ref<HTMLDivElement | null>(null)
const chartResidualRef = ref<HTMLDivElement | null>(null)
const chartCompareRef = ref<HTMLDivElement | null>(null)

const chartRaw = ref<echarts.ECharts | null>(null)
const chartFiltered = ref<echarts.ECharts | null>(null)
const chartResidual = ref<echarts.ECharts | null>(null)
const chartCompare = ref<echarts.ECharts | null>(null)

const playing = ref(false)
const playIndex = ref(0)
const playHead = ref(0)
const timer = ref<number | null>(null)

// 历史分析终极方案（不动实时检测）：X 轴固定 [0, windowSize-1]，只渲染窗口内的点
// 默认播放速度为 3，窗口点数为 500
const playSpeed = ref(3)
const windowSize = 500

const canAnalyze = computed(
  () => signal1Files.value.length + signal2Files.value.length + (singleFile.value ? 1 : 0) > 0
)

const maxIndex = computed(() =>
  analysisResult.value ? Math.max(analysisResult.value.points.length - 1, 0) : 0
)
const currentTime = computed(() =>
  analysisResult.value && analysisResult.value.points.length
    ? analysisResult.value.points[playIndex.value]?.timestamp ?? 0
    : 0
)
const totalTime = computed(() =>
  analysisResult.value && analysisResult.value.points.length
    ? analysisResult.value.points[analysisResult.value.points.length - 1].timestamp
    : 0
)

const formatSize = (size?: number) => {
  if (!size && size !== 0) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

const handleCustomUpload = (options: UploadRequestOptions, group: 'signal1' | 'signal2' | 'single') => {
  const file = options.file as File & { uid?: string }
  const uploadFile: UploadFile = {
    name: file.name,
    size: file.size,
    status: 'success',
    uid: file.uid || `${Date.now()}-${Math.random()}`,
    raw: file
  }
  if (group === 'signal1') {
    signal1Files.value.push(uploadFile)
  } else if (group === 'signal2') {
    signal2Files.value.push(uploadFile)
  } else {
    singleFile.value = uploadFile
  }
  // 手动模式下必须调用 onSuccess 告知 Element Plus 上传完成
  options.onSuccess && options.onSuccess({}, file as any)
}

const buildFormData = () => {
  const form = new FormData()
  const appendFiles = (files: UploadFile[], group: string) => {
    files.forEach((f) => {
      if (f.raw instanceof File) {
        form.append('files', f.raw)
        form.append('groups', group)
      }
    })
  }
  appendFiles(signal1Files.value, 'signal1')
  appendFiles(signal2Files.value, 'signal2')
  if (singleFile.value && singleFile.value.raw instanceof File) {
    form.append('files', singleFile.value.raw)
    form.append('groups', 'single')
  }
  form.append('thresholdFactor', String(thresholdFactor.value))
  return form
}

const initCharts = () => {
  const unit = analysisResult.value?.channel.unit || 'Amplitude'

  const initOne = (
    dom: HTMLDivElement | null,
    legend: string[],
    isCompareChart = false,
    lineColor = '#409EFF', // 默认蓝色
    showAnomaly = false // 是否显示异常点
  ) => {
    if (!dom) return null
    const instance = echarts.init(dom)
    const baseSeries = isCompareChart
      ? [
          {
            name: legend[0],
            type: 'line',
            showSymbol: true,
            symbol: 'circle',
            symbolSize: 4,
            smooth: true,
            lineStyle: { width: 1.4, color: '#409EFF' },
            itemStyle: { color: '#409EFF' },
            data: []
          },
          {
            name: legend[1],
            type: 'line',
            showSymbol: true,
            symbol: 'circle',
            symbolSize: 4,
            smooth: true,
            lineStyle: { width: 1.4, color: '#67C23A' },
            itemStyle: { color: '#67C23A' },
            data: []
          }
        ]
      : [
          {
            name: legend[0],
            type: 'line',
            showSymbol: true,
            symbol: 'circle',
            symbolSize: 4,
            smooth: true,
            lineStyle: { width: 1.4, color: lineColor },
            itemStyle: { color: lineColor },
            data: []
          }
        ]
    
    // 如果需要显示异常点，添加异常点散点图
    if (showAnomaly && !isCompareChart) {
      baseSeries.push({
        name: '异常点',
        type: 'scatter',
        symbol: 'diamond',
        symbolSize: 8,
        itemStyle: { color: '#F56C6C' },
        data: []
      } as any)
    }
    
    instance.setOption({
      // 和实时检测页面尽量保持一致的"连续曲线"视觉效果
      animation: false,
      tooltip: { trigger: 'axis' },
      legend: { data: showAnomaly && !isCompareChart ? [...legend, '异常点'] : legend },
      grid: { left: 50, right: 20, top: 20, bottom: 30 },
      xAxis: {
        type: 'value', // 使用 value 类型，表示窗口内索引
        boundaryGap: false,
        name: '窗口索引',
        min: 0,
        max: Math.max(windowSize - 1, 0) // 固定窗口范围
      },
      yAxis: {
        type: 'value',
        scale: true,
        name: unit
      },
      // 历史分析波形：每个样本点以点显示，然后用平滑曲线连接
      series: baseSeries
    })
    return instance
  }

  chartRaw.value = initOne(chartRawRef.value, ['原始信号'], false, '#409EFF', true) as echarts.ECharts // 蓝色，显示异常点
  chartFiltered.value = initOne(chartFilteredRef.value, ['滤波信号'], false, '#67C23A', true) as echarts.ECharts // 绿色，显示异常点
  chartResidual.value = initOne(chartResidualRef.value, ['残差信号'], false, '#E6A23C', true) as echarts.ECharts // 橙色，显示异常点
  chartCompare.value = initOne(chartCompareRef.value, ['原始信号', '滤波信号'], true) as echarts.ECharts
}

const renderFull = () => {
  if (!analysisResult.value) return
  const pts = analysisResult.value.points
  if (!pts.length) return

  // 只渲染最近 windowSize 个点；X 轴固定为 [0, windowSize-1]
  const win = Math.max(1, Math.floor(windowSize))
  const startIdx = Math.max(0, pts.length - win)
  const windowPts = pts.slice(startIdx)

  // 生成 ECharts 需要的 [x, y] 数据：x 是窗口内索引
  const rawData = windowPts.map((p, i) => [i, p.rawValue])
  const filteredData = windowPts.map((p, i) => [i, p.filteredValue])
  const residualData = windowPts.map((p, i) => [i, p.residualValue])
  
  // 异常点数据（只显示异常点）
  const rawAnomalyData = windowPts.map((p, i) => p.isAnomaly ? [i, p.rawValue] : null).filter(Boolean) as [number, number][]
  const filteredAnomalyData = windowPts.map((p, i) => p.isAnomaly ? [i, p.filteredValue] : null).filter(Boolean) as [number, number][]
  const residualAnomalyData = windowPts.map((p, i) => p.isAnomaly ? [i, p.residualValue] : null).filter(Boolean) as [number, number][]

  const xAxisRange = { min: 0, max: Math.max(win - 1, 0) }

  chartRaw.value?.setOption({
    xAxis: xAxisRange,
    series: [{ data: rawData }, { data: rawAnomalyData }]
  })
  chartFiltered.value?.setOption({
    xAxis: xAxisRange,
    series: [{ data: filteredData }, { data: filteredAnomalyData }]
  })
  chartResidual.value?.setOption({
    xAxis: xAxisRange,
    series: [{ data: residualData }, { data: residualAnomalyData }]
  })
  chartCompare.value?.setOption({
    xAxis: xAxisRange,
    series: [{ data: rawData }, { data: filteredData }]
  })
}

const renderByIndex = (idx: number) => {
  if (!analysisResult.value) return
  const pts = analysisResult.value.points
  if (!pts.length) return

  // 索引边界保护
  const safeIdx = Math.min(Math.max(idx, 0), pts.length - 1)

  // 滑动窗口：只保留最近 windowSize 个点；X 轴固定为 [0, windowSize-1]
  const win = Math.max(1, Math.floor(windowSize))
  const startIdx = Math.max(0, safeIdx - win + 1)
  const windowPts = pts.slice(startIdx, safeIdx + 1)

  const rawData = windowPts.map((p, i) => [i, p.rawValue])
  const filteredData = windowPts.map((p, i) => [i, p.filteredValue])
  const residualData = windowPts.map((p, i) => [i, p.residualValue])
  
  // 异常点数据（只显示异常点）
  const rawAnomalyData = windowPts.map((p, i) => p.isAnomaly ? [i, p.rawValue] : null).filter(Boolean) as [number, number][]
  const filteredAnomalyData = windowPts.map((p, i) => p.isAnomaly ? [i, p.filteredValue] : null).filter(Boolean) as [number, number][]
  const residualAnomalyData = windowPts.map((p, i) => p.isAnomaly ? [i, p.residualValue] : null).filter(Boolean) as [number, number][]

  const xAxisRange = { min: 0, max: Math.max(win - 1, 0) }

  chartRaw.value?.setOption({
    xAxis: xAxisRange,
    series: [{ data: rawData }, { data: rawAnomalyData }]
  })
  chartFiltered.value?.setOption({
    xAxis: xAxisRange,
    series: [{ data: filteredData }, { data: filteredAnomalyData }]
  })
  chartResidual.value?.setOption({
    xAxis: xAxisRange,
    series: [{ data: residualData }, { data: residualAnomalyData }]
  })
  chartCompare.value?.setOption({
    xAxis: xAxisRange,
    series: [{ data: rawData }, { data: filteredData }]
  })
}

// 3σ动态阈值法异常检测
const detectAnomaly3Sigma = (points: HistoryAnalysisPoint[], multiplier: number = 3.0) => {
  if (!points.length) return
  
  // 计算残差序列的均值和标准差
  const residuals = points.map(p => p.residualValue)
  const mean = residuals.reduce((sum, val) => sum + val, 0) / residuals.length
  const variance = residuals.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) / residuals.length
  const stdDev = Math.sqrt(variance)
  
  // 动态阈值：均值 ± multiplier * 标准差
  const upperThreshold = mean + multiplier * stdDev
  const lowerThreshold = mean - multiplier * stdDev
  
  // 标记异常点
  let anomalyCount = 0
  points.forEach(point => {
    const isAnomaly = point.residualValue > upperThreshold || point.residualValue < lowerThreshold
    point.isAnomaly = isAnomaly
    if (isAnomaly) {
      anomalyCount++
      point.anomalyType = '3sigma'
    }
  })
  
  return anomalyCount
}

// 分位数阈值法异常检测
const detectAnomalyQuantile = (points: HistoryAnalysisPoint[], factor: number = 1.5) => {
  if (!points.length) return
  
  // 计算残差序列的95%分位数
  const residuals = points.map(p => Math.abs(p.residualValue)).sort((a, b) => a - b)
  const quantile95Index = Math.floor(residuals.length * 0.95)
  const quantile95 = residuals[quantile95Index] || residuals[residuals.length - 1]
  const threshold = quantile95 * factor
  
  // 标记异常点
  let anomalyCount = 0
  points.forEach(point => {
    const isAnomaly = Math.abs(point.residualValue) > threshold
    point.isAnomaly = isAnomaly
    if (isAnomaly) {
      anomalyCount++
      point.anomalyType = 'quantile'
    }
  })
  
  return anomalyCount
}

const handleAnalyze = async () => {
  if (!canAnalyze.value) {
    ElMessage.warning('请先选择至少一个 TDMS 文件')
    return
  }
  analyzing.value = true
  try {
    const form = buildFormData()
    const resp = await analyzeTdmsHistory(form, { filterType: filterType.value, ...kalmanParams })
    if (!resp.points.length) {
      ElMessage.warning('未解析到有效样本')
      return
    }
    
    // 应用异常检测算法
    if (anomalyAlgorithm.value === '3sigma') {
      resp.anomalyCount = detectAnomaly3Sigma(resp.points, sigmaMultiplier.value)
      ElMessage.success(`分析完成，使用3σ动态阈值法检测到 ${resp.anomalyCount} 个异常点`)
    } else {
      resp.anomalyCount = detectAnomalyQuantile(resp.points, thresholdFactor.value)
      ElMessage.success(`分析完成，使用分位数阈值法检测到 ${resp.anomalyCount} 个异常点`)
    }
    
    analysisResult.value = resp

    initCharts()
    playIndex.value = 0
    playHead.value = 0
    renderFull()
    // 默认不自动播放，由用户点击播放按钮控制
    playing.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '分析失败')
  } finally {
    analyzing.value = false
  }
}

// 仅重新按当前算法和参数做异常检测，并刷新右上角"当前异常点"数量；波形样式保持不变
const handleDetectAnomaly = async () => {
  if (!analysisResult.value) {
    ElMessage.warning('请先完成一次信号分析')
    return
  }
  analyzing.value = true
  try {
    // 重新应用异常检测算法
    if (anomalyAlgorithm.value === '3sigma') {
      analysisResult.value.anomalyCount = detectAnomaly3Sigma(analysisResult.value.points, sigmaMultiplier.value)
      ElMessage.success(`异常检测完成，使用3σ动态阈值法检测到 ${analysisResult.value.anomalyCount} 个异常点`)
    } else {
      analysisResult.value.anomalyCount = detectAnomalyQuantile(analysisResult.value.points, thresholdFactor.value)
      ElMessage.success(`异常检测完成，使用分位数阈值法检测到 ${analysisResult.value.anomalyCount} 个异常点`)
    }
    
    // 重新按当前播放位置渲染一遍窗口
    renderByIndex(playIndex.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '异常检测失败')
  } finally {
    analyzing.value = false
  }
}

const tick = () => {
  if (!playing.value || !analysisResult.value) return

  const pts = analysisResult.value.points
  if (!pts.length) return

  // 按"索引"推进播放：每 40ms 推进 playSpeed 个点（允许小数，靠 playHead 累积）
  playHead.value = Math.max(playHead.value, playIndex.value)
  playHead.value += playSpeed.value
  const idx = Math.min(Math.floor(playHead.value), pts.length - 1)
  playIndex.value = idx
  renderByIndex(playIndex.value)
  if (playIndex.value >= maxIndex.value) {
    playing.value = false
  }
}

const togglePlay = () => {
  if (!analysisResult.value) return
  if (!playing.value) {
    // 刚开始播放时，先把窗口"填满"：
    // 直接把索引推到 windowSize-1（或最后一个点），让用户一上来就看到满窗口波形
    if (playIndex.value >= maxIndex.value) {
      playIndex.value = 0
      playHead.value = 0
    }
    const targetIdx = Math.min(Math.max(windowSize - 1, 0), maxIndex.value)
    playIndex.value = targetIdx
    playHead.value = playIndex.value
    // 先用新的索引渲染一次，保证一点击“播放”窗口就是填满的
    renderByIndex(playIndex.value)
    playing.value = true
  } else {
    // 暂停
    playing.value = false
  }
}

const applyProgress = () => {
  if (!analysisResult.value) return
  // 拖动进度条时，直接按新的索引重绘当前时间窗口
  renderByIndex(playIndex.value)
}

watch(
  () => playing.value,
  (val) => {
    if (val) {
      if (timer.value != null) {
        window.clearInterval(timer.value)
      }
      // 更高刷新率，让曲线像流水一样平滑滚动
      timer.value = window.setInterval(tick, 40)
    } else if (timer.value != null) {
      window.clearInterval(timer.value)
      timer.value = null
    }
  }
)

onMounted(() => {
  if (chartRawRef.value || chartFilteredRef.value || chartResidualRef.value || chartCompareRef.value) {
    initCharts()
    // 初始显示从 0 开始的时间窗口
    if (analysisResult.value && analysisResult.value.points.length) {
      renderByIndex(0)
    }
  }
})

onBeforeUnmount(() => {
  if (timer.value != null) {
    window.clearInterval(timer.value)
  }
  chartRaw.value?.dispose()
  chartFiltered.value?.dispose()
  chartResidual.value?.dispose()
  chartCompare.value?.dispose()
})

const downloadPng = () => {
  if (!analysisResult.value) return
  const charts = [
    { ins: chartRaw.value, name: 'raw' },
    { ins: chartFiltered.value, name: 'filtered' },
    { ins: chartResidual.value, name: 'residual' },
    { ins: chartCompare.value, name: 'compare' }
  ]
  const channelName = analysisResult.value.channel.name || 'channel'
  charts.forEach(({ ins, name }) => {
    if (!ins) return
    const url = ins.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#ffffff' })
    const a = document.createElement('a')
    a.href = url
    a.download = `${channelName}_${name}.png`
    a.click()
  })
}
</script>

<style scoped>
.upload-panels {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.upload-card {
  height: 100%;
}
.card-header {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-weight: 600;
}
.sub-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.upload-icon {
  font-size: 40px;
  color: var(--el-color-primary);
  margin-bottom: 8px;
}
.file-list {
  max-height: 140px;
  margin-top: 8px;
}
.file-item {
  display: flex;
  justify-content: space-between;
  padding: 2px 0;
  font-size: 13px;
}
.file-item .name {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-item .size {
  color: var(--el-text-color-secondary);
}
.config-card {
  margin-bottom: 16px;
}
.config-content {
  padding: 8px 0;
}
.config-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}
.config-row:last-child {
  margin-bottom: 0;
}
.config-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.config-label {
  font-size: 14px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  min-width: fit-content;
}
.threshold-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
.info-icon {
  color: var(--el-color-info);
  cursor: help;
  font-size: 16px;
}
.algorithm-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.algorithm-name {
  flex: 1;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--el-bg-color-page);
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
.anomaly-summary {
  display: flex;
  align-items: center;
}
.summary-label {
  font-size: 14px;
  margin-right: 4px;
}
.anomaly-summary .count {
  color: var(--el-color-danger);
  font-weight: 700;
  font-size: 18px;
  margin-left: 4px;
}
.controls {
  display: flex;
  gap: 12px;
  align-items: center;
}
.controls .el-button {
  display: flex;
  align-items: center;
  gap: 6px;
}
.chart-grid {
  margin-top: 8px;
}
.chart-panel {
  height: 260px;
}
.chart-title {
  font-size: 13px;
  margin: 4px 0;
  color: var(--el-text-color-secondary);
}
.playback-bar {
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-light);
}
.controls {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.playback-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}
.label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.progress-slider {
  flex: 1;
}
.speed-slider {
  width: 160px;
}
.time {
  font-family: SFMono-Regular, ui-monospace, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
  font-size: 12px;
}
</style>

