<template>
  <el-card shadow="hover">
    <template #header>
      <div class="card-header">
        <el-icon><Setting /></el-icon>
        <span>滤波器配置</span>
      </div>
    </template>

    <el-form label-width="100px">
      <!-- 滤波器类型 -->
      <el-form-item label="滤波器类型">
        <el-select v-model="localConfig.type" @change="handleTypeChange">
          <el-option
            v-for="filter in availableFilters"
            :key="filter.value"
            :label="filter.label"
            :value="filter.value"
            :disabled="filter.disabled"
          >
            <span>{{ filter.label }}</span>
            <span v-if="filter.desc" style="float: right; color: #8492a6; font-size: 12px">
              {{ filter.desc }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>

      <!-- Butterworth参数 -->
      <template v-if="localConfig.type === 'butterworth'">
        <el-form-item label="采样率">
          <el-input-number
            v-model="localConfig.sampleRate"
            :min="1000"
            :max="1000000"
            :step="1000"
          />
          <span style="margin-left: 8px">Hz</span>
        </el-form-item>

        <el-form-item label="截止频率">
          <el-input-number
            v-model="localConfig.cutoffFreq"
            :min="10"
            :max="50000"
            :step="10"
          />
          <span style="margin-left: 8px">Hz</span>
          <el-tooltip content="建议设置为信号主频率的1.2-1.5倍">
            <el-icon style="margin-left: 8px; color: #409eff"><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-form-item>

        <el-form-item label="滤波阶数">
          <el-input-number v-model="localConfig.order" :min="2" :max="12" :step="2" />
          <el-tooltip content="阶数越高，滤波效果越陡峭，但可能引入更多相位失真">
            <el-icon style="margin-left: 8px; color: #409eff"><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-form-item>
      </template>

      <!-- LMS/NLMS参数 -->
      <template v-if="localConfig.type === 'lms' || localConfig.type === 'nlms'">
        <el-form-item label="步长">
          <el-input-number
            v-model="localConfig.stepSize"
            :min="0.001"
            :max="0.1"
            :step="0.001"
            :precision="3"
          />
          <el-tooltip content="步长控制收敛速度，太大会不稳定，太小收敛慢">
            <el-icon style="margin-left: 8px; color: #409eff"><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-form-item>

        <el-form-item label="滤波器长度">
          <el-input-number
            v-model="localConfig.filterLength"
            :min="8"
            :max="128"
            :step="8"
          />
          <el-tooltip content="滤波器长度影响性能和计算量">
            <el-icon style="margin-left: 8px; color: #409eff"><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-form-item>
      </template>

      <!-- 应用按钮 -->
      <el-form-item>
        <el-button type="primary" @click="handleApply" :loading="applying">
          <el-icon><Check /></el-icon>
          应用滤波
        </el-button>
        <el-button @click="handleReset">
          <el-icon><RefreshLeft /></el-icon>
          重置
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Setting, QuestionFilled, Check, RefreshLeft } from '@element-plus/icons-vue'
import type { FilterConfig as FilterConfigType } from '@/utils/signal/filterEngine'

interface Props {
  modelValue: FilterConfigType
  dataSource: 'realtime' | 'history' | 'upload'
  applying?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  applying: false
})

const emit = defineEmits(['update:modelValue', 'apply'])

const localConfig = ref<FilterConfigType>({ ...props.modelValue })

// 根据数据源推荐不同的滤波器
const availableFilters = [
  {
    value: 'butterworth',
    label: 'Butterworth低通滤波',
    desc: '适合历史数据',
    disabled: false
  },
  {
    value: 'lms',
    label: 'LMS自适应滤波',
    desc: '适合实时数据',
    disabled: false
  },
  {
    value: 'nlms',
    label: 'NLMS自适应滤波',
    desc: '适合实时数据',
    disabled: false
  },
  {
    value: 'kalman',
    label: 'Kalman滤波',
    desc: '即将上线',
    disabled: true
  }
]

// 监听配置变化
watch(
  localConfig,
  (newVal) => {
    emit('update:modelValue', newVal)
  },
  { deep: true }
)

// 监听外部变化
watch(
  () => props.modelValue,
  (newVal) => {
    localConfig.value = { ...newVal }
  },
  { deep: true }
)

// 滤波器类型改变时，切换推荐参数
const handleTypeChange = () => {
  if (localConfig.value.type === 'butterworth') {
    localConfig.value.cutoffFreq = 5000
    localConfig.value.order = 6
  } else if (localConfig.value.type === 'lms' || localConfig.value.type === 'nlms') {
    localConfig.value.stepSize = 0.01
    localConfig.value.filterLength = 32
  }
}

// 应用滤波
const handleApply = () => {
  emit('apply', localConfig.value)
}

// 重置配置
const handleReset = () => {
  if (props.dataSource === 'realtime') {
    localConfig.value = {
      type: 'lms',
      sampleRate: 100000,
      stepSize: 0.01,
      filterLength: 32
    }
  } else {
    localConfig.value = {
      type: 'butterworth',
      sampleRate: 100000,
      cutoffFreq: 5000,
      order: 6
    }
  }
}
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
</style>
