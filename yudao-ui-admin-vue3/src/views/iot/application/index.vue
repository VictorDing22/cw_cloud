<template>
  <div class="application-page">
    <div class="page-header">
      <h1>📱 工业健康监测应用场景</h1>
      <p>基于声发射技术的工业健康监测解决方案，覆盖航天、航空、桥梁、文物等多个领域</p>
    </div>
    
    <!-- 应用场景卡片 -->
    <el-row :gutter="20" class="scenarios">
      <el-col :span="12" v-for="scenario in scenarios" :key="scenario.id">
        <el-card shadow="hover" class="scenario-card" @click="selectScenario(scenario)">
          <div class="scenario-content">
            <div class="scenario-icon" :style="{ background: scenario.color + '20', color: scenario.color }">
              <Icon :icon="scenario.icon" size="48" />
            </div>
            <div class="scenario-info">
              <h3>{{ scenario.name }}</h3>
              <p class="scenario-desc">{{ scenario.description }}</p>
              <el-row class="scenario-stats">
                <el-col :span="12">
                  <div class="stat-item">
                    <Icon icon="ep:cpu" />
                    <span>{{ scenario.deviceCount }}台设备</span>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="stat-item">
                    <Icon icon="ep:success-filled" />
                    <span>{{ scenario.accuracy }}%准确率</span>
                  </div>
                </el-col>
              </el-row>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 场景详情 -->
    <el-dialog 
      v-model="scenarioDialogVisible" 
      :title="currentScenario?.name" 
      width="900px"
      v-if="currentScenario"
    >
      <el-tabs v-model="activeTab">
        <el-tab-pane label="场景介绍" name="intro">
          <div class="scenario-detail">
            <h4>应用背景</h4>
            <p>{{ currentScenario.background }}</p>
            
            <h4>技术方案</h4>
            <ul>
              <li v-for="(solution, index) in currentScenario.solutions" :key="index">
                {{ solution }}
              </li>
            </ul>
            
            <h4>实施效果</h4>
            <el-row :gutter="20">
              <el-col :span="8" v-for="(effect, index) in currentScenario.effects" :key="index">
                <el-statistic :title="effect.label" :value="effect.value" :suffix="effect.unit" />
              </el-col>
            </el-row>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="监测参数" name="params">
          <el-table :data="currentScenario.monitorParams" border>
            <el-table-column prop="name" label="参数名称" width="150" />
            <el-table-column prop="unit" label="单位" width="100" />
            <el-table-column prop="threshold" label="告警阈值" width="120" align="right" />
            <el-table-column prop="description" label="说明" />
          </el-table>
        </el-tab-pane>
        
        <el-tab-pane label="案例展示" name="cases">
          <el-timeline>
            <el-timeline-item 
              v-for="(caseItem, index) in currentScenario.cases" 
              :key="index"
              :timestamp="caseItem.date"
              placement="top"
            >
              <el-card>
                <h4>{{ caseItem.title }}</h4>
                <p>{{ caseItem.description }}</p>
                <el-tag type="success">{{ caseItem.result }}</el-tag>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { Icon } from '@iconify/vue'
import { ElMessage } from 'element-plus'

defineOptions({ name: 'IotApplication' })

interface Scenario {
  id: number
  name: string
  description: string
  icon: string
  color: string
  deviceCount: number
  accuracy: number
  background: string
  solutions: string[]
  effects: Array<{ label: string; value: number; unit: string }>
  monitorParams: Array<{ name: string; unit: string; threshold: number; description: string }>
  cases: Array<{ date: string; title: string; description: string; result: string }>
}

const scenarioDialogVisible = ref(false)
const currentScenario = ref<Scenario | null>(null)
const activeTab = ref('intro')

