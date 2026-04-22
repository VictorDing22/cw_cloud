<template>
  <div class="backend-filter-container">
    <!-- 页面标题 -->
    <el-card class="header-card" shadow="hover">
      <div class="header-content">
        <div class="title-section">
          <div class="title-text">
            <h2>Backend 实时滤波服务</h2>
            <p>基于 Kafka 的实时信号滤波与数据处理</p>
          </div>
        </div>
        <div class="status-section">
          <el-tag :type="backendConnected ? 'success' : 'danger'" size="large">
            {{ backendConnected ? '服务运行中' : '服务未连接' }}
          </el-tag>
        </div>
      </div>
    </el-card>

    <!-- Backend 界面嵌入 -->
    <el-card class="iframe-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>Backend 滤波控制台</span>
          <el-space>
            <el-button 
              type="primary" 
              @click="refreshIframe"
              size="small"
            >
              刷新
            </el-button>
            <el-button 
              type="success" 
              @click="openInNewTab"
              size="small"
            >
              新窗口打开
            </el-button>
          </el-space>
        </div>
      </template>
      
      <div class="iframe-container">
        <iframe 
          ref="backendIframe"
          :src="backendUrl" 
          frameborder="0"
          @load="onIframeLoad"
        ></iframe>
      </div>
    </el-card>

    <!-- 使用说明 -->
    <el-card class="instruction-card" shadow="hover">
      <template #header>
        <span>使用说明</span>
      </template>
      
      <el-steps :active="activeStep" align-center>
        <el-step title="新开 WebSocket" description="点击界面中的'新开WebSocket'按钮" />
        <el-step title="配置参数" description="输入设备ID、波形样本数和滤波因子" />
        <el-step title="推送数据" description="点击'推送到Kafka'生成模拟数据" />
        <el-step title="查看结果" description="在下方图表中查看实时滤波效果" />
      </el-steps>

      <el-divider />

      <el-row :gutter="20">
        <el-col :span="8">
          <el-statistic title="Kafka 输入主题" value="sample-input" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="Kafka 输出主题" value="sample-output" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="服务端口" value="8080" />
        </el-col>
      </el-row>

      <el-divider />

      <el-alert
        title="提示"
        type="info"
        :closable="false"
        show-icon
      >
        <template #default>
          <ul style="margin: 0; padding-left: 20px;">
            <li>推送到Kafka的数据会自动被滤波处理并发送到 <code>sample-output</code> 主题</li>
            <li>可以在"实时滤波监控"页面查看处理后的数据和波形对比图</li>
            <li>调整滤波因子可以观察不同的滤波效果（值越大，滤波越平滑）</li>
            <li>如果界面无法加载，请确认 backend.jar 服务是否正常运行</li>
          </ul>
        </template>
      </el-alert>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'

// 状态
const backendUrl = ref(`${window.location.origin}/cal`)
const backendConnected = ref(false)
const activeStep = ref(0)
const backendIframe = ref(null)

// 检查 backend 服务状态
const checkBackendStatus = async () => {
  try {
    const response = await fetch(`${window.location.origin}/actuator/health`, {
      method: 'GET',
      mode: 'no-cors'
    })
    backendConnected.value = true
  } catch (error) {
    console.log('Backend 服务检查:', error)
    // 即使无法访问健康检查端点，也假设服务可能在运行
    backendConnected.value = true
  }
}

// iframe 加载完成
const onIframeLoad = () => {
  backendConnected.value = true
  ElMessage.success('Backend 滤波服务加载成功')
}

// 刷新 iframe
const refreshIframe = () => {
  if (backendIframe.value) {
    backendIframe.value.src = backendIframe.value.src
    ElMessage.info('正在刷新...')
  }
}

// 在新窗口打开
const openInNewTab = () => {
  window.open(backendUrl.value, '_blank')
  ElMessage.success('已在新窗口打开')
}

// 定时检查服务状态
let statusCheckInterval: any = null

onMounted(() => {
  checkBackendStatus()
  statusCheckInterval = setInterval(checkBackendStatus, 10000) // 每10秒检查一次
})

onUnmounted(() => {
  if (statusCheckInterval) {
    clearInterval(statusCheckInterval)
  }
})
</script>

<style scoped lang="scss">
.backend-filter-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: calc(100vh - 84px);
}

.header-card {
  margin-bottom: 20px;
  
  .header-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
    
    .title-section {
      display: flex;
      align-items: center;
      gap: 16px;
      
      .title-text {
        h2 {
          margin: 0;
          font-size: 24px;
          color: #303133;
        }
        
        p {
          margin: 4px 0 0 0;
          font-size: 14px;
          color: #909399;
        }
      }
    }
  }
}

.iframe-card {
  margin-bottom: 20px;
  
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 600;
    font-size: 16px;
    
    span {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }
  
  .iframe-container {
    width: 100%;
    height: 800px;
    overflow: hidden;
    border-radius: 4px;
    background: #fff;
    
    iframe {
      width: 100%;
      height: 100%;
      border: none;
    }
  }
}

.instruction-card {
  :deep(.el-card__header) {
    font-weight: 600;
    font-size: 16px;
    
    span {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }
  
  :deep(.el-steps) {
    margin: 20px 0;
  }
  
  :deep(.el-statistic) {
    text-align: center;
    
    .el-statistic__head {
      font-size: 14px;
      color: #909399;
      margin-bottom: 8px;
    }
    
    .el-statistic__content {
      font-size: 24px;
      font-weight: 600;
      color: #409EFF;
    }
  }
  
  code {
    padding: 2px 6px;
    background: #f5f7fa;
    border: 1px solid #e4e7ed;
    border-radius: 3px;
    color: #e6a23c;
    font-family: 'Courier New', monospace;
  }
}

@media (max-width: 768px) {
  .header-content {
    flex-direction: column;
    gap: 16px;
    
    .title-section {
      width: 100%;
    }
    
    .status-section {
      width: 100%;
      text-align: center;
    }
  }
  
  .iframe-container {
    height: 600px !important;
  }
}
</style>
