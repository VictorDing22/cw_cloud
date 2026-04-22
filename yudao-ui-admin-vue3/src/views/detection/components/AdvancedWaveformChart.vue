<template>
  <div class="advanced-waveform-container">
    <el-row :gutter="16">
      <!-- 1. 时域波形对比 -->
      <el-col :span="12">
        <div ref="timeDomainRef" :style="{ height: '400px', width: '100%' }"></div>
      </el-col>
      
      <!-- 2. 频域分析(FFT) -->
      <el-col :span="12">
        <div ref="frequencyDomainRef" :style="{ height: '400px', width: '100%' }"></div>
      </el-col>
      
      <!-- 3. 滤波误差分布 -->
      <el-col :span="12">
        <div ref="errorDistributionRef" :style="{ height: '400px', width: '100%' }"></div>
      </el-col>
      
      <!-- 4. 数据分布直方图 -->
      <el-col :span="12">
        <div ref="histogramRef" :style="{ height: '400px', width: '100%' }"></div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  originalData: { type: Array, default: () => [] }, // 原始信号数据 [[timestamp, value], ...]
  filteredData: { type: Array, default: () => [] }, // 滤波信号数据 [[timestamp, value], ...]
  // 当前窗口在全量数据中的起始索引（用于回放时让X轴滚动）
  startIndex: { type: Number, default: 0 },
  // 累计数据（用于让直方图/误差分布随播放逐步增长）
  accumulatedOriginalData: { type: Array, default: () => [] },
  accumulatedFilteredData: { type: Array, default: () => [] },
})

const timeDomainRef = ref<HTMLElement | null>(null)
const frequencyDomainRef = ref<HTMLElement | null>(null)
const errorDistributionRef = ref<HTMLElement | null>(null)
const histogramRef = ref<HTMLElement | null>(null)

let timeDomainChart: echarts.ECharts | null = null
let frequencyDomainChart: echarts.ECharts | null = null
let errorDistributionChart: echarts.ECharts | null = null
let histogramChart: echarts.ECharts | null = null

// 存储Y轴范围，避免频繁变化
const fftYAxisMax = ref<number>(1)
// 误差分布：默认固定到 ±0.003（与示例图一致），尽量不变；仅当数据明显超出时才扩展
const errorYAxisMin = ref<number>(-0.003)
const errorYAxisMax = ref<number>(0.003)
const histogramYAxisMax = ref<number>(10)

// === 仅用于「滤波误差分布」和「数据分布直方图」的增量累计状态 ===
// 目标：让两张图从播放第一秒开始逐步变化，而不是最后一秒才突然更新
const accLen = ref(0) // 当前累计到的长度（点数）
const accErrorAll = ref<Array<[number, number]>>([])
const accOrigAll = ref<number[]>([])
const accFiltAll = ref<number[]>([])

// 直方图：固定 bin 区间，避免 globalMin/globalMax 随播放变化导致柱子“跳变”
const histFixedRange = ref<{ min: number; max: number } | null>(null)
// 直方图：时间平滑后的计数（让它“慢慢增长”更自然）
const histSmoothedOrig = ref<number[]>([])
const histSmoothedFilt = ref<number[]>([])

const toVal = (d: any): number => {
  if (typeof d === 'number') return Number.isFinite(d) ? d : 0
  if (Array.isArray(d) && d.length >= 2) return Number(d[1]) || 0
  return 0
}

const downsampleArray = (arr: number[], max: number) => {
  if (arr.length <= max) return arr
  const step = Math.ceil(arr.length / max)
  const out: number[] = []
  for (let i = 0; i < arr.length; i += step) out.push(arr[i])
  return out
}

const downsamplePoints = (pts: Array<[number, number]>, max: number) => {
  if (pts.length <= max) return pts
  const step = Math.ceil(pts.length / max)
  const out: Array<[number, number]> = []
  for (let i = 0; i < pts.length; i += step) out.push(pts[i])
  return out
}