const scenarios: Scenario[] = [
  {
    id: 1,
    name: '航空航天发动机监测',
    description: '航天发动机运行状态实时监测，故障早期预警',
    icon: 'ep:rocket',
    color: '#409eff',
    deviceCount: 156,
    accuracy: 95.8,
    background: '航空航天发动机是关键设备，需要实时监测其健康状态。通过声发射技术，可以在早期发现裂纹、磨损等故障征兆，避免重大事故。',
    solutions: [
      '布置高灵敏度声发射传感器，采集频率10kHz',
      '使用卡尔曼滤波和LMS自适应滤波组合处理信号',
      '基于AI模式识别技术，自动分类正常和异常模式',
      '实时报警系统，毫秒级响应',
      '预测性维护，延长设备寿命30%以上'
    ],
    effects: [
      { label: '故障检出率', value: 98.5, unit: '%' },
      { label: '误报率', value: 1.2, unit: '%' },
      { label: '响应时间', value: 50, unit: 'ms' }
    ],
    monitorParams: [
      { name: '幅度', unit: 'dB', threshold: 100, description: '声发射信号强度' },
      { name: '振铃计数', unit: '次', threshold: 500, description: '信号振荡次数' },
      { name: '能量', unit: 'KpJ', threshold: 80, description: '声发射能量' },
      { name: 'RMS', unit: 'mV', threshold: 400, description: '有效值' }
    ],
    cases: [
      {
        date: '2024-03-15',
        title: '某型号航天发动机裂纹早期发现',
        description: '通过持续监测，在裂纹长度仅2mm时就发现异常，避免重大事故',
        result: '成功预警，挽回损失约800万元'
      },
      {
        date: '2024-05-20',
        title: '发动机疲劳损伤监测',
        description: '连续运行1000小时监测，准确预测剩余寿命',
        result: '预测准确率95%，延长维护周期20%'
      }
    ]
  },
  {
    id: 2,
    name: '桥梁道路健康检测',
    description: '桥梁结构健康监测，车辆通行时实时采集数据',
    icon: 'ep:connection',
    color: '#67c23a',
    deviceCount: 89,
    accuracy: 93.5,
    background: '桥梁是重要的基础设施，长期承受车辆荷载和环境影响，容易产生裂缝和疲劳损伤。声发射技术可以实时监测结构健康状态。',
    solutions: [
      '在桥梁关键部位布置声发射传感器阵列',
      '车辆通行时触发采集，捕获动态响应',
      '使用小波滤波处理环境噪声干扰',
      '建立裂缝发展模型，预测结构安全性',
      '云平台远程监控，24小时值守'
    ],
    effects: [
      { label: '覆盖桥梁数', value: 15, unit: '座' },
      { label: '检测准确率', value: 93.5, unit: '%' },
      { label: '预警提前量', value: 30, unit: '天' }
    ],
    monitorParams: [
      { name: '幅度', unit: 'dB', threshold: 80, description: '裂缝信号强度' },
      { name: '持续时间', unit: 'μs', threshold: 5000, description: '信号持续时间' },
      { name: '能量', unit: 'KpJ', threshold: 60, description: '释放能量' }
    ],
    cases: [
      {
        date: '2024-06-10',
        title: '某高速公路大桥裂缝发现',
        description: '在例行监测中发现桥墩出现微小裂缝',
        result: '提前30天预警，及时修复，避免交通事故'
      }
    ]
  },
  {
    id: 3,
    name: '文物保护裂缝监测',
    description: '珍贵文物表面裂缝的长期监测和保护',
    icon: 'ep:trophy',
    color: '#e6a23c',
    deviceCount: 45,
    accuracy: 97.2,
    background: '珍贵文物需要长期保护，表面裂缝的发展会威胁文物安全。声发射技术可以无损、实时监测裂缝发展情况。',
    solutions: [
      '采用微型声发射传感器，不破坏文物表面',
      '超低功耗设计，电池供电可持续1年',
      '高灵敏度检测，捕获微小裂缝扩展',
      '温湿度补偿，消除环境影响',
      '云端存储数据，专家远程分析'
    ],
    effects: [
      { label: '监测文物数', value: 120, unit: '件' },
      { label: '裂缝检出率', value: 97.2, unit: '%' },
      { label: '误报率', value: 0.8, unit: '%' }
    ],
    monitorParams: [
      { name: '幅度', unit: 'dB', threshold: 60, description: '裂缝信号' },
      { name: '上升时间', unit: 'μs', threshold: 200, description: '信号上升速度' }
    ],
    cases: [
      {
        date: '2024-07-01',
        title: '某博物馆陶瓷文物裂缝监测',
        description: '连续3个月监测，发现裂缝扩展趋势',
        result: '及时调整保护环境，成功遏制裂缝发展'
      }
    ]
  },
  {
    id: 4,
    name: '工业制造设备故障预警',
    description: '生产设备实时监测，预测性维护',
    icon: 'ep:tools',
    color: '#f56c6c',
    deviceCount: 234,
    accuracy: 92.3,
    background: '工业制造设备的突然故障会导致生产停滞和巨大损失。通过声发射技术进行健康监测，可以实现预测性维护。',
    solutions: [
      '在关键设备部件布置传感器网络',
      '7x24小时连续监测，不影响生产',
      '多算法融合，提高检测准确率',
      '设备健康评分，指导维护计划',
      '故障模式库，快速定位问题'
    ],
    effects: [
      { label: '监测设备', value: 234, unit: '台' },
      { label: '故障预防率', value: 89, unit: '%' },
      { label: '维护成本降低', value: 35, unit: '%' }
    ],
    monitorParams: [
      { name: '幅度', unit: 'dB', threshold: 90, description: '磨损信号' },
      { name: '振铃计数', unit: '次', threshold: 400, description: '冲击次数' },
      { name: 'RMS', unit: 'mV', threshold: 350, description: '振动烈度' }
    ],
    cases: [
      {
        date: '2024-08-15',
        title: '某汽车制造厂冲压设备故障预警',
        description: '提前7天发现轴承磨损异常',
        result: '计划性停机维护，避免突然故障，节省停产损失50万元'
      }
    ]
  }
]

const selectScenario = (scenario: Scenario) => {
  currentScenario.value = scenario
  scenarioDialogVisible.value = true
  activeTab.value = 'intro'
}
</script>

<style scoped>
.application-page {
  padding: 20px;
}

.page-header {
  margin-bottom: 30px;
  text-align: center;
}

.page-header h1 {
  margin: 0 0 12px 0;
  color: #303133;
  font-size: 28px;
}

.page-header p {
  margin: 0;
  color: #909399;
  font-size: 16px;
}

.scenarios {
  margin-top: 30px;
}

.scenario-card {
  margin-bottom: 20px;
  cursor: pointer;
  transition: all 0.3s;
}

.scenario-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
}

.scenario-content {
  display: flex;
  gap: 20px;
}

.scenario-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  border-radius: 16px;
  flex-shrink: 0;
}

.scenario-info {
  flex: 1;
}

.scenario-info h3 {
  margin: 0 0 8px 0;
  color: #303133;
  font-size: 18px;
}

.scenario-desc {
  margin: 0 0 12px 0;
  color: #606266;
  font-size: 14px;
}

.scenario-stats {
  margin-top: 12px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #909399;
}

.scenario-detail h4 {
  margin: 20px 0 10px 0;
  color: #303133;
}

.scenario-detail p {
  line-height: 1.8;
  color: #606266;
}

.scenario-detail ul {
  padding-left: 20px;
}

.scenario-detail li {
  margin: 8px 0;
  line-height: 1.6;
  color: #606266;
}
</style>
