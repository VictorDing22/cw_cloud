<template>
  <ContentWrap title="系统状态">
    <el-row :gutter="20">
      <!-- 服务状态卡片 -->
      <el-col :span="6" v-for="service in services" :key="service.name">
        <el-card shadow="hover" :body-style="{ padding: '20px' }">
          <div class="service-card">
            <div class="service-icon" :class="service.status === 'running' ? 'running' : 'stopped'">
              <el-icon :size="40">
                <component :is="service.icon" />
              </el-icon>
            </div>
            <div class="service-info">
              <div class="service-name">{{ service.name }}</div>
              <div class="service-port">端口: {{ service.port }}</div>
              <el-tag :type="service.status === 'running' ? 'success' : 'danger'" size="small">
                {{ service.status === 'running' ? '运行中' : '未运行' }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 系统资源监控 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>CPU使用率</template>
          <div id="cpu-chart" style="height: 200px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>内存使用率</template>
          <div id="memory-chart" style="height: 200px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>网络流量</template>
          <div id="network-chart" style="height: 200px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 详细信息 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span>服务详细信息</span>
              <el-button type="primary" :icon="'Refresh'" @click="refreshStatus">刷新</el-button>
            </div>
          </template>
          <el-table :data="services" style="width: 100%">
            <el-table-column prop="name" label="服务名称" width="180" />
            <el-table-column prop="port" label="端口" width="100" />
            <el-table-column prop="status" label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="row.status === 'running' ? 'success' : 'danger'">
                  {{ row.status === 'running' ? '运行中' : '未运行' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="uptime" label="运行时长" width="150" />
            <el-table-column prop="memory" label="内存占用" width="120" />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { Monitor, Cpu, MemoryCard, Connection } from '@element-plus/icons-vue'

const services = ref([
  { name: 'MySQL', port: '3306', status: 'running', icon: 'DataBoard', uptime: '10天 5小时', memory: '512 MB', description: '数据库服务' },
  { name: 'Redis', port: '6379', status: 'running', icon: 'Coin', uptime: '10天 5小时', memory: '128 MB', description: '缓存服务' },
  { name: 'Nacos', port: '8848', status: 'running', icon: 'Connection', uptime: '2天 3小时', memory: '1.2 GB', description: '服务注册中心' },
  { name: 'Kafka', port: '9092', status: 'stopped', icon: 'Guide', uptime: '-', memory: '-', description: '消息队列' },
  { name: 'Gateway', port: '48080', status: 'running', icon: 'Platform', uptime: '2天 3小时', memory: '256 MB', description: 'API网关' },
  { name: 'System', port: '48081', status: 'running', icon: 'Setting', uptime: '2天 3小时', memory: '180 MB', description: '系统服务' },
  { name: 'IoT', port: '48083', status: 'running', icon: 'Cpu', uptime: '1天 12小时', memory: '220 MB', description: 'IoT设备管理' },
  { name: 'backend.jar', port: '8080', status: 'stopped', icon: 'Monitor', uptime: '-', memory: '-', description: '实时滤波服务' }
])

let cpuChart: echarts.ECharts | null = null
let memoryChart: echarts.ECharts | null = null
let networkChart: echarts.ECharts | null = null

// 刷新状态
const refreshStatus = async () => {
  ElMessage.success('状态已刷新')
  // TODO: 调用后端API获取真实状态
}

// 初始化图表
const initCharts = () => {
  // CPU使用率仪表盘
  const cpuEl = document.getElementById('cpu-chart')
  if (cpuEl) {
    cpuChart = echarts.init(cpuEl)
    cpuChart.setOption({
      series: [
        {
          type: 'gauge',
          startAngle: 180,
          endAngle: 0,
          min: 0,
          max: 100,
          splitNumber: 10,
          axisLine: {
            lineStyle: {
              width: 6,
              color: [
                [0.3, '#67C23A'],
                [0.7, '#E6A23C'],
                [1, '#F56C6C']
              ]
            }
          },
          pointer: {
            icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z',
            length: '12%',
            width: 20,
            offsetCenter: [0, '-60%'],
            itemStyle: {
              color: 'auto'
            }
          },
          axisTick: {
            length: 12,
            lineStyle: {
              color: 'auto',
              width: 2
            }
          },
          splitLine: {
            length: 20,
            lineStyle: {
              color: 'auto',
              width: 5
            }
          },
          axisLabel: {
            color: '#464646',
            fontSize: 12,
            distance: -60,
            formatter: (value) => {
              return value + '%'
            }
          },
          detail: {
            fontSize: 24,
            offsetCenter: [0, '0%'],
            valueAnimation: true,
            formatter: (value) => {
              return value.toFixed(1) + '%'
            },
            color: 'auto'
          },
          data: [{ value: 35.5, name: 'CPU' }]
        }
      ]
    })
  }

  // 内存使用率仪表盘
  const memoryEl = document.getElementById('memory-chart')
  if (memoryEl) {
    memoryChart = echarts.init(memoryEl)
    memoryChart.setOption({
      series: [
        {
          type: 'gauge',
          startAngle: 180,
          endAngle: 0,
          min: 0,
          max: 16,
          splitNumber: 8,
          axisLine: {
            lineStyle: {
              width: 6,
              color: [
                [0.5, '#67C23A'],
                [0.8, '#E6A23C'],
                [1, '#F56C6C']
              ]
            }
          },
          pointer: {
            icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z',
            length: '12%',
            width: 20,
            offsetCenter: [0, '-60%'],
            itemStyle: {
              color: 'auto'
            }
          },
          axisTick: {
            length: 12,
            lineStyle: {
              color: 'auto',
              width: 2
            }
          },
          splitLine: {
            length: 20,
            lineStyle: {
              color: 'auto',
              width: 5
            }
          },
          axisLabel: {
            color: '#464646',
            fontSize: 12,
            distance: -60,
            formatter: (value) => {
              return value + 'GB'
            }
          },
          detail: {
            fontSize: 24,
            offsetCenter: [0, '0%'],
            valueAnimation: true,
            formatter: (value) => {
              return value.toFixed(1) + ' GB'
            },
            color: 'auto'
          },
          data: [{ value: 8.2, name: '内存' }]
        }
      ]
    })
  }

  // 网络流量折线图
  const networkEl = document.getElementById('network-chart')
  if (networkEl) {
    networkChart = echarts.init(networkEl)
    networkChart.setOption({
      tooltip: {
        trigger: 'axis'
      },
      xAxis: {
        type: 'category',
        data: ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00']
      },
      yAxis: {
        type: 'value',
        name: 'MB/s'
      },
      series: [
        {
          name: '上行',
          type: 'line',
          smooth: true,
          data: [12, 15, 20, 25, 18, 22],
          itemStyle: { color: '#409EFF' }
        },
        {
          name: '下行',
          type: 'line',
          smooth: true,
          data: [25, 30, 35, 40, 32, 38],
          itemStyle: { color: '#67C23A' }
        }
      ]
    })
  }
}

onMounted(async () => {
  await nextTick()
  initCharts()

  window.addEventListener('resize', () => {
    cpuChart?.resize()
    memoryChart?.resize()
    networkChart?.resize()
  })
})
</script>

<style scoped lang="scss">
.service-card {
  display: flex;
  align-items: center;
  gap: 15px;

  .service-icon {
    width: 60px;
    height: 60px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;

    &.running {
      background-color: #f0f9ff;
      color: #409EFF;
    }

    &.stopped {
      background-color: #fef0f0;
      color: #F56C6C;
    }
  }

  .service-info {
    flex: 1;

    .service-name {
      font-size: 16px;
      font-weight: bold;
      margin-bottom: 5px;
    }

    .service-port {
      font-size: 12px;
      color: #909399;
      margin-bottom: 5px;
    }
  }
}
</style>