// 简单移动平均（用于“误差随时间演化”的平滑曲线）
const movingAverage = (values: number[], windowSize: number) => {
  const n = values.length
  if (n === 0) return []
  const w = Math.max(1, Math.floor(windowSize))
  if (w <= 1) return values.slice()
  const out = new Array(n).fill(0)
  let sum = 0
  let count = 0
  for (let i = 0; i < n; i++) {
    sum += values[i]
    count++
    if (i - w >= 0) {
      sum -= values[i - w]
      count--
    }
    out[i] = sum / Math.max(count, 1)
  }
  return out
}

// 直方图计数的空间平滑（减小两端尖峰的视觉突兀）
const smoothCountsSpatial = (counts: number[]) => {
  if (!counts || counts.length === 0) return []
  const k = [1, 2, 3, 2, 1]
  const ks = 9
  const n = counts.length
  const out = new Array(n).fill(0)
  for (let i = 0; i < n; i++) {
    let s = 0
    let w = 0
    for (let j = -2; j <= 2; j++) {
      const idx = Math.min(Math.max(i + j, 0), n - 1)
      const kw = k[j + 2]
      s += (counts[idx] || 0) * kw
      w += kw
    }
    out[i] = s / Math.max(w || ks, 1)
  }
  return out
}

// 同步累计：优先使用父组件传入的累计数组（节流后每 ~200ms 更新一次）
const syncAccumulated = () => {
  const o = (props.accumulatedOriginalData || []) as any[]
  const f = (props.accumulatedFilteredData || []) as any[]
  const newLen = Math.min(o.length, f.length)

  // 没数据：重置
  if (newLen <= 0) {
    accLen.value = 0
    accErrorAll.value = []
    accOrigAll.value = []
    accFiltAll.value = []
    histFixedRange.value = null
    histSmoothedOrig.value = []
    histSmoothedFilt.value = []
    return
  }

  // 回放重置或拖动到更早：重建（避免出现“只有最后一刻才有”）
  if (newLen < accLen.value) {
    accLen.value = 0
    accErrorAll.value = []
    accOrigAll.value = []
    accFiltAll.value = []
    histFixedRange.value = null
    histSmoothedOrig.value = []
    histSmoothedFilt.value = []
  }

  // 大跳跃（拖动进度条）：直接重建一次（否则增量要补太多）
  const delta = newLen - accLen.value
  if (delta > 5000 && accLen.value > 0) {
    accOrigAll.value = o.slice(0, newLen).map(toVal)
    accFiltAll.value = f.slice(0, newLen).map(toVal)
    accErrorAll.value = computeError(accOrigAll.value, accFiltAll.value, 0)
    accLen.value = newLen
    return
  }

  // 增量追加
  if (newLen > accLen.value) {
    for (let i = accLen.value; i < newLen; i++) {
      const ov = toVal(o[i])
      const fv = toVal(f[i])
      accOrigAll.value.push(ov)
      accFiltAll.value.push(fv)
      // 误差点全量先存着，展示时再下采样
      accErrorAll.value.push([i, ov - fv])
    }
    accLen.value = newLen
  }

  // 极端长序列：做一次“压缩”，避免内存持续增长
  const HARD_LIMIT = 80000
  if (accOrigAll.value.length > HARD_LIMIT) {
    accOrigAll.value = downsampleArray(accOrigAll.value, 40000)
    accFiltAll.value = downsampleArray(accFiltAll.value, 40000)
    accErrorAll.value = downsamplePoints(accErrorAll.value, 40000)
    accLen.value = accOrigAll.value.length
  }
}

