<template>
  <div class="app-container">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="消息类型" prop="messageType">
          <el-select v-model="queryParams.messageType" placeholder="请选择消息类型" clearable>
            <el-option label="告警通知" value="alert" />
            <el-option label="系统通知" value="system" />
            <el-option label="设备通知" value="device" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="readStatus">
          <el-select v-model="queryParams.readStatus" placeholder="请选择状态" clearable>
            <el-option label="未读" :value="0" />
            <el-option label="已读" :value="1" />
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
          <el-button type="primary" plain @click="handleMarkAllRead">全部标记已读</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain :disabled="multiple" @click="handleDelete">删除</el-button>
        </el-col>
      </el-row>

      <!-- 数据表格 -->
      <el-table
        v-loading="loading"
        :data="messageList"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="消息类型" prop="messageType" width="120">
          <template #default="scope">
            <el-tag v-if="scope.row.messageType === 'alert'" type="danger">告警通知</el-tag>
            <el-tag v-else-if="scope.row.messageType === 'system'" type="primary">系统通知</el-tag>
            <el-tag v-else type="info">设备通知</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题" prop="title" show-overflow-tooltip />
        <el-table-column label="内容" prop="content" show-overflow-tooltip />
        <el-table-column label="级别" prop="level" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.level === 'high'" type="danger">高</el-tag>
            <el-tag v-else-if="scope.row.level === 'medium'" type="warning">中</el-tag>
            <el-tag v-else type="success">低</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="readStatus" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.readStatus === 0" type="danger">未读</el-tag>
            <el-tag v-else type="success">已读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="接收时间" prop="createTime" width="180" />
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleView(scope.row)">查看</el-button>
            <el-button
              v-if="scope.row.readStatus === 0"
              link
              type="success"
              @click="handleMarkRead(scope.row)"
            >
              标记已读
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

    <!-- 消息详情对话框 -->
    <el-dialog v-model="detailVisible" title="消息详情" width="600px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="消息类型">
          <el-tag v-if="currentMessage.messageType === 'alert'" type="danger">告警通知</el-tag>
          <el-tag v-else-if="currentMessage.messageType === 'system'" type="primary">系统通知</el-tag>
          <el-tag v-else type="info">设备通知</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="级别">
          <el-tag v-if="currentMessage.level === 'high'" type="danger">高</el-tag>
          <el-tag v-else-if="currentMessage.level === 'medium'" type="warning">中</el-tag>
          <el-tag v-else type="success">低</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="标题">{{ currentMessage.title }}</el-descriptions-item>
        <el-descriptions-item label="内容">{{ currentMessage.content }}</el-descriptions-item>
        <el-descriptions-item label="设备信息" v-if="currentMessage.deviceInfo">
          {{ currentMessage.deviceInfo }}
        </el-descriptions-item>
        <el-descriptions-item label="接收时间">{{ currentMessage.createTime }}</el-descriptions-item>
        <el-descriptions-item label="阅读时间" v-if="currentMessage.readTime">
          {{ currentMessage.readTime }}
        </el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="currentMessage.readStatus === 0" type="primary" @click="handleMarkRead(currentMessage)">
          标记已读
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAlertMessageList,
  markMessageAsRead,
  markAllMessagesAsRead,
  deleteAlertMessage
} from '@/api/iot/alert'

defineOptions({ name: 'IotAlertMessage' })

const loading = ref(false)
const messageList = ref<any[]>([])
const total = ref(0)
const multiple = ref(true)
const ids = ref<number[]>([])

const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  messageType: '',
  readStatus: undefined,
  dateRange: []
})

const detailVisible = ref(false)
const currentMessage = ref<any>({})

// 查询列表
const handleQuery = async () => {
  loading.value = true
  try {
    const res = await getAlertMessageList(queryParams)
    messageList.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取消息列表失败', error)
    // 使用Mock数据
    messageList.value = [
      {
        id: 1,
        messageType: 'alert',
        title: '告警通知',
        content: '设备JF_RAEM1_WP1_03触发10级超压告警',
        level: 'high',
        readStatus: 0,
        deviceInfo: 'JF_RAEM1_WP1_03',
        createTime: '2023-12-25 13:51:47'
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
  queryParams.messageType = ''
  queryParams.readStatus = undefined
  queryParams.dateRange = []
  queryParams.pageNo = 1
  handleQuery()
}

// 查看详情
const handleView = (row: any) => {
  currentMessage.value = { ...row }
  detailVisible.value = true
  
  // 如果未读，自动标记为已读
  if (row.readStatus === 0) {
    handleMarkRead(row)
  }
}

// 标记已读
const handleMarkRead = async (row: any) => {
  try {
    await markMessageAsRead(row.id)
    ElMessage.success('已标记为已读')
    handleQuery()
    if (detailVisible.value) {
      detailVisible.value = false
    }
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 全部标记已读
const handleMarkAllRead = async () => {
  await ElMessageBox.confirm('确定要将所有未读消息标记为已读吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })

  try {
    await markAllMessagesAsRead()
    ElMessage.success('操作成功')
    handleQuery()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 删除
const handleDelete = async (row?: any) => {
  const deleteIds = row ? [row.id] : ids.value

  await ElMessageBox.confirm('确定要删除选中的消息吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })

  try {
    await deleteAlertMessage(deleteIds)
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

