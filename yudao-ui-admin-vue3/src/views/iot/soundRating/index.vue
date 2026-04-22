<template>
  <div class="sound-rating-page">
    <div class="page-header">
      <h2>⭐ 声发射评级</h2>
      <p>基于声发射数据进行设备健康状态评级，预测设备剩余寿命</p>
    </div>
    
    <!-- 评级概览 -->
    <el-row :gutter="20" class="rating-overview">
      <el-col :span="6" v-for="(rating, index) in ratingLevels" :key="index">
        <el-card shadow="hover" :class="['rating-card', `rating-${rating.level}`]">
          <div class="rating-content">
            <div class="rating-icon">
              <Icon :icon="rating.icon" size="36" />
            </div>
            <div class="rating-info">
              <div class="rating-label">{{ rating.label }}</div>
              <div class="rating-count">{{ rating.count }}</div>
              <div class="rating-percent">{{ rating.percent }}%</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 筛选器 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="设备">
          <el-select v-model="queryForm.device" placeholder="全部设备" clearable style="width: 200px">
            <el-option label="qc_raem1_4g_107" value="qc_raem1_4g_107" />
            <el-option label="qc_raem1_4g_108" value="qc_raem1_4g_108" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="评级">
          <el-select v-model="queryForm.rating" placeholder="全部评级" clearable style="width: 150px">
            <el-option label="优秀" value="excellent" />
            <el-option label="良好" value="good" />
            <el-option label="一般" value="fair" />
            <el-option label="较差" value="poor" />
          </el-select>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="loadData" :icon="Search">查询</el-button>
          <el-button @click="handleReset" :icon="Refresh">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <!-- 评级列表 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>设备健康评级列表</span>
              <el-button size="small" @click="loadData" :icon="Refresh">刷新</el-button>
            </div>
          </template>
          
          <el-table :data="ratingData" v-loading="loading" stripe>
            <el-table-column prop="deviceName" label="设备名称" width="180" />
            <el-table-column label="健康评级" width="120">
              <template #default="{ row }">
                <el-rate 
                  v-model="row.ratingStars" 
                  disabled 
                  show-score 
                  :colors="['#99A9BF', '#F7BA2A', '#FF9900']"
                />
              </template>
            </el-table-column>
            <el-table-column label="评级" width="100">
              <template #default="{ row }">
                <el-tag :type="getRatingType(row.rating)">
                  {{ getRatingText(row.rating) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="healthScore" label="健康分数" width="100" align="right">
              <template #default="{ row }">
                <span :style="{ color: getScoreColor(row.healthScore) }">
                  {{ row.healthScore }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="remainingLife" label="预计剩余寿命" width="150">
              <template #default="{ row }">
                {{ row.remainingLife }} 天
              </template>
            </el-table-column>
            <el-table-column prop="evaluateTime" label="评级时间" width="180">
              <template #default="{ row }">
                {{ new Date(row.evaluateTime).toLocaleString() }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="viewDetail(row)">
                  详情
                </el-button>
                <el-button size="small" type="success" link @click="viewHistory(row)">
                  历史
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      
      <el-col :span="8">
        <el-card>
          <template #header>评级趋势</template>
          <div ref="trendChartRef" style="height: 300px"></div>
        </el-card>
        
        <el-card style="margin-top: 20px">
          <template #header>评级分布</template>
          <div ref="distributionChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { Icon } from '@iconify/vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

defineOptions({ name: 'IotSoundRating' })

interface RatingData {
  deviceId: string
  deviceName: string
  rating: 'excellent' | 'good' | 'fair' | 'poor'
  ratingStars: number
  healthScore: number
  remainingLife: number
  evaluateTime: number
}

const loading = ref(false)
const trendChartRef = ref<HTMLElement>()
const distributionChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let distributionChart: echarts.ECharts | null = null

const queryForm = reactive({
  device: '',
  rating: ''
})

const ratingData = ref<RatingData[]>([])

// 评级统计
const ratingLevels = computed(() => {
  const total = ratingData.value.length
  return [
    {
      level: 'excellent',
      label: '优秀',
      icon: 'ep:medal',
      count: ratingData.value.filter(r => r.rating === 'excellent').length,
      percent: total > 0 ? Math.round(ratingData.value.filter(r => r.rating === 'excellent').length / total * 100) : 0
    },
    {
      level: 'good',
      label: '良好',
      icon: 'ep:success-filled',
      count: ratingData.value.filter(r => r.rating === 'good').length,
      percent: total > 0 ? Math.round(ratingData.value.filter(r => r.rating === 'good').length / total * 100) : 0
    },
    {
      level: 'fair',
      label: '一般',
      icon: 'ep:warning-filled',
      count: ratingData.value.filter(r => r.rating === 'fair').length,
      percent: total > 0 ? Math.round(ratingData.value.filter(r => r.rating === 'fair').length / total * 100) : 0
    },
    {
      level: 'poor',
      label: '较差',
      icon: 'ep:circle-close-filled',
      count: ratingData.value.filter(r => r.rating === 'poor').length,
      percent: total > 0 ? Math.round(ratingData.value.filter(r => r.rating === 'poor').length / total * 100) : 0
    }
  ]
})

// 生成模拟数据
const generateMockData = (): RatingData[] => {
  const data: RatingData[] = []
  const ratings: Array<'excellent' | 'good' | 'fair' | 'poor'> = ['excellent', 'good', 'fair', 'poor']
  const now = Date.now()
  
  for (let i = 1; i <= 20; i++) {
    const rating = ratings[Math.floor(Math.random() * ratings.length)]
    let stars = 5
    let score = 95
    let life = 365
    
    if (rating === 'excellent') { stars = 5; score = 90 + Math.random() * 10; life = 300 + Math.random() * 200 }
    else if (rating === 'good') { stars = 4; score = 75 + Math.random() * 15; life = 200 + Math.random() * 100 }
    else if (rating === 'fair') { stars = 3; score = 60 + Math.random() * 15; life = 100 + Math.random() * 100 }
    else { stars = 2; score = 40 + Math.random() * 20; life = 30 + Math.random() * 70 }
    
    data.push({
      deviceId: `qc_raem1_4g_${100 + i}`,
      deviceName: `RAEM设备_${i}`,
      rating,
      ratingStars: stars,
      healthScore: Math.round(score),
      remainingLife: Math.round(life),
      evaluateTime: now - Math.random() * 3600000
    })
  }
  
  return data
}

// 初始化图表
const initCharts = () => {
  if (trendChartRef.value) {
    trendChart = echarts.init(trendChartRef.value)
    const now = Date.now()
    const trendData = Array.from({ length: 30 }, (_, i) => [
      now - (30 - i) * 86400000,
      70 + Math.random() * 25
    ])
    
    trendChart.setOption({
      title: { text: '健康分数趋势', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'time' },
      yAxis: { type: 'value', min: 0, max: 100, name: '分数' },
      series: [{
        type: 'line',
        data: trendData,
        smooth: true,
        lineStyle: { color: '#409eff', width: 2 },
        areaStyle: { color: 'rgba(64, 158, 255, 0.2)' }
      }]
    })
  }
  
  if (distributionChartRef.value) {
    distributionChart = echarts.init(distributionChartRef.value)
    distributionChart.setOption({
      title: { text: '评级分布', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: '70%',
        data: [
          { value: ratingLevels.value[0].count, name: '优秀', itemStyle: { color: '#67c23a' } },
          { value: ratingLevels.value[1].count, name: '良好', itemStyle: { color: '#409eff' } },
          { value: ratingLevels.value[2].count, name: '一般', itemStyle: { color: '#e6a23c' } },
          { value: ratingLevels.value[3].count, name: '较差', itemStyle: { color: '#f56c6c' } }
        ],
        label: { formatter: '{b}: {c} ({d}%)' }
      }]
    })
  }
}

const loadData = () => {
  loading.value = true
  setTimeout(() => {
    let data = generateMockData()
    
    if (queryForm.device) {
      data = data.filter(d => d.deviceId === queryForm.device)
    }
    if (queryForm.rating) {
      data = data.filter(d => d.rating === queryForm.rating)
    }
    
    ratingData.value = data
    loading.value = false
    
    // 更新图表
    initCharts()
  }, 500)
}

const handleReset = () => {
  queryForm.device = ''
  queryForm.rating = ''
  loadData()
}

const viewDetail = (row: RatingData) => {
  ElMessage.info(`查看 ${row.deviceName} 的详细评级报告`)
}

const viewHistory = (row: RatingData) => {
  ElMessage.info(`查看 ${row.deviceName} 的历史评级记录`)
}

const getRatingType = (rating: string) => {
  const types: Record<string, string> = {
    'excellent': 'success',
    'good': 'primary',
    'fair': 'warning',
    'poor': 'danger'
  }
  return types[rating] || 'info'
}

const getRatingText = (rating: string) => {
  const texts: Record<string, string> = {
    'excellent': '优秀',
    'good': '良好',
    'fair': '一般',
    'poor': '较差'
  }
  return texts[rating] || rating
}

const getScoreColor = (score: number) => {
  if (score >= 90) return '#67c23a'
  if (score >= 75) return '#409eff'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

onMounted(() => {
  loadData()
})

onUnmounted(() => {
  trendChart?.dispose()
  distributionChart?.dispose()
})
</script>

<style scoped>
.sound-rating-page {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  color: #303133;
}

.page-header p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.rating-overview {
  margin-bottom: 20px;
}

.rating-card {
  cursor: pointer;
  transition: transform 0.3s;
}

.rating-card:hover {
  transform: translateY(-4px);
}

.rating-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.rating-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  border-radius: 12px;
}

.rating-excellent .rating-icon { background: rgba(103, 194, 58, 0.1); color: #67c23a; }
.rating-good .rating-icon { background: rgba(64, 158, 255, 0.1); color: #409eff; }
.rating-fair .rating-icon { background: rgba(230, 162, 60, 0.1); color: #e6a23c; }
.rating-poor .rating-icon { background: rgba(245, 108, 108, 0.1); color: #f56c6c; }

.rating-info {
  flex: 1;
}

.rating-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 4px;
}

.rating-count {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.rating-percent {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.filter-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
