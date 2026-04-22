<template>
  <div class="app-container">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="联系人" prop="contactName">
          <el-input
            v-model="queryParams.contactName"
            placeholder="请输入联系人"
            clearable
            @keyup.enter="handleQuery"
          />
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
        :data="userList"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="联系名称" prop="contactName" />
        <el-table-column label="类型" prop="contactType">
          <template #default="scope">
            <el-tag v-if="scope.row.contactType === 'email'">邮件</el-tag>
            <el-tag v-else-if="scope.row.contactType === 'phone'" type="success">短信</el-tag>
            <el-tag v-else type="info">其他</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="门户" prop="gatewayName">
          <template #default="scope">
            {{ scope.row.gatewayName || '-' }} <br />
            <span style="color: #999; font-size: 12px">{{ scope.row.gatewayLocation || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="使用语言" prop="language">
          <template #default="scope">
            <el-tag v-if="scope.row.language === 'zh-CN'" type="success">简体中文</el-tag>
            <el-tag v-else-if="scope.row.language === 'en-US'" type="primary">English</el-tag>
            <el-tag v-else>{{ scope.row.language }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="接收器数(min)" prop="receiverCount" align="center" />
        <el-table-column label="状态" prop="status" align="center">
          <template #default="scope">
            <el-switch
              v-model="scope.row.status"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" />
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button link type="primary" @click="handleViewDetails(scope.row)">详情</el-button>
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
      width="600px"
      @close="handleDialogClose"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="form.contactName" placeholder="请输入联系人" />
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

        <el-form-item label="使用语言" prop="language">
          <el-select v-model="form.language" placeholder="请选择语言" style="width: 100%">
            <el-option label="简体中文" value="zh-CN" />
            <el-option label="English" value="en-US" />
          </el-select>
        </el-form-item>

        <el-form-item label="手机" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>

        <el-form-item label="接收器数(min)" prop="receiverCount">
          <el-input-number v-model="form.receiverCount" :min="1" :max="999" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getAlertUserList,
  createAlertUser,
  updateAlertUser,
  deleteAlertUser,
  updateAlertUserStatus
} from '@/api/iot/alert'

defineOptions({ name: 'IotAlertUser' })

const loading = ref(false)
const userList = ref<any[]>([])
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
  contactName: '',
  gatewayId: undefined
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const form = reactive({
  id: undefined,
  contactName: '',
  gatewayId: undefined,
  language: 'zh-CN',
  phone: '',
  email: '',
  receiverCount: 10,
  remark: ''
})

const rules = reactive<FormRules>({
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  gatewayId: [{ required: true, message: '请选择门户', trigger: 'change' }],
  language: [{ required: true, message: '请选择语言', trigger: 'change' }],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
})

// 查询列表
const handleQuery = async () => {
  loading.value = true
  try {
    const res = await getAlertUserList(queryParams)
    userList.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取告警用户列表失败', error)
    // 使用Mock数据
    userList.value = [
      {
        id: 1,
        contactName: '张三',
        contactType: 'email',
        gatewayId: 1,
        gatewayName: '消化炉关键（广州）',
        gatewayLocation: '研究公司',
        language: 'zh-CN',
        phone: '13800138000',
        email: 'zhangsan@example.com',
        receiverCount: 10,
        status: 1,
        createTime: '2023-12-25 10:00:00'
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
  queryParams.contactName = ''
  queryParams.gatewayId = undefined
  queryParams.pageNo = 1
  handleQuery()
}

// 新增
const handleAdd = () => {
  dialogTitle.value = '新增告警联系人'
  resetForm()
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: any) => {
  dialogTitle.value = '编辑告警联系人'
  Object.assign(form, row)
  dialogVisible.value = true
}

// 查看详情
const handleViewDetails = (row: any) => {
  ElMessage.info('详情功能开发中...')
}

// 状态变更
const handleStatusChange = async (row: any) => {
  try {
    await updateAlertUserStatus(row.id, row.status)
    ElMessage.success('状态更新成功')
    handleQuery()
  } catch (error) {
    row.status = row.status === 1 ? 0 : 1
    ElMessage.error('状态更新失败')
  }
}

// 删除
const handleDelete = async (row?: any) => {
  const deleteIds = row ? [row.id] : ids.value
  
  await ElMessageBox.confirm('确定要删除选中的告警用户吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })

  try {
    await deleteAlertUser(deleteIds)
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

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate()

  try {
    if (form.id) {
      await updateAlertUser(form)
      ElMessage.success('更新成功')
    } else {
      await createAlertUser(form)
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
  form.contactName = ''
  form.gatewayId = undefined
  form.language = 'zh-CN'
  form.phone = ''
  form.email = ''
  form.receiverCount = 10
  form.remark = ''
}

// 对话框关闭
const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetForm()
}

onMounted(() => {
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