// 计算 FFT (优化的 DFT 实现)
const computeFFT = (data: number[]): { frequencies: number[], amplitudes: number[] } => {
  const N = data.length
  if (N < 2) return { frequencies: [], amplitudes: [] }
  
  // 去均值（去除DC分量，避免DC分量过大影响频谱显示）
  const mean = data.reduce((sum, val) => sum + val, 0) / N
  const centeredData = data.map(val => val - mean)
  
  // 采样率：假设数据是等间隔采样的，根据数据点数估算
  // 如果数据点少，使用较小的采样率；数据点多，使用较大的采样率
  // 这里假设每个数据点代表1ms的采样间隔（1000Hz采样率）
  const sampleRate = 1000 // Hz，可以根据实际采样间隔调整
  const df = sampleRate / N // 频率分辨率
  
  const frequencies: number[] = []
  const amplitudes: number[] = []
  
  // 只计算到奈奎斯特频率（N/2），这是有意义的频率范围
  const maxK = Math.floor(N / 2)
  
  for (let k = 0; k < maxK; k++) {
    let real = 0
    let imag = 0
    
    // DFT计算：X(k) = Σ x(n) * e^(-j*2π*k*n/N)
    const angleStep = -2 * Math.PI * k / N
    
    for (let n = 0; n < N; n++) {
      const angle = angleStep * n
      real += centeredData[n] * Math.cos(angle)
      imag += centeredData[n] * Math.sin(angle)
    }
    
    // 计算幅度并归一化
    // 对于k=0（DC分量），不需要乘以2
    // 对于k=N/2（奈奎斯特频率），如果N是偶数，也不需要乘以2
    // 其他频率分量需要乘以2（因为负频率的对称性）
    let amplitude = Math.sqrt(real * real + imag * imag) / N
    if (k > 0 && (N % 2 === 0 ? k < N / 2 : k <= (N - 1) / 2)) {
      amplitude *= 2
    }
    
    // 避免显示过小的值（可能是数值误差）
    if (amplitude < 1e-10) {
      amplitude = 0
    }
    
    frequencies.push(k * df)
    amplitudes.push(amplitude)
  }
  
  return { frequencies, amplitudes }
}

// 计算滤波误差（支持抽样，避免累计点过多导致卡顿）
const computeError = (original: number[], filtered: number[], baseIndex: number): Array<[number, number]> => {
  const errors: Array<[number, number]> = []
  const minLen = Math.min(original.length, filtered.length)
  
  // 最多保留这么多点（散点图够用且不卡）
  const MAX_POINTS = 4000
  const step = Math.max(1, Math.ceil(minLen / MAX_POINTS))

  for (let i = 0; i < minLen; i += step) {
    const origVal = Number(original[i]) || 0
    const filtVal = Number(filtered[i]) || 0
    const error = origVal - filtVal
    errors.push([baseIndex + i, error])
  }
  
  return errors
}

// 计算直方图数据（可指定统一的 min/max，保证两组柱子对齐）
const computeHistogram = (
  data: number[],
  bins: number = 50,
  range?: { min: number; max: number }
): { values: number[]; counts: number[] } => {
  if (data.length === 0) return { values: [], counts: [] }

  const values = data.map((d: any) => (typeof d === 'number' ? d : d?.[1]))
    .map((v: any) => Number(v))
    .filter((v: number) => Number.isFinite(v))

  if (values.length === 0) return { values: [], counts: [] }

  const min = range ? range.min : Math.min(...values)
  const max = range ? range.max : Math.max(...values)
  const span = Math.max(max - min, 1e-12)
  const binWidth = span / bins

  const histogram = new Array(bins).fill(0)
  const binCenters: number[] = []

  for (let i = 0; i < bins; i++) {
    binCenters.push(min + (i + 0.5) * binWidth)
  }

  values.forEach((val: number) => {
    const rawIdx = Math.floor((val - min) / binWidth)
    const binIndex = Math.min(Math.max(rawIdx, 0), bins - 1)
    histogram[binIndex]++
  })

  return { values: binCenters, counts: histogram }
}

