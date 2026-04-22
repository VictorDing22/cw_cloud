<template>
  <div class="app-container">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="告警场景" prop="sceneId">
          <el-select v-model="queryParams.sceneId" placeholder="请选择告警场景" clearable>
            <el-option
              v-for="item in sceneList"
              :key="item.id"
              :label="item.sceneName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="设备" prop="deviceKey">
          <el-select v-model="queryParams.deviceKey" placeholder="请选择设备" clearable filterable>
            <el-option
              v-for="item in deviceList"
              :key="item.deviceKey"
              :label="`${item.deviceName} (${item.deviceKey})`"
              :value="item.deviceKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="告警级别" prop="alertLevel">
          <el-select v-model="queryParams.alertLevel" placeholder="请选择级别" clearable>
            <el-option label="1级" :value="1" />
            <el-option label="2级" :value="2" />
            <el-option label="3级" :value="3" />
            <el-option label="4级" :value="4" />
            <el-option label="5级" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理状态" prop="handleStatus">
          <el-select v-model="queryParams.handleStatus" placeholder="请选择状态" clearable>
            <el-option label="未处理" :value="0" />
            <el-option label="处理中" :value="1" />
            <el-option label="已处理" :value="2" />
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
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 操作按钮 -->
      <el-row :gutter="10" class="mb-2">
        <el-col :span="1.5">
          <el-button type="success" plain @click="handleExport">导出</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain :disabled="multiple" @click="handleDelete">删除</el-button>
        </el-col>
      </el-row>

      <!-- 数据表格 -->
      <el-table
        v-loading="loading"
        :data="logList"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="告警场景" prop="sceneName" show-overflow-tooltip />
        <el-table-column label="设备信息" prop="deviceName">
          <template #default="scope">
            {{ scope.row.deviceName || '-' }} <br />
            <span style="color: #999; font-size: 12px">{{ scope.row.deviceKey || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="告警级别" prop="alertLevel" width="100" align="center">
          <template #default="scope">
            <el-tag
              :type="getLevelType(scope.row.alertLevel)"
              effect="dark"
            >
              {{ scope.row.alertLevel }} 级
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="告警参数" prop="alertParams" show-overflow-tooltip>
          <template #default="scope">
            {{ formatAlertParams(scope.row.alertParams) }}
          </template>
        </el-table-column>
        <el-table-column label="阈值" prop="threshold" align="center" />
        <el-table-column label="实际值" prop="actualValue" align="center" />
        <el-table-column label="处理状态" prop="handleStatus" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.handleStatus === 0" type="danger">未处理</el-tag>
            <el-tag v-else-if="scope.row.handleStatus === 1" type="warning">处理中</el-tag>
            <el-tag v-else type="success">已处理</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发时间" prop="createTime" width="180" />
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleView(scope.row)">详情</el-button>
            <el-button
              v-if="scope.row.handleStatus !== 2"
              link
              type="success"
              @click="handleProcess(scope.row)"
            >
              处理
            </el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="handleQuery"
      />
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="告警日志详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="告警场景">{{ currentLog.sceneName }}</el-descriptions-item>
        <el-descriptions-item label="告警级别">
          <el-tag :type="getLevelType(currentLog.alertLevel)" effect="dark">
            {{ currentLog.alertLevel }} 级
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="设备名称">{{ currentLog.deviceName }}</el-descriptions-item>
        <el-descriptions-item label="设备标识">{{ currentLog.deviceKey }}</el-descriptions-item>
        <el-descriptions-item label="告警参数" :span="2">
          {{ formatAlertParams(currentLog.alertParams) }}
        </el-descriptions-item>
        <el-descriptions-item label="阈值">{{ currentLog.threshold }}</el-descriptions-item>
        <el-descriptions-item label="实际值">{{ currentLog.actualValue }}</el-descriptions-item>
        <el-descriptions-item label="触发时间" :span="2">
          {{ currentLog.createTime }}
        </el-descriptions-item>
        <el-descriptions-item label="处理状态">
          <el-tag v-if="currentLog.handleStatus === 0" type="danger">未处理</el-tag>
          <el-tag v-else-if="currentLog.handleStatus === 1" type="warning">处理中</el-tag>
          <el-tag v-else type="success">已处理</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="处理人" v-if="currentLog.handleUser">
          {{ currentLog.handleUser }}
        </el-descriptions-item>
        <el-descriptions-item label="处理时间" :span="2" v-if="currentLog.handleTime">
          {{ currentLog.handleTime }}
        </el-descriptions-item>
        <el-descriptions-item label="处理备注" :span="2" v-if="currentLog.handleRemark">
          {{ currentLog.handleRemark }}
        </el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 处理对话框 -->
    <el-dialog v-model="processVisible" title="处理告警" width="500px">
      <el-form ref="formRef" :model="processForm" :rules="processRules" label-width="100px">
        <el-form-item label="处理状态" prop="handleStatus">
          <el-radio-group v-model="processForm.handleStatus">
            <el-radio :label="1">处理中</el-radio>
            <el-radio :label="2">已处理</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理备注" prop="handleRemark">
          <el-input
            v-model="processForm.handleRemark"
            type="textarea"
            :rows="4"
            placeholder="请输入处理备注"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="processVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitProcess">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getAlertLogList,
  processAlertLog,
  deleteAlertLog,
  exportAlertLog
} from '@/api/iot/alert'
import { getAlertSceneList } from '@/api/iot/alert'
import { getDeviceList } from '@/api/iot/device'

defineOptions({ name: 'IotAlertLog' })

const loading = ref(false)
const logList = ref<any[]>([])
const sceneList = ref<any[]>([])
const deviceList = ref<any[]>([])
const total = ref(0)
const multiple = ref(true)
const ids = ref<number[]>([])

const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  sceneId: undefined,
  deviceKey: '',
  alertLevel: undefined,
  handleStatus: undefined,
  dateRange: []
})

