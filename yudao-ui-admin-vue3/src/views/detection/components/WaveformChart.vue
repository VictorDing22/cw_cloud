<template>
  <div ref="chartRef" :style="{ height: height, width: '100%' }"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] },
  height: { type: String, default: '300px' },
  title: { type: String, default: '实时信号波形' },
  color: { type: String, default: '#409EFF' }
})

const chartRef = ref<HTMLElement | null>(null)
let chartInstance: echarts.ECharts | null = null

const initChart = () => {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  const option = {
    backgroundColor: 'transparent',
    title: { 
      text: props.title, 
      textStyle: { fontSize: 14, color: '#303133' },
      left: 'center'
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#ebeef5',
      borderWidth: 1,
      textStyle: { color: '#303133' }
    },
    xAxis: { 
      type: 'time', 
      splitLine: { show: false },
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#606266' }
    },
    yAxis: { 
      type: 'value', 
      boundaryGap: [0, '100%'],
      axisLine: { show: false },
      axisLabel: { color: '#606266' },
      splitLine: { lineStyle: { color: '#ebeef5' } }
    },
    series: [{
      name: '幅值',
      type: 'line',
      showSymbol: false,
      smooth: true,
      data: props.data,
      lineStyle: { width: 1, color: props.color },
      areaStyle: { 
        opacity: 0.1, 
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: props.color },
          { offset: 1, color: 'transparent' }
        ])
      },
      large: true
    }]
  }
  chartInstance.setOption(option)
}

watch(() => props.data, (newData) => {
  if (!chartInstance) return
  const data = (newData || []) as any[]

  const option: any = {
    series: [{ data }]
  }

  if (data.length > 0) {
    // 假设数据格式 [timestamp, value]
    const times = data.map((d) => Number(d[0]))
    const values = data.map((d) => Number(d[1]) || 0)

    const minTime = Math.min(...times)
    const maxTime = Math.max(...times)

    // 纵轴对称、平稳：以 0 为中心，取绝对值最大值并放大一点
    const maxAbs = Math.max(...values.map((v) => Math.abs(v))) || 1
    const base = Math.ceil(maxAbs * 10) / 10 // 保留 1 位小数向上取整

    option.xAxis = {
      type: 'time',
      min: minTime,
      max: maxTime
    }
    option.yAxis = {
      type: 'value',
      min: -base,
      max: base,
      splitNumber: 5
    }
  }

  chartInstance.setOption(option)
}, { deep: false })

onMounted(() => {
  initChart()
  window.addEventListener('resize', () => chartInstance?.resize())
})

onUnmounted(() => {
  chartInstance?.dispose()
})
</script>