// 处理数据
const processedData = computed(() => {
  const original = (props.originalData || []) as any[]
  const filtered = (props.filteredData || []) as any[]
  
  if (original.length === 0 && filtered.length === 0) {
    return {
      originalSamples: [],
      filteredSamples: [],
      originalFFT: { frequencies: [], amplitudes: [] },
      filteredFFT: { frequencies: [], amplitudes: [] },
      // 误差/直方图由增量累计逻辑驱动，这里留空避免每帧重算拖到“最后一刻”
      errors: [],
      originalHist: { values: [], counts: [] },
      filteredHist: { values: [], counts: [] }
    }
  }
  
  // 提取数值（支持 [timestamp, value] 或直接 value）
  const originalValues = original.map(d => {
    if (typeof d === 'number') return d
    if (Array.isArray(d) && d.length >= 2) return Number(d[1]) || 0
    return 0
  })
  
  const filteredValues = filtered.map(d => {
    if (typeof d === 'number') return d
    if (Array.isArray(d) && d.length >= 2) return Number(d[1]) || 0
    return 0
  })
  
  // 提取采样索引（用于时域图）- 使用实际的数据索引
  const minLen = Math.min(original.length, filtered.length)
  const originalSamples = original.slice(0, minLen).map((d, i) => {
    const val = typeof d === 'number' ? d : (Array.isArray(d) ? d[1] : 0)
    return [props.startIndex + i, Number(val) || 0]
  })
  
  const filteredSamples = filtered.slice(0, minLen).map((d, i) => {
    const val = typeof d === 'number' ? d : (Array.isArray(d) ? d[1] : 0)
    return [props.startIndex + i, Number(val) || 0]
  })

  // 时域图：窗口放大后做下采样，保证“全图有数据”同时避免渲染卡顿
  const MAX_TIME_POINTS = 2000
  const originalSamplesDown = downsamplePoints(originalSamples as Array<[number, number]>, MAX_TIME_POINTS)
  const filteredSamplesDown = downsamplePoints(filteredSamples as Array<[number, number]>, MAX_TIME_POINTS)
  
  // 计算 FFT（只使用相同长度的数据，至少需要2个点）
  const fftDataLength = Math.max(minLen, 2)
  const originalFFT = originalValues.length >= 2 ? computeFFT(originalValues.slice(0, fftDataLength)) : { frequencies: [], amplitudes: [] }
  const filteredFFT = filteredValues.length >= 2 ? computeFFT(filteredValues.slice(0, fftDataLength)) : { frequencies: [], amplitudes: [] }
  
  return {
    originalSamples: originalSamplesDown,
    filteredSamples: filteredSamplesDown,
    originalFFT,
    filteredFFT,
    errors: [],
    originalHist: { values: [], counts: [] },
    filteredHist: { values: [], counts: [] }
  }
})

// 初始化时域波形对比图
const initTimeDomainChart = () => {
  if (!timeDomainRef.value) return
  timeDomainChart = echarts.init(timeDomainRef.value)
  
  const updateChart = () => {
    const { originalSamples, filteredSamples } = processedData.value
    
    const option: any = {
      backgroundColor: 'transparent',
      title: {
        text: '时域波形对比',
        left: 'center',
        textStyle: { color: '#303133', fontSize: 14 }
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(255, 255, 255, 0.95)',
        borderColor: '#ebeef5',
        borderWidth: 1,
        textStyle: { color: '#303133' }
      },
      legend: {
        data: ['原始信号', '滤波信号'],
        top: 30,
        textStyle: { color: '#303133' }
      },
      grid: {
        left: '10%',
        right: '4%',
        bottom: '15%',
        top: '20%',
        containLabel: true
      },
      xAxis: {
        type: 'value',
        name: '采样',
        nameLocation: 'middle',
        nameGap: 30,
        nameTextStyle: { color: '#606266' },
        axisLine: { lineStyle: { color: '#dcdfe6' } },
        axisLabel: { color: '#606266' },
        splitLine: { show: false }
      },
      yAxis: {
        type: 'value',
        name: '幅值',
        nameLocation: 'middle',
        nameGap: 50,
        nameTextStyle: { color: '#606266' },
        axisLine: { lineStyle: { color: '#dcdfe6' } },
        axisLabel: { color: '#606266' },
        splitLine: { lineStyle: { color: '#ebeef5' } }
      },
      dataZoom: [{
        type: 'slider',
        show: true,
        xAxisIndex: [0],
        bottom: 10,
        height: 20,
        handleStyle: { color: '#409EFF' },
        dataBackground: { areaStyle: { color: '#666' } },
        selectedDataBackground: { areaStyle: { color: '#409EFF' } }
      }],
      series: [
        {
          name: '原始信号',
          type: 'line',
          data: originalSamples,
          smooth: true,
          symbol: 'none',
          lineStyle: { color: '#409EFF', width: 1 },
          areaStyle: { opacity: 0.1, color: '#409EFF' }
        },
        {
          name: '滤波信号',
          type: 'line',
          data: filteredSamples,
          smooth: true,
          symbol: 'none',
          lineStyle: { color: '#f56c6c', width: 1 },
          areaStyle: { opacity: 0.1, color: '#f56c6c' }
        }
      ]
    }
    
    timeDomainChart?.setOption(option, true)
  }
  
  updateChart()
  watch(() => processedData.value, updateChart, { deep: true })
}