const detailVisible = ref(false)
const processVisible = ref(false)
const currentLog = ref<any>({})
const formRef = ref<FormInstance>()

const processForm = reactive({
  id: undefined,
  handleStatus: 2,
  handleRemark: ''
})

const processRules = reactive<FormRules>({
  handleStatus: [{ required: true, message: '请选择处理状态', trigger: 'change' }],
  handleRemark: [{ required: true, message: '请输入处理备注', trigger: 'blur' }]
})

// 获取告警场景列表
const getSceneList = async () => {
  try {
    const res = await getAlertSceneList({ pageNo: 1, pageSize: 100 })
    sceneList.value = res.data?.list || []
  } catch (error) {
    console.error('获取告警场景列表失败', error)
    // 使用Mock数据
    sceneList.value = [
      { id: 1, sceneName: '10级超压' },
      { id: 2, sceneName: '11级超压' }
    ]
  }
}

// 获取设备列表
const getDevices = async () => {
  try {
    const res = await getDeviceList({ pageNo: 1, pageSize: 100 })
    deviceList.value = res.data?.list || []
  } catch (error) {
    console.error('获取设备列表失败', error)
    // 使用Mock数据
    deviceList.value = [
      { deviceKey: 'JF_RAEM1_WP1_03', deviceName: 'JF_RAEM1_WP1_03' }
    ]
  }
}

// 查询列表
const handleQuery = async () => {
  loading.value = true
  try {
    const res = await getAlertLogList(queryParams)
    logList.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取告警日志列表失败', error)
    // 使用Mock数据（后端API未实现时）
    logList.value = [
      {
        id: 1,
        sceneId: 1,
        sceneName: '10级超压',
        deviceKey: 'JF_RAEM1_WP1_03',
        deviceName: 'JF_RAEM1_WP1_03',
        alertLevel: 1,
        alertParams: { amplitude: 73.358, energy: 74.073, rms: 0.831 },
        threshold: 40,
        actualValue: 73.358,
        handleStatus: 0,
        createTime: '2023-12-25 13:51:47.476'
      }
    ]
    total.value = 1
    ElMessage.warning('后端API未实现，当前显示模拟数据')
  } finally {
    loading.value = false
  }
}

// 重置查询
const resetQuery = () => {
  queryParams.sceneId = undefined
  queryParams.deviceKey = ''
  queryParams.alertLevel = undefined
  queryParams.handleStatus = undefined
  queryParams.dateRange = []
  queryParams.pageNo = 1
  handleQuery()
}

// 查看详情
const handleView = (row: any) => {
  currentLog.value = { ...row }
  detailVisible.value = true
}

// 处理告警
const handleProcess = (row: any) => {
  processForm.id = row.id
  processForm.handleStatus = 2
  processForm.handleRemark = ''
  processVisible.value = true
}

// 提交处理
const handleSubmitProcess = async () => {
  if (!formRef.value) return

  await formRef.value.validate()

  try {
    await processAlertLog(processForm)
    ElMessage.success('处理成功')
    processVisible.value = false
    handleQuery()
  } catch (error) {
    ElMessage.error('处理失败')
  }
}

// 导出
const handleExport = async () => {
  try {
    await exportAlertLog(queryParams)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

// 删除
const handleDelete = async (row?: any) => {
  const deleteIds = row ? [row.id] : ids.value

  await ElMessageBox.confirm('确定要删除选中的告警日志吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })

  try {
    await deleteAlertLog(deleteIds)
    ElMessage.success('删除成功')
    handleQuery()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

// 表格选择
const handleSelectionChange = (selection: any[]) => {
  ids.value = selection.map((item) => item.id)
  multiple.value = !selection.length
}

// 获取级别类型
const getLevelType = (level: number) => {
  if (level >= 4) return 'danger'
  if (level >= 3) return 'warning'
  return 'success'
}

// 格式化告警参数
const formatAlertParams = (params: any) => {
  if (!params) return '-'
  if (typeof params === 'string') return params
  
  const labels = {
    amplitude: '幅度',
    energy: '能量',
    rms: 'RMS'
  }
  
  return Object.keys(params)
    .map((key) => `${labels[key] || key}: ${params[key]}`)
    .join(', ')
}

onMounted(() => {
  getSceneList()
  getDevices()
  handleQuery()
})
</script>

<style scoped lang="scss">
.app-container {
  padding: 20px;
}

.mb-2 {
  margin-bottom: 12px;
}
</style>

