<template>
  <div class="app-container">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="告警名称" prop="sceneName">
          <el-input
            v-model="queryParams.sceneName"
            placeholder="请输入告警名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="类型" prop="sceneType">
          <el-select v-model="queryParams.sceneType" placeholder="请选择类型" clearable>
            <el-option label="强度" value="intensity" />
            <el-option label="温度" value="temperature" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="门户" prop="gatewayId">
          <el-select v-model="queryParams.gatewayId" placeholder="请选择门户" clearable>
            <el-option
              v-for="item in gatewayList"
              :key="item.id"
              :label="`${item.name} (${item.location})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 操作按钮 -->
      <el-row :gutter="10" class="mb-2">
        <el-col :span="1.5">
          <el-button type="primary" plain @click="handleAdd">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain @click="handleImport">导入</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain :disabled="multiple" @click="handleDelete">删除</el-button>
        </el-col>
      </el-row>

      <!-- 数据表格 -->
      <el-table
        v-loading="loading"
        :data="sceneList"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="告警名称" prop="sceneName" />
        <el-table-column label="类型" prop="sceneType">
          <template #default="scope">
            <el-tag v-if="scope.row.sceneType === 'intensity'" type="danger">强度</el-tag>
            <el-tag v-else-if="scope.row.sceneType === 'temperature'" type="warning">温度</el-tag>
            <el-tag v-else type="info">其他</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="门户" prop="gatewayName">
          <template #default="scope">
            {{ scope.row.gatewayName || '-' }} <br />
            <span style="color: #999; font-size: 12px">{{ scope.row.gatewayLocation || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="告警等级" prop="alertLevel" align="center">
          <template #default="scope">
            {{ scope.row.alertLevel || '-' }} 级
          </template>
        </el-table-column>
        <el-table-column label="触发时间(s)" prop="triggerDuration" align="center" />
        <el-table-column label="通知方式" prop="notifyMethod">
          <template #default="scope">
            <el-tag v-if="scope.row.notifyMethod?.includes('email')" size="small" class="mr-1">邮件</el-tag>
            <el-tag v-if="scope.row.notifyMethod?.includes('sms')" size="small" type="success">短信</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.status === 1" type="success">启用</el-tag>
            <el-tag v-else type="info">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" />
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button link type="primary" @click="handleCopy(scope.row)">复制</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="800px"
      @close="handleDialogClose"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-tabs v-model="activeTab">
          <!-- 基本信息 -->
          <el-tab-pane label="基本信息" name="basic">
            <el-form-item label="告警名称" prop="sceneName">
              <el-input v-model="form.sceneName" placeholder="请输入告警名称" />
            </el-form-item>

            <el-form-item label="类型" prop="sceneType">
              <el-select v-model="form.sceneType" placeholder="请选择类型" style="width: 100%">
                <el-option label="强度" value="intensity" />
                <el-option label="温度" value="temperature" />
                <el-option label="其他" value="other" />
              </el-select>
            </el-form-item>

            <el-form-item label="门户" prop="gatewayId">
              <el-select v-model="form.gatewayId" placeholder="请选择门户" style="width: 100%">
                <el-option
                  v-for="item in gatewayList"
                  :key="item.id"
                  :label="`${item.name} (${item.location})`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="告警等级" prop="alertLevel">
              <el-input-number v-model="form.alertLevel" :min="1" :max="5" />
              <span class="ml-2 text-sm text-gray-500">（1-5级，数字越大级别越高）</span>
            </el-form-item>

            <el-form-item label="触发时间(s)" prop="triggerDuration">
              <el-input-number v-model="form.triggerDuration" :min="1" :max="999" />
              <span class="ml-2 text-sm text-gray-500">（统计该时间内采集的数据）</span>
            </el-form-item>

            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :label="1">启用</el-radio>
                <el-radio :label="0">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-tab-pane>

          <!-- 触发规则 -->
          <el-tab-pane label="触发规则" name="rules">
            <el-form-item label="评级类型">
              <el-radio-group v-model="form.ratingType">
                <el-radio label="auto">自动评级</el-radio>
                <el-radio label="manual">自定义规则</el-radio>
              </el-radio-group>
            </el-form-item>

            <div v-if="form.ratingType === 'manual'">
              <el-form-item
                v-for="(rule, index) in form.rules"
                :key="index"
                :label="`强度${index + 1}`"
              >
                <el-row :gutter="10">
                  <el-col :span="6">
                    <el-checkbox v-model="rule.enabled">启用强度</el-checkbox>
                  </el-col>
                  <el-col :span="6">
                    <el-select v-model="rule.parameter" placeholder="参数">
                      <el-option label="幅度 (dB)" value="amplitude" />
                      <el-option label="能量 (Kcal)" value="energy" />
                      <el-option label="RMS (mV)" value="rms" />
                    </el-select>
                  </el-col>
                  <el-col :span="6">
                    <el-select v-model="rule.condition" placeholder="条件">
                      <el-option label="大于" value="gt" />
                      <el-option label="小于" value="lt" />
                      <el-option label="等于" value="eq" />
                      <el-option label="大于等于" value="gte" />
                      <el-option label="小于等于" value="lte" />
                    </el-select>
                  </el-col>
                  <el-col :span="6">
                    <el-input-number
                      v-model="rule.threshold"
                      placeholder="阈值"
                      style="width: 100%"
                    />
                  </el-col>
                </el-row>
              </el-form-item>

              <el-form-item>
                <el-button type="primary" plain size="small" @click="addRule">新增强度</el-button>
              </el-form-item>
            </div>

            <el-form-item label="评估规则">
              <el-select v-model="form.evaluationRule" placeholder="选择评估规则" style="width: 100%">
                <el-option label="满足任一条件" value="any" />
                <el-option label="满足所有条件" value="all" />
              </el-select>
            </el-form-item>

            <el-form-item label="统计时长(s)">
              <el-input-number v-model="form.statisticsDuration" :min="1" :max="3600" />
            </el-form-item>

            <el-form-item label="评估阈值上限">
              <el-select v-model="form.thresholdType" style="width: 100%">
                <el-option label="不上限" value="unlimited" />
                <el-option label="上限强度1" value="level1" />
                <el-option label="上限强度2" value="level2" />
                <el-option label="上限强度3" value="level3" />
              </el-select>
            </el-form-item>

            <el-form-item label="宝马规则上限阈值(s)">
              <el-input-number v-model="form.bmwThreshold" :min="1" :max="999" />
            </el-form-item>
          </el-tab-pane>

          <!-- 通知设置 -->
          <el-tab-pane label="通知设置" name="notify">
            <el-form-item label="通知方式">
              <el-checkbox-group v-model="form.notifyMethod">
                <el-checkbox label="email">邮件</el-checkbox>
                <el-checkbox label="sms">短信</el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="通知对象">
              <el-select
                v-model="form.notifyUsers"
                multiple
                placeholder="请选择通知用户"
                style="width: 100%"
              >
                <el-option
                  v-for="user in alertUserList"
                  :key="user.id"
                  :label="user.contactName"
                  :value="user.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="备注">
              <el-input
                v-model="form.remark"
                type="textarea"
                :rows="3"
                placeholder="请输入备注"
              />
            </el-form-item>
          </el-tab-pane>
        </el-tabs>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getAlertSceneList,
  createAlertScene,
  updateAlertScene,
  deleteAlertScene
} from '@/api/iot/alert'
import { getAlertUserList } from '@/api/iot/alert'

defineOptions({ name: 'IotAlertScene' })

const loading = ref(false)
const sceneList = ref<any[]>([])
const alertUserList = ref<any[]>([])
const gatewayList = ref<any[]>([
  { id: 1, name: '消化炉关键（广州）', location: '研究公司' },
  { id: 2, name: 'RAEM1', location: 'RAEM1' }
])

const total = ref(0)
const multiple = ref(true)
const ids = ref<number[]>([])

const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  sceneName: '',
  sceneType: '',
  gatewayId: undefined
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const activeTab = ref('basic')
const formRef = ref<FormInstance>()

const form = reactive({
  id: undefined,
  sceneName: '',
  sceneType: '',
  gatewayId: undefined,
  alertLevel: 2,
  triggerDuration: 20,
  status: 1,
  ratingType: 'auto',
  rules: [
    { enabled: false, parameter: 'amplitude', condition: 'gt', threshold: 40 },
    { enabled: false, parameter: 'energy', condition: 'gt', threshold: 50 },
    { enabled: false, parameter: 'rms', condition: 'gt', threshold: 60 }
  ],
  evaluationRule: 'any',
  statisticsDuration: 20,
  thresholdType: 'unlimited',
  bmwThreshold: 1,
  notifyMethod: ['email'],
  notifyUsers: [],
  remark: ''
})

const rules = reactive<FormRules>({
  sceneName: [{ required: true, message: '请输入告警名称', trigger: 'blur' }],
  sceneType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  gatewayId: [{ required: true, message: '请选择门户', trigger: 'change' }],
  alertLevel: [{ required: true, message: '请输入告警等级', trigger: 'blur' }]
})

// 查询列表
const handleQuery = async () => {
  loading.value = true
  try {
    const res = await getAlertSceneList(queryParams)
    sceneList.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取告警场景列表失败', error)
    // 使用Mock数据
    sceneList.value = [
      {
        id: 1,
        sceneName: '10级超压',
        sceneType: 'intensity',
        gatewayId: 1,
        gatewayName: '消化炉关键（广州）',
        gatewayLocation: '研究公司',
        alertLevel: 2,
        triggerDuration: 20,
        status: 1,
        notifyMethod: ['email'],
        createTime: '2023-12-25 10:00:00'
      }
    ]
    total.value = 1
    ElMessage.warning('后端API未实现，当前显示模拟数据')
  } finally {
    loading.value = false
  }
}

// 获取告警用户列表
const getAlertUsers = async () => {
  try {
    const res = await getAlertUserList({ pageNo: 1, pageSize: 100 })
    alertUserList.value = res.data?.list || []
  } catch (error) {
    console.error('获取告警用户列表失败', error)
  }
}

// 重置查询
const resetQuery = () => {
  queryParams.sceneName = ''
  queryParams.sceneType = ''
  queryParams.gatewayId = undefined
  queryParams.pageNo = 1
  handleQuery()
}

// 新增
const handleAdd = () => {
  dialogTitle.value = '新增告警场景'
  activeTab.value = 'basic'
  resetForm()
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: any) => {
  dialogTitle.value = '编辑告警场景'
  activeTab.value = 'basic'
  Object.assign(form, JSON.parse(JSON.stringify(row)))
  dialogVisible.value = true
}

// 复制
const handleCopy = (row: any) => {
  dialogTitle.value = '复制告警场景'
  activeTab.value = 'basic'
  Object.assign(form, JSON.parse(JSON.stringify(row)))
  form.id = undefined
  form.sceneName = `${row.sceneName}_副本`
  dialogVisible.value = true
}

// 删除
const handleDelete = async (row?: any) => {
  const deleteIds = row ? [row.id] : ids.value

  await ElMessageBox.confirm('确定要删除选中的告警场景吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })

  try {
    await deleteAlertScene(deleteIds)
    ElMessage.success('删除成功')
    handleQuery()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

// 导入
const handleImport = () => {
  ElMessage.info('导入功能开发中...')
}

// 表格选择
const handleSelectionChange = (selection: any[]) => {
  ids.value = selection.map((item) => item.id)
  multiple.value = !selection.length
}

// 添加规则
const addRule = () => {
  form.rules.push({ enabled: false, parameter: 'amplitude', condition: 'gt', threshold: 0 })
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate()

  try {
    if (form.id) {
      await updateAlertScene(form)
      ElMessage.success('更新成功')
    } else {
      await createAlertScene(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    handleQuery()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 重置表单
const resetForm = () => {
  form.id = undefined
  form.sceneName = ''
  form.sceneType = ''
  form.gatewayId = undefined
  form.alertLevel = 2
  form.triggerDuration = 20
  form.status = 1
  form.ratingType = 'auto'
  form.rules = [
    { enabled: false, parameter: 'amplitude', condition: 'gt', threshold: 40 },
    { enabled: false, parameter: 'energy', condition: 'gt', threshold: 50 },
    { enabled: false, parameter: 'rms', condition: 'gt', threshold: 60 }
  ]
  form.evaluationRule = 'any'
  form.statisticsDuration = 20
  form.thresholdType = 'unlimited'
  form.bmwThreshold = 1
  form.notifyMethod = ['email']
  form.notifyUsers = []
  form.remark = ''
}

// 对话框关闭
const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetForm()
}

onMounted(() => {
  handleQuery()
  getAlertUsers()
})
</script>

<style scoped lang="scss">
.app-container {
  padding: 20px;
}

.mb-2 {
  margin-bottom: 12px;
}

.mr-1 {
  margin-right: 4px;
}

.ml-2 {
  margin-left: 8px;
}

.text-sm {
  font-size: 12px;
}

.text-gray-500 {
  color: #999;
}
</style>