// 初始化频域分析图
const initFrequencyDomainChart = () => {
  if (!frequencyDomainRef.value) return
  frequencyDomainChart = echarts.init(frequencyDomainRef.value)
  
  const updateChart = () => {
    const { originalFFT, filteredFFT } = processedData.value
    
    // 计算Y轴最大值：包含全频段峰值，避免左侧峰值“顶到天花板”导致看不出具体大小
    // 仍然用分位数和阈值更新来保持Y轴稳定（不乱跳）
    const allAmplitudes = [
      ...(originalFFT.amplitudes || []),
      ...(filteredFFT.amplitudes || [])
    ]
      .map(v => (Number.isFinite(v) ? Number(v) : 0))
      .filter(v => v >= 0)

    if (allAmplitudes.length > 0) {
      const sorted = [...allAmplitudes].sort((a, b) => a - b)
      // 用 99% 分位作为“有效最大值”，避免极端异常点把Y轴拉得过大
      const p99Index = Math.max(0, Math.floor(sorted.length * 0.99) - 1)
      const p99 = sorted[p99Index] ?? 0
      const currentMax = Math.max(p99, sorted[sorted.length - 1] ?? 0)

      if (currentMax > 0) {
        // 留出 10% 余量，并保留两位小数，避免刻度抖动
        const newMax = Math.ceil(currentMax * 1.1 * 100) / 100
        const nextMax = Math.max(newMax, 0.2)

        // 只在变化超过20%时才更新，保持稳定
        if (nextMax > fftYAxisMax.value * 1.2 || nextMax < fftYAxisMax.value * 0.8) {
          fftYAxisMax.value = nextMax
        }
      }
    } else {
      if (fftYAxisMax.value <= 0) fftYAxisMax.value = 1
    }
    
    const option: any = {
      backgroundColor: 'transparent',
      title: {
        text: '频域分析(FFT)',
        left: 'center',
        textStyle: { color: '#303133', fontSize: 14 }
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(255, 255, 255, 0.95)',
        borderColor: '#ebeef5',
        borderWidth: 1,
        textStyle: { color: '#303133' }
      },
      legend: {
        data: ['原始频谱', '滤波频谱'],
        top: 30,
        textStyle: { color: '#303133' }
      },
      grid: {
        left: '10%',
        right: '4%',
        bottom: '15%',
        top: '20%',
        containLabel: true
      },
      xAxis: {
        type: 'value',
        name: '频率 (Hz)',
        nameLocation: 'middle',
        nameGap: 30,
        nameTextStyle: { color: '#606266' },
        axisLine: { lineStyle: { color: '#dcdfe6' } },
        axisLabel: { color: '#606266' },
        splitLine: { show: false },
        min: 0,
        scale: false // 不自动缩放，从0开始
      },
      yAxis: {
        type: 'value',
        name: '幅值',
        nameLocation: 'middle',
        nameGap: 50,
        nameTextStyle: { color: '#606266' },
        axisLine: { lineStyle: { color: '#dcdfe6' } },
        axisLabel: { color: '#606266' },
        splitLine: { lineStyle: { color: '#ebeef5' } },
        min: 0, // 幅值从0开始
        scale: false, // 不自动缩放，保持固定范围
        max: fftYAxisMax.value || 1 // 使用稳定的最大值
      },
      dataZoom: [{
        type: 'slider',
        show: true,
        xAxisIndex: [0],
        bottom: 10,
        height: 20,
        handleStyle: { color: '#409EFF' },
        dataBackground: { areaStyle: { color: '#666' } },
        selectedDataBackground: { areaStyle: { color: '#409EFF' } }
      }],
      series: [
        {
          name: '原始频谱',
          type: 'line',
          data: originalFFT.frequencies.length > 0 
            ? originalFFT.frequencies.map((f, i) => [f, originalFFT.amplitudes[i] || 0])
            : [],
          smooth: false, // 频域数据不需要平滑
          symbol: 'none',
          step: false,
          areaStyle: { opacity: 0.3, color: '#409EFF' },
          lineStyle: { color: '#409EFF', width: 2 },
          emphasis: {
            focus: 'series',
            lineStyle: { width: 3 }
          }
        },
        {
          name: '滤波频谱',
          type: 'line',
          data: filteredFFT.frequencies.length > 0
            ? filteredFFT.frequencies.map((f, i) => [f, filteredFFT.amplitudes[i] || 0])
            : [],
          smooth: false, // 频域数据不需要平滑
          symbol: 'none',
          step: false,
          areaStyle: { opacity: 0.3, color: '#f56c6c' },
          lineStyle: { color: '#f56c6c', width: 2 },
          emphasis: {
            focus: 'series',
            lineStyle: { width: 3 }
          }
        }
      ]
    }
    
    frequencyDomainChart?.setOption(option, true)
  }
  
  updateChart()
  watch(() => processedData.value, updateChart, { deep: true })
}

