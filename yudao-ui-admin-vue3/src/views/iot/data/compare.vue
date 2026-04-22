<template>
  <div class="app-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>参数数据对比</span>
          <el-button type="primary" @click="handleCompare">开始对比</el-button>
        </div>
      </template>

      <!-- 筛选条件 -->
      <el-form :model="queryParams" label-width="100px" :inline="true">
        <el-form-item label="产品">
          <el-select v-model="queryParams.productId" placeholder="请选择产品" @change="handleProductChange">
            <el-option
              v-for="item in productList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="设备">
          <el-select
            v-model="queryParams.deviceIds"
            placeholder="请选择设备（最多3个）"
            multiple
            :multiple-limit="3"
          >
            <el-option
              v-for="item in deviceList"
              :key="item.deviceKey"
              :label="`${item.deviceName} (${item.deviceKey})`"
              :value="item.deviceKey"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="参数">
          <el-select
            v-model="queryParams.parameters"
            placeholder="请选择参数（最多选择3个）"
            multiple
            :multiple-limit="3"
          >
            <el-option label="幅度 (dB)" value="amplitude" />
            <el-option label="能量 (Kcal)" value="energy" />
            <el-option label="RMS (mV)" value="rms" />
          </el-select>
        </el-form-item>

        <el-form-item label="统计时间">
          <el-select v-model="queryParams.timeRange" placeholder="请选择时间范围">
            <el-option label="最近1小时" value="1h" />
            <el-option label="最近6小时" value="6h" />
            <el-option label="最近12小时" value="12h" />
            <el-option label="最近24小时" value="24h" />
            <el-option label="最近7天" value="7d" />
          </el-select>
        </el-form-item>

        <el-form-item label="时间范围">
          <el-date-picker
            v-model="queryParams.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 对比图表 -->
    <el-card class="box-card mt-4" v-if="chartData.length > 0">
      <template #header>
        <div class="card-header">
          <span>参数趋势对比</span>
          <div>
            <el-radio-group v-model="chartType" @change="handleChartTypeChange">
              <el-radio-button label="line">折线图</el-radio-button>
              <el-radio-button label="bar">柱状图</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>

      <div ref="chartRef" style="width: 100%; height: 400px"></div>
    </el-card>

    <!-- 数据列表 -->
    <el-card class="box-card mt-4" v-if="tableData.length > 0">
      <template #header>
        <div class="card-header">
          <span>数据明细</span>
          <el-button type="success" @click="handleExport">导出数据</el-button>
        </div>
      </template>

      <el-table :data="tableData" border>
        <el-table-column prop="time" label="时间" width="180" />
        <el-table-column
          v-for="device in queryParams.deviceIds"
          :key="device"
          :label="getDeviceName(device)"
          align="center"
        >
          <el-table-column
            v-for="param in queryParams.parameters"
            :key="param"
            :label="getParamLabel(param)"
            :prop="`${device}_${param}`"
            width="120"
          />
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jump"
        @size-change="handleQuery"
        @current-change="handleQuery"
      />
    </el-card>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { getProductList } from '@/api/iot/product'
import { getDeviceList } from '@/api/iot/device'
import { getParameterCompareData } from '@/api/iot/data'

defineOptions({ name: 'IotDataCompare' })

const chartRef = ref<HTMLDivElement>()
let chartInstance: echarts.ECharts | null = null

// 查询参数
const queryParams = ref({
  productId: '',
  deviceIds: [] as string[],
  parameters: [] as string[],
  timeRange: '24h',
  dateRange: []
})

// 列表数据
const productList = ref<any[]>([])
const deviceList = ref<any[]>([])
const chartData = ref<any[]>([])
const tableData = ref<any[]>([])
const chartType = ref('line')

// 分页
const pagination = ref({
  page: 1,
  size: 20,
  total: 0
})

// 获取产品列表
const getProducts = async () => {
  try {
    const res = await getProductList({ pageNo: 1, pageSize: 100 })
    productList.value = res.data?.list || []
  } catch (error) {
    console.error('获取产品列表失败', error)
    // 使用Mock数据
    productList.value = [
      { id: '1', name: 'RAEM1' }
    ]
  }
}

// 产品变化时获取设备列表
const handleProductChange = async () => {
  queryParams.value.deviceIds = []
  if (!queryParams.value.productId) {
    deviceList.value = []
    return
  }
  
  try {
    const res = await getDeviceList({
      productId: queryParams.value.productId,
      pageNo: 1,
      pageSize: 100
    })
    deviceList.value = res.data?.list || []
  } catch (error) {
    console.error('获取设备列表失败', error)
  }
}

// 开始对比
const handleCompare = async () => {
  if (queryParams.value.deviceIds.length === 0) {
    ElMessage.warning('请至少选择一个设备')
    return
  }
  if (queryParams.value.parameters.length === 0) {
    ElMessage.warning('请至少选择一个参数')
    return
  }

  try {
    const res = await getParameterCompareData(queryParams.value)
    chartData.value = res.data?.chartData || []
    tableData.value = res.data?.tableData || []
    pagination.value.total = res.data?.total || 0

    // 渲染图表
    renderChart()
  } catch (error) {
    console.error('获取对比数据失败', error)
    ElMessage.error('获取对比数据失败')
  }
}

// 渲染图表
const renderChart = () => {
  if (!chartRef.value) return

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  const series = queryParams.value.deviceIds.flatMap((deviceId) =>
    queryParams.value.parameters.map((param) => ({
      name: `${getDeviceName(deviceId)} - ${getParamLabel(param)}`,
      type: chartType.value,
      data: chartData.value.map((item) => item[`${deviceId}_${param}`] || 0),
      smooth: true
    }))
  )

  const option = {
    title: {
      text: '参数趋势对比',
      left: 'center'
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      }
    },
    legend: {
      top: 30,
      data: series.map((s) => s.name)
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: chartData.value.map((item) => item.time)
    },
    yAxis: {
      type: 'value',
      name: '数值'
    },
    series: series
  }

  chartInstance.setOption(option)
}

// 图表类型变化
const handleChartTypeChange = () => {
  renderChart()
}

// 查询数据
const handleQuery = () => {
  handleCompare()
}

// 导出数据
const handleExport = () => {
  ElMessage.success('导出功能开发中...')
}

// 获取设备名称
const getDeviceName = (deviceId: string) => {
  const device = deviceList.value.find((d) => d.deviceKey === deviceId)
  return device ? device.deviceName : deviceId
}

// 获取参数标签
const getParamLabel = (param: string) => {
  const labels = {
    amplitude: '幅度 (dB)',
    energy: '能量 (Kcal)',
    rms: 'RMS (mV)'
  }
  return labels[param] || param
}

onMounted(() => {
  getProducts()
})

onBeforeUnmount(() => {
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped lang="scss">
.app-container {
  padding: 20px;
}

.box-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mt-4 {
  margin-top: 20px;
}
</style>

