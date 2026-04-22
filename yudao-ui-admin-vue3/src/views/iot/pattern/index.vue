<template>
  <div class="pattern-page">
    <div class="page-header">
      <h2>🎯 AI模式识别</h2>
      <p>基于机器学习的设备状态模式识别，自动分类正常和异常模式</p>
    </div>

    <!-- 模式库 -->
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>模式库管理</span>
              <el-button type="primary" size="small" @click="handleAddPattern">
                <Icon icon="ep:plus" />
                新增模式
              </el-button>
            </div>
          </template>

          <el-table :data="patternLibrary" style="width: 100%" max-height="500">
            <el-table-column prop="name" label="模式名称" width="120" />
            <el-table-column label="类型" width="80">
              <template #default="{ row }">
                <el-tag :type="row.type === 'normal' ? 'success' : 'danger'" size="small">
                  {{ row.type === 'normal' ? '正常' : '异常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sampleCount" label="样本数" width="80" align="right" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="selectPattern(row)">
                  查看
                </el-button>
                <el-button size="small" type="danger" link @click="deletePattern(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card style="margin-top: 20px">
          <template #header>模型训练</template>
          <el-form label-width="100px">
            <el-form-item label="训练算法">
              <el-select v-model="trainConfig.algorithm" style="width: 100%">
                <el-option label="神经网络(NN)" value="nn" />
                <el-option label="支持向量机(SVM)" value="svm" />
                <el-option label="随机森林(RF)" value="rf" />
                <el-option label="K近邻(KNN)" value="knn" />
              </el-select>
            </el-form-item>

            <el-form-item label="训练轮次">
              <el-input-number
                v-model="trainConfig.epochs"
                :min="10"
                :max="1000"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="学习率">
              <el-input-number
                v-model="trainConfig.learningRate"
                :min="0.0001"
                :max="0.1"
                :step="0.0001"
                :precision="4"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="trainModel" :loading="training" style="width: 100%">
                {{ training ? '训练中...' : '开始训练' }}
              </el-button>
            </el-form-item>

            <el-progress
              v-if="training"
              :percentage="trainProgress"
              :color="trainProgress < 100 ? '#409eff' : '#67c23a'"
            />
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>实时识别</span>
              <el-button-group size="small">
                <el-button
                  :type="recognitionMode === 'realtime' ? 'primary' : 'default'"
                  @click="recognitionMode = 'realtime'"
                >
                  实时识别
                </el-button>
                <el-button
                  :type="recognitionMode === 'batch' ? 'primary' : 'default'"
                  @click="recognitionMode = 'batch'"
                >
                  批量识别
                </el-button>
              </el-button-group>
            </div>
          </template>

          <div v-if="recognitionMode === 'realtime'">
            <el-form :inline="true">
              <el-form-item label="选择设备">
                <el-select v-model="recognitionConfig.device" style="width: 200px">
                  <el-option label="qc_raem1_4g_107" value="qc_raem1_4g_107" />
                  <el-option label="qc_raem1_4g_108" value="qc_raem1_4g_108" />
                </el-select>
              </el-form-item>

              <el-form-item>
                <el-button type="primary" @click="startRecognition" :loading="recognizing">
                  {{ recognizing ? '识别中...' : '开始识别' }}
                </el-button>
              </el-form-item>
            </el-form>

            <el-divider />

            <el-result
              v-if="recognitionResult"
              :icon="recognitionResult.pattern.type === 'normal' ? 'success' : 'error'"
              :title="recognitionResult.pattern.name"
              :sub-title="`置信度: ${(recognitionResult.confidence * 100).toFixed(2)}%`"
            >
              <template #extra>
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="识别时间">
                    {{ new Date(recognitionResult.timestamp).toLocaleString() }}
                  </el-descriptions-item>
                  <el-descriptions-item label="处理耗时">
                    {{ recognitionResult.processingTime }}ms
                  </el-descriptions-item>
                  <el-descriptions-item label="模式类型">
                    <el-tag
                      :type="recognitionResult.pattern.type === 'normal' ? 'success' : 'danger'"
                    >
                      {{ recognitionResult.pattern.type === 'normal' ? '正常模式' : '异常模式' }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="相似度">
                    <el-progress
                      :percentage="recognitionResult.similarity * 100"
                      :color="recognitionResult.similarity > 0.8 ? '#67c23a' : '#e6a23c'"
                    />
                  </el-descriptions-item>
                </el-descriptions>

                <div style="margin-top: 20px">
                  <div ref="signatureChartRef" style="height: 250px"></div>
                </div>
              </template>
            </el-result>
          </div>

          <div v-if="recognitionMode === 'batch'">
            <el-table :data="batchResults" max-height="500">
              <el-table-column type="index" label="#" width="60" />
              <el-table-column prop="timestamp" label="时间" width="180">
                <template #default="{ row }">
                  {{ new Date(row.timestamp).toLocaleString() }}
                </template>
              </el-table-column>
              <el-table-column prop="patternName" label="识别模式" width="150" />
              <el-table-column label="类型" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.type === 'normal' ? 'success' : 'danger'" size="small">
                    {{ row.type === 'normal' ? '正常' : '异常' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="confidence" label="置信度" width="120" align="right">
                <template #default="{ row }"> {{ (row.confidence * 100).toFixed(2) }}% </template>
              </el-table-column>
              <el-table-column prop="processingTime" label="耗时(ms)" width="100" align="right" />
            </el-table>
          </div>
        </el-card>

        <el-card style="margin-top: 20px">
          <template #header>模型性能</template>
          <el-row :gutter="20">
            <el-col :span="12">
              <div ref="accuracyChartRef" style="height: 300px"></div>
            </el-col>
            <el-col :span="12">
              <div ref="confusionMatrixRef" style="height: 300px"></div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { Icon } from '@iconify/vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

defineOptions({ name: 'IotPattern' })

interface Pattern {
  id: number
  name: string
  type: 'normal' | 'abnormal'
  sampleCount: number
  signature: number[]
}

const training = ref(false)
const recognizing = ref(false)
const trainProgress = ref(0)
const recognitionMode = ref<'realtime' | 'batch'>('realtime')

const patternLibrary = ref<Pattern[]>([
  { id: 1, name: '正常运行模式', type: 'normal', sampleCount: 1000, signature: [] },
  { id: 2, name: '轻微磨损模式', type: 'normal', sampleCount: 500, signature: [] },
  { id: 3, name: '裂纹发展模式', type: 'abnormal', sampleCount: 300, signature: [] },
  { id: 4, name: '严重故障模式', type: 'abnormal', sampleCount: 200, signature: [] }
])

const trainConfig = reactive({
  algorithm: 'nn',
  epochs: 100,
  learningRate: 0.001
})

const recognitionConfig = reactive({
  device: 'qc_raem1_4g_107'
})

const recognitionResult = ref<any>(null)
const batchResults = ref<any[]>([])

const signatureChartRef = ref<HTMLElement>()
const accuracyChartRef = ref<HTMLElement>()
const confusionMatrixRef = ref<HTMLElement>()

let signatureChart: echarts.ECharts | null = null
let accuracyChart: echarts.ECharts | null = null
let confusionMatrixChart: echarts.ECharts | null = null

const handleAddPattern = () => {
  ElMessage.info('新增模式功能开发中...')
}

const selectPattern = (pattern: Pattern) => {
  ElMessage.info(`查看模式：${pattern.name}`)
}

const deletePattern = (pattern: Pattern) => {
  ElMessage.info(`删除模式：${pattern.name}`)
}

const trainModel = () => {
  training.value = true
  trainProgress.value = 0

  const interval = setInterval(() => {
    trainProgress.value += 10
    if (trainProgress.value >= 100) {
      clearInterval(interval)
      training.value = false
      ElMessage.success('模型训练完成！')
      initPerformanceCharts()
    }
  }, 200)
}

const startRecognition = () => {
  recognizing.value = true

  setTimeout(() => {
    // 模拟识别结果
    const patterns = patternLibrary.value
    const randomPattern = patterns[Math.floor(Math.random() * patterns.length)]

    recognitionResult.value = {
      pattern: randomPattern,
      confidence: 0.85 + Math.random() * 0.14,
      similarity: 0.8 + Math.random() * 0.19,
      timestamp: Date.now(),
      processingTime: Math.floor(50 + Math.random() * 150)
    }

    recognizing.value = false

    // 绘制特征图
    if (signatureChartRef.value) {
      signatureChart = echarts.init(signatureChartRef.value)

      const mockSignature = Array.from({ length: 50 }, () => Math.random())

      signatureChart.setOption({
        title: { text: '模式特征签名', left: 'center', textStyle: { fontSize: 12 } },
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category' },
        yAxis: { type: 'value' },
        series: [
          {
            type: 'line',
            data: mockSignature,
            smooth: true,
            areaStyle: { color: 'rgba(64, 158, 255, 0.3)' },
            lineStyle: { color: '#409eff', width: 2 }
          }
        ]
      })
    }
  }, 1000)
}

const initPerformanceCharts = () => {
  // 准确率趋势图
  if (accuracyChartRef.value) {
    accuracyChart = echarts.init(accuracyChartRef.value)

    const epochs = Array.from({ length: 100 }, (_, i) => i + 1)
    const accuracy = epochs.map((e) =>
      Math.min(0.95, 0.5 + (e / 100) * 0.45 + (Math.random() - 0.5) * 0.05)
    )

    accuracyChart.setOption({
      title: { text: '训练准确率', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: epochs, name: '训练轮次' },
      yAxis: { type: 'value', min: 0, max: 1, name: '准确率' },
      series: [
        {
          type: 'line',
          data: accuracy,
          smooth: true,
          lineStyle: { color: '#67c23a', width: 2 },
          areaStyle: { color: 'rgba(103, 194, 58, 0.2)' }
        }
      ]
    })
  }

  // 混淆矩阵
  if (confusionMatrixRef.value) {
    confusionMatrixChart = echarts.init(confusionMatrixRef.value)

    const confusionData = [
      [0, 0, 950], // 预测正常，实际正常
      [0, 1, 50], // 预测正常，实际异常
      [1, 0, 30], // 预测异常，实际正常
      [1, 1, 970] // 预测异常，实际异常
    ]

    confusionMatrixChart.setOption({
      title: { text: '混淆矩阵', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: {
        position: 'top',
        formatter: (params: any) => {
          const labels = ['正常', '异常']
          return `预测${labels[params.value[1]]} / 实际${labels[params.value[0]]}: ${params.value[2]}`
        }
      },
      grid: { left: 80, top: 80, right: 40, bottom: 60 },
      xAxis: { type: 'category', data: ['正常', '异常'], name: '预测类别' },
      yAxis: { type: 'category', data: ['正常', '异常'], name: '实际类别' },
      visualMap: {
        min: 0,
        max: 1000,
        calculable: true,
        orient: 'horizontal',
        left: 'center',
        bottom: 10,
        inRange: { color: ['#e0f3f8', '#4575b4'] }
      },
      series: [
        {
          type: 'heatmap',
          data: confusionData,
          label: { show: true, formatter: '{c}' }
        }
      ]
    })
  }
}

onUnmounted(() => {
  signatureChart?.dispose()
  accuracyChart?.dispose()
  confusionMatrixChart?.dispose()
})
</script>

<style scoped>
.pattern-page {
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