// 初始化滤波误差分布图
const initErrorDistributionChart = () => {
  if (!errorDistributionRef.value) return
  errorDistributionChart = echarts.init(errorDistributionRef.value)
  
  const updateChart = () => {
    // 增量同步累计数据
    syncAccumulated()
    const rawErrors = downsamplePoints(accErrorAll.value, 4000)

    // 将“点状噪声”转换为“随时间演化”的平滑曲线
    // 用移动平均窗口（约 50 点）表现整体趋势；窗口越大越平滑
    const xs = rawErrors.map(p => p[0])
    const ys = rawErrors.map(p => p[1])
    const smoothY = movingAverage(ys, 50)
    const errorsLine: Array<[number, number]> = xs.map((x, i) => [x, smoothY[i] ?? 0])
    
    // Y轴范围“基本不变”：默认 ±0.003；只有当误差明显超出当前范围才扩展（按 0.001 粒度）
    if (errorsLine.length > 0) {
      const y2 = errorsLine.map(e => e[1]).filter(v => Number.isFinite(v))
      if (y2.length > 0) {
        const minY = Math.min(...y2)
        const maxY = Math.max(...y2)
        // 超出当前范围 10% 才扩展，避免频繁变化
        if (minY < errorYAxisMin.value * 1.1) {
          const nextMin = Math.floor(minY * 1.1 * 1000) / 1000
          errorYAxisMin.value = Math.min(nextMin, -0.003)
        }
        if (maxY > errorYAxisMax.value * 1.1) {
          const nextMax = Math.ceil(maxY * 1.1 * 1000) / 1000
          errorYAxisMax.value = Math.max(nextMax, 0.003)
        }
      }
    }
    
    const option: any = {
      backgroundColor: 'transparent',
      title: {
        text: '滤波误差分布',
        left: 'center',
        textStyle: { color: '#303133', fontSize: 14 }
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(255, 255, 255, 0.95)',
        borderColor: '#ebeef5',
        borderWidth: 1,
        textStyle: { color: '#303133' },
        axisPointer: { type: 'line' }
      },
      grid: {
        left: '10%',
        right: '4%',
        bottom: '15%',
        top: '15%',
        containLabel: true
      },
      xAxis: {
        type: 'value',
        name: '采样',
        nameLocation: 'middle',
        nameGap: 30,
        nameTextStyle: { color: '#606266' },
        axisLine: { lineStyle: { color: '#dcdfe6' } },
        axisLabel: { color: '#606266' },
        splitLine: { show: false },
        // 累计型：X轴从 0 增长到当前播放进度
        min: 0,
        max: Math.max(accLen.value - 1, 100)
      },
      yAxis: {
        type: 'value',
        name: '误差',
        nameLocation: 'middle',
        nameGap: 50,
        nameTextStyle: { color: '#606266' },
        axisLine: { lineStyle: { color: '#dcdfe6' } },
        axisLabel: { color: '#606266' },
        splitLine: { lineStyle: { color: '#ebeef5' } },
        scale: false, // 不自动缩放，保持固定范围
        min: errorYAxisMin.value, // 使用稳定的最小值
        max: errorYAxisMax.value // 使用稳定的最大值
      },
      series: [
        {
          name: '滤波误差',
          type: 'line',
          data: errorsLine,
          showSymbol: false,
          smooth: true,
          lineStyle: { color: 'rgba(103, 194, 58, 0.95)', width: 1.5 },
          areaStyle: { color: 'rgba(103, 194, 58, 0.10)' },
          // 0 基线
          markLine: {
            symbol: 'none',
            label: { show: false },
            lineStyle: { color: '#dcdfe6', width: 1 },
            data: [{ yAxis: 0 }]
          }
        }
      ]
    }
    
    errorDistributionChart?.setOption(option, true)
  }
  
  updateChart()
  // 只监听累计数组长度变化（父组件已节流），避免 deep watch 大数组导致“最后一刻才更新”
  watch(
    [() => (props.accumulatedOriginalData as any[]).length, () => (props.accumulatedFilteredData as any[]).length],
    updateChart
  )
}

