<template>
  <div class="device-manage-page">
      <!-- 页面标题 -->
      <div class="page-header">
        <h2>🔧 设备管理</h2>
        <p>管理物联网设备的基本信息、状态监控和操作控制</p>
      </div>
      
      <!-- 搜索筛选器 -->
      <el-card class="search-card" shadow="never">
        <el-form :inline="true" :model="queryForm">
          <el-form-item label="设备名称">
            <el-input v-model="queryForm.deviceName" placeholder="请输入设备名称" clearable style="width: 200px" />
          </el-form-item>
          
          <el-form-item label="产品">
            <el-select v-model="queryForm.product" placeholder="请选择产品" clearable style="width: 150px">
              <el-option label="RAEM1" value="RAEM1" />
              <el-option label="RAEM2" value="RAEM2" />
            </el-select>
          </el-form-item>
          
          <el-form-item label="状态">
            <el-select v-model="queryForm.status" placeholder="请选择状态" clearable style="width: 120px">
              <el-option label="在线" value="online" />
              <el-option label="离线" value="offline" />
              <el-option label="告警" value="warning" />
            </el-select>
          </el-form-item>
          
          <el-form-item>
            <el-button type="primary" @click="handleSearch" :icon="Search">搜索</el-button>
            <el-button @click="handleReset" :icon="Refresh">重置</el-button>
          </el-form-item>
        </el-form>
        
        <div class="operation-bar">
          <el-button type="primary" @click="handleAdd" :icon="Plus">添加设备</el-button>
          <el-button type="success" @click="handleBatchStart" :disabled="selectedDevices.length === 0">
            批量启动采集
          </el-button>
          <el-button type="warning" @click="handleBatchStop" :disabled="selectedDevices.length === 0">
            批量停止采集
          </el-button>
          <el-button type="danger" @click="handleBatchDelete" :disabled="selectedDevices.length === 0">
            批量删除
          </el-button>
        </div>
      </el-card>
      
      <!-- 设备列表 -->
      <el-card class="device-list-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>设备列表（共{{ deviceData.length }}台）</span>
            <div class="status-stats">
              <el-tag type="success">在线: {{ onlineCount }}</el-tag>
              <el-tag type="info" style="margin-left: 8px">离线: {{ offlineCount }}</el-tag>
              <el-tag type="warning" style="margin-left: 8px">告警: {{ warningCount }}</el-tag>
            </div>
          </div>
        </template>
        
        <el-table 
          :data="deviceData" 
          @selection-change="handleSelectionChange"
          v-loading="loading"
          stripe
          style="width: 100%"
        >
          <el-table-column type="selection" width="55" />
          <el-table-column prop="id" label="设备ID" width="180" />
          <el-table-column prop="name" label="设备名称" width="200" />
          <el-table-column prop="product" label="产品型号" width="120" />
          
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag 
                :type="row.status === 'online' ? 'success' : row.status === 'warning' ? 'warning' : 'info'"
                effect="dark"
              >
                {{ statusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          
          <el-table-column label="采集状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.collecting ? 'success' : 'info'" size="small">
                {{ row.collecting ? '采集中' : '已停止' }}
              </el-tag>
            </template>
          </el-table-column>
          
          <el-table-column prop="version" label="固件版本" width="150" />
          <el-table-column prop="dataCount" label="数据量" width="100" align="right" />
          <el-table-column prop="alertCount" label="告警数" width="100" align="right">
            <template #default="{ row }">
              <el-badge :value="row.alertCount" :max="99" v-if="row.alertCount > 0">
                <span>{{ row.alertCount }}</span>
              </el-badge>
              <span v-else>0</span>
            </template>
          </el-table-column>
          
          <el-table-column prop="lastUpdate" label="最后更新" width="180">
            <template #default="{ row }">
              {{ new Date(row.lastUpdate).toLocaleString() }}
            </template>
          </el-table-column>
          
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="viewDetail(row)">
                详情
              </el-button>
              <el-button size="small" type="success" link @click="handleConfig(row)">
                配置
              </el-button>
              <el-button 
                size="small" 
                :type="row.collecting ? 'warning' : 'success'" 
                link 
                @click="toggleCollecting(row)"
              >
                {{ row.collecting ? '停止' : '启动' }}
              </el-button>
              <el-button size="small" type="info" link @click="handleRestart(row)">
                重启
              </el-button>
              <el-button size="small" type="danger" link @click="handleDelete(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadDeviceData"
          @current-change="loadDeviceData"
          style="margin-top: 20px; justify-content: flex-end"
        />
      </el-card>
      
      <!-- 设备详情对话框 -->
      <el-dialog v-model="detailDialogVisible" title="设备详情" width="800px">
        <el-descriptions :column="2" border v-if="currentDevice">
          <el-descriptions-item label="设备ID">{{ currentDevice.id }}</el-descriptions-item>
          <el-descriptions-item label="设备名称">{{ currentDevice.name }}</el-descriptions-item>
          <el-descriptions-item label="产品型号">{{ currentDevice.product }}</el-descriptions-item>
          <el-descriptions-item label="固件版本">{{ currentDevice.version }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="currentDevice.status === 'online' ? 'success' : 'info'">
              {{ statusText(currentDevice.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="采集状态">
            <el-tag :type="currentDevice.collecting ? 'success' : 'info'">
              {{ currentDevice.collecting ? '采集中' : '已停止' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="数据量">{{ currentDevice.dataCount }}</el-descriptions-item>
          <el-descriptions-item label="告警数">{{ currentDevice.alertCount }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ new Date(currentDevice.createTime).toLocaleString() }}
          </el-descriptions-item>
          <el-descriptions-item label="最后更新">
            {{ new Date(currentDevice.lastUpdate).toLocaleString() }}
          </el-descriptions-item>
        </el-descriptions>
        
        <div class="device-actions" style="margin-top: 20px">
          <el-space>
            <el-button type="primary" @click="handleConfig(currentDevice)">设备配置</el-button>
            <el-button type="success" @click="viewDeviceData">查看数据</el-button>
            <el-button type="info" @click="handleRestart(currentDevice)">重启设备</el-button>
          </el-space>
        </div>
        
        <template #footer>
          <el-button @click="detailDialogVisible = false">关闭</el-button>
        </template>
      </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

defineOptions({ name: 'IotDevice' })

interface Device {
  id: string
  name: string
  product: string
  status: 'online' | 'offline' | 'warning'
  collecting: boolean
  version: string
  dataCount: number
  alertCount: number
  lastUpdate: number
  createTime: number
}

const loading = ref(false)
const selectedDevices = ref<Device[]>([])
const detailDialogVisible = ref(false)
const currentDevice = ref<Device | null>(null)

const queryForm = reactive({
  deviceName: '',
  product: '',
  status: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

// 模拟设备数据
const deviceData = ref<Device[]>([])

// 统计
const onlineCount = computed(() => deviceData.value.filter(d => d.status === 'online').length)
const offlineCount = computed(() => deviceData.value.filter(d => d.status === 'offline').length)
const warningCount = computed(() => deviceData.value.filter(d => d.status === 'warning').length)

// 生成模拟设备数据
const generateMockDevices = (): Device[] => {
  const devices: Device[] = []
  const now = Date.now()
  
  for (let i = 1; i <= 50; i++) {
    const statusRand = Math.random()
    let status: 'online' | 'offline' | 'warning'
    if (statusRand > 0.8) status = 'offline'
    else if (statusRand > 0.7) status = 'warning'
    else status = 'online'
    
    devices.push({
      id: `qc_raem1_4g_${100 + i}`,
      name: `RAEM设备_${i}`,
      product: i % 3 === 0 ? 'RAEM2' : 'RAEM1',
      status: status,
      collecting: status === 'online' && Math.random() > 0.3,
      version: `V1.0.${50 + i}`,
      dataCount: Math.floor(Math.random() * 50000) + 1000,
      alertCount: status === 'warning' ? Math.floor(Math.random() * 10) + 1 : 0,
      lastUpdate: now - Math.floor(Math.random() * 3600000),
      createTime: now - Math.floor(Math.random() * 86400000 * 30)
    })
  }
  
  return devices
}

// 加载设备数据
const loadDeviceData = () => {
  loading.value = true
  
  setTimeout(() => {
    let allDevices = generateMockDevices()
    
    // 筛选
    if (queryForm.deviceName) {
      allDevices = allDevices.filter(d => d.name.includes(queryForm.deviceName))
    }
    if (queryForm.product) {
      allDevices = allDevices.filter(d => d.product === queryForm.product)
    }
    if (queryForm.status) {
      allDevices = allDevices.filter(d => d.status === queryForm.status)
    }
    
    // 分页
    pagination.total = allDevices.length
    const start = (pagination.page - 1) * pagination.pageSize
    deviceData.value = allDevices.slice(start, start + pagination.pageSize)
    
    loading.value = false
  }, 500)
}

// 事件处理
const handleSearch = () => {
  pagination.page = 1
  loadDeviceData()
}

const handleReset = () => {
  queryForm.deviceName = ''
  queryForm.product = ''
  queryForm.status = ''
  handleSearch()
}

const handleSelectionChange = (devices: Device[]) => {
  selectedDevices.value = devices
}

const handleAdd = () => {
  ElMessage.info('添加设备功能开发中...')
}

const handleBatchStart = () => {
  ElMessageBox.confirm(
    `确定要启动 ${selectedDevices.value.length} 台设备的数据采集吗？`,
    '批量启动',
    { type: 'success' }
  ).then(() => {
    ElMessage.success('已启动数据采集')
    loadDeviceData()
  })
}

const handleBatchStop = () => {
  ElMessageBox.confirm(
    `确定要停止 ${selectedDevices.value.length} 台设备的数据采集吗？`,
    '批量停止',
    { type: 'warning' }
  ).then(() => {
    ElMessage.success('已停止数据采集')
    loadDeviceData()
  })
}

const handleBatchDelete = () => {
  ElMessageBox.confirm(
    `确定要删除 ${selectedDevices.value.length} 台设备吗？删除后数据将无法恢复！`,
    '批量删除',
    { type: 'error', confirmButtonText: '确定删除', cancelButtonText: '取消' }
  ).then(() => {
    ElMessage.success('设备已删除')
    selectedDevices.value = []
    loadDeviceData()
  })
}

const viewDetail = (device: Device) => {
  currentDevice.value = device
  detailDialogVisible.value = true
}

const handleConfig = (device: Device) => {
  ElMessage.info(`配置设备: ${device.name}`)
  // TODO: 打开设备配置对话框
}

const toggleCollecting = (device: Device) => {
  const action = device.collecting ? '停止' : '启动'
  ElMessageBox.confirm(
    `确定要${action}设备 ${device.name} 的数据采集吗？`,
    `${action}采集`,
    { type: 'warning' }
  ).then(() => {
    device.collecting = !device.collecting
    ElMessage.success(`已${action}数据采集`)
  })
}

const handleRestart = (device: Device) => {
  ElMessageBox.confirm(
    `确定要重启设备 ${device.name} 吗？重启过程约需30秒。`,
    '重启设备',
    { type: 'warning' }
  ).then(() => {
    ElMessage.success('设备重启命令已发送')
    // TODO: 调用重启API
  })
}

const handleDelete = (device: Device) => {
  ElMessageBox.confirm(
    `确定要删除设备 ${device.name} 吗？删除后数据将无法恢复！`,
    '删除设备',
    { type: 'error', confirmButtonText: '确定删除' }
  ).then(() => {
    ElMessage.success('设备已删除')
    loadDeviceData()
  })
}

const viewDeviceData = () => {
  ElMessage.info('跳转到设备数据页面...')
  // TODO: 跳转到声发射数据页面
}

const statusText = (status: string) => {
  const texts: Record<string, string> = {
    'online': '在线',
    'offline': '离线',
    'warning': '告警'
  }
  return texts[status] || status
}

onMounted(() => {
  loadDeviceData()
})
</script>

<style scoped>
.device-manage-page {
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

.search-card {
  margin-bottom: 16px;
}

.operation-bar {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e4e7ed;
}

.device-list-card {
  margin-top: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-stats {
  display: flex;
  gap: 8px;
}

.device-actions {
  padding: 20px;
  background: #f5f7fa;
  border-radius: 4px;
}
</style>