// 初始化数据分布直方图
const initHistogramChart = () => {
  if (!histogramRef.value) return
  histogramChart = echarts.init(histogramRef.value)
  
  const updateChart = () => {
    // 增量同步累计数据
    syncAccumulated()

    // 为避免每帧重算直方图（尤其是累计点多），做一次 200ms 节流
    // 但父组件也已节流，所以这里主要是兜底
    const now = Date.now()
    const shouldRecalc = (updateChart as any)._lastMs ? (now - (updateChart as any)._lastMs > 200) : true
    if (shouldRecalc) (updateChart as any)._lastMs = now
    else {
      // 仍然可以只刷新 Y 轴上限（如果需要），这里直接继续用旧值渲染
    }

    // 计算直方图：使用累计值（做一次下采样，保持性能）
    const origVals = downsampleArray(accOrigAll.value, 10000)
    const filtVals = downsampleArray(accFiltAll.value, 10000)
    const combined = [...origVals, ...filtVals].filter((v) => Number.isFinite(v))

    // 固定 bin 区间：首次有数据时锁定，后续不再随播放改变，避免“最后一秒突然跳变”
    if (!histFixedRange.value && combined.length > 0) {
      const min = Math.min(...combined)
      const max = Math.max(...combined)
      const pad = Math.max((max - min) * 0.05, 1e-6)
      histFixedRange.value = { min: min - pad, max: max + pad }
    }
    const histRange = histFixedRange.value || { min: -1, max: 1 }

    const originalHistRaw = origVals.length > 0 ? computeHistogram(origVals, 50, histRange) : { values: [], counts: [] }
    const filteredHistRaw = filtVals.length > 0 ? computeHistogram(filtVals, 50, histRange) : { values: [], counts: [] }

    // 空间平滑：让分布更“连续”，减少两端尖峰的突兀（视觉更自然）
    const originalCountsSpatial = smoothCountsSpatial((originalHistRaw.counts || []).map((c) => c || 0))
    const filteredCountsSpatial = smoothCountsSpatial((filteredHistRaw.counts || []).map((c) => c || 0))

    // 时间平滑：让柱状图随播放“慢慢增长”，避免某一帧突然变高
    const alpha = 0.25 // 越小越平滑、越“慢慢长”
    const bins = originalCountsSpatial.length || filteredCountsSpatial.length || 50
    if (histSmoothedOrig.value.length !== bins) histSmoothedOrig.value = new Array(bins).fill(0)
    if (histSmoothedFilt.value.length !== bins) histSmoothedFilt.value = new Array(bins).fill(0)
    for (let i = 0; i < bins; i++) {
      const o = originalCountsSpatial[i] || 0
      const f = filteredCountsSpatial[i] || 0
      histSmoothedOrig.value[i] = histSmoothedOrig.value[i] + alpha * (o - histSmoothedOrig.value[i])
      histSmoothedFilt.value[i] = histSmoothedFilt.value[i] + alpha * (f - histSmoothedFilt.value[i])
    }

    const originalHist = { values: originalHistRaw.values, counts: histSmoothedOrig.value }
    const filteredHist = { values: filteredHistRaw.values, counts: histSmoothedFilt.value }
    
    // 计算Y轴最大值，但只在变化较大时更新
    const maxCount = Math.max(
      ...(originalHist.counts || []),
      ...(filteredHist.counts || [])
    )
    if (maxCount > 0) {
      const newMax = Math.ceil(maxCount * 1.2 / 10) * 10
      // 只在变化超过30%时才更新，保持稳定
      if (newMax > histogramYAxisMax.value * 1.3 || newMax < histogramYAxisMax.value * 0.7) {
        histogramYAxisMax.value = Math.max(newMax, 10)
      }
    }
    
    const option: any = {
      backgroundColor: 'transparent',
      title: {
        text: '数据分布直方图',
        left: 'center',
        textStyle: { color: '#303133', fontSize: 14 }
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(255, 255, 255, 0.95)',
        borderColor: '#ebeef5',
        borderWidth: 1,
        textStyle: { color: '#303133' },
        axisPointer: { type: 'shadow' }
      },
      legend: {
        data: ['原始分布', '滤波分布'],
        top: 30,
        textStyle: { color: '#303133' }
      },
      grid: {
        left: '10%',
        right: '4%',
        bottom: '15%',
        top: '20%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        name: '数值',
        nameLocation: 'middle',
        nameGap: 30,
        nameTextStyle: { color: '#606266' },
        axisLine: { lineStyle: { color: '#dcdfe6' } },
        axisLabel: { color: '#606266', fontSize: 10, rotate: 0, interval: 4 },
        splitLine: { show: false },
        data: (originalHist.values || []).map(v => Number(v).toFixed(4))
      },
      yAxis: {
        type: 'value',
        name: '频数',
        nameLocation: 'middle',
        nameGap: 50,
        nameTextStyle: { color: '#606266' },
        axisLine: { lineStyle: { color: '#dcdfe6' } },
        axisLabel: { color: '#606266' },
        splitLine: { lineStyle: { color: '#ebeef5' } },
        min: 0, // 频数从0开始
        scale: false, // 不自动缩放，保持固定范围
        max: histogramYAxisMax.value || 10 // 使用稳定的最大值
      },
      series: [
        {
          name: '原始分布',
          type: 'bar',
          data: (originalHist.counts || []).map((c) => c || 0),
          itemStyle: { 
            color: 'rgba(64, 158, 255, 0.55)',
            borderColor: 'rgba(64, 158, 255, 0.95)',
            borderWidth: 1
          },
          barWidth: 6,
          barGap: '-35%', // 叠加对比更明显（类似截图）
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowColor: 'rgba(64, 158, 255, 0.5)'
            }
          }
        },
        {
          name: '滤波分布',
          type: 'bar',
          data: (filteredHist.counts || []).map((c) => c || 0),
          itemStyle: { 
            color: 'rgba(245, 108, 108, 0.45)',
            borderColor: 'rgba(245, 108, 108, 0.95)',
            borderWidth: 1
          },
          barWidth: 6,
          barGap: '-35%',
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowColor: 'rgba(245, 108, 108, 0.5)'
            }
          }
        }
      ]
    }
    
    histogramChart?.setOption(option, true)
  }
  
  updateChart()
  watch(
    [() => (props.accumulatedOriginalData as any[]).length, () => (props.accumulatedFilteredData as any[]).length],
    updateChart
  )
}

const resizeCharts = () => {
  timeDomainChart?.resize()
  frequencyDomainChart?.resize()
  errorDistributionChart?.resize()
  histogramChart?.resize()
}

onMounted(() => {
  initTimeDomainChart()
  initFrequencyDomainChart()
  initErrorDistributionChart()
  initHistogramChart()
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  timeDomainChart?.dispose()
  frequencyDomainChart?.dispose()
  errorDistributionChart?.dispose()
  histogramChart?.dispose()
  window.removeEventListener('resize', resizeCharts)
})
</script>

<style scoped>
.advanced-waveform-container {
  width: 100%;
  padding: 16px;
  background: #ffffff;
  border-radius: 8px;
}
</style>
