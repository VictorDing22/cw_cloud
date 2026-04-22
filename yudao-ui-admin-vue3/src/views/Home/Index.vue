<template>
  <div class="home-page">
    <!-- 项目标题 -->
    <div class="hero-section">
      <div class="hero-content">
        <h1 class="project-title">
          <Icon icon="ep:monitor" size="48" style="vertical-align: middle; margin-right: 12px" />
          工业健康监测平台
        </h1>
        <p class="project-subtitle">基于声发射技术的实时监测与智能分析系统</p>
        <div class="hero-tags">
          <el-tag size="large" type="primary">Spring Cloud微服务</el-tag>
          <el-tag size="large" type="success">Vue3 + TypeScript</el-tag>
          <el-tag size="large" type="warning">20个滤波算法</el-tag>
          <el-tag size="large" type="danger">实时监控</el-tag>
                </div>
              </div>
            </div>

    <!-- 核心功能 -->
    <el-row :gutter="24" class="features-section">
      <el-col :span="6" v-for="feature in features" :key="feature.id">
        <el-card shadow="hover" class="feature-card">
          <div class="feature-icon" :style="{ background: feature.color + '20', color: feature.color }">
            <Icon :icon="feature.icon" size="40" />
          </div>
          <h3>{{ feature.title }}</h3>
          <p>{{ feature.description }}</p>
          <el-button type="primary" link @click="navigateTo(feature.path)">
            了解更多 →
          </el-button>
        </el-card>
          </el-col>
    </el-row>

    <!-- 系统架构 -->
    <el-card class="architecture-section">
      <template #header>
        <h2>🏗️ 系统架构</h2>
      </template>
      
      <el-row :gutter="20">
        <el-col :span="8">
          <div class="arch-layer">
            <h4>数据采集层</h4>
            <ul>
              <li>声发射传感器</li>
              <li>振动传感器</li>
              <li>高速数据采集</li>
              <li>实时数据传输</li>
            </ul>
              </div>
        </el-col>
        
        <el-col :span="8">
          <div class="arch-layer">
            <h4>微服务处理层</h4>
            <ul>
              <li>20个滤波算法微服务</li>
              <li>卡尔曼、LMS、NLMS等</li>
              <li>Docker容器化部署</li>
              <li>K8S编排管理</li>
            </ul>
              </div>
        </el-col>
        
        <el-col :span="8">
          <div class="arch-layer">
            <h4>Web平台层</h4>
            <ul>
              <li>实时数据可视化</li>
              <li>设备管理</li>
              <li>告警管理</li>
              <li>AI模式识别</li>
            </ul>
            </div>
          </el-col>
        </el-row>
    </el-card>

    <!-- 应用场景 -->
    <el-card class="scenarios-section">
        <template #header>
        <h2>🎯 应用场景</h2>
        </template>
      
      <el-row :gutter="20">
        <el-col :span="6" v-for="scenario in scenarios" :key="scenario.id">
          <div class="scenario-item">
            <div class="scenario-icon">
              <Icon :icon="scenario.icon" size="36" />
                </div>
            <h4>{{ scenario.name }}</h4>
            <p>{{ scenario.desc }}</p>
                </div>
            </el-col>
          </el-row>
      </el-card>

    <!-- 技术特点 -->
    <el-row :gutter="20" class="tech-section">
      <el-col :span="12">
        <el-card>
          <template #header>
            <h3>💡 技术特点</h3>
          </template>
          <ul class="tech-list">
            <li><Icon icon="ep:check" color="#67c23a" /> 微服务架构，20个滤波算法独立部署</li>
            <li><Icon icon="ep:check" color="#67c23a" /> 实时性强，毫秒级数据处理和报警</li>
            <li><Icon icon="ep:check" color="#67c23a" /> AI智能分析，自动模式识别</li>
            <li><Icon icon="ep:check" color="#67c23a" /> 多维可视化，ECharts图表展示</li>
            <li><Icon icon="ep:check" color="#67c23a" /> 预测性维护，设备寿命预测</li>
            <li><Icon icon="ep:check" color="#67c23a" /> 可扩展架构，易于添加新功能</li>
          </ul>
              </el-card>
            </el-col>
      
      <el-col :span="12">
        <el-card>
          <template #header>
            <h3>📊 系统数据</h3>
          </template>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-statistic title="功能模块" :value="11" suffix="个">
                <template #prefix>
                  <Icon icon="ep:menu" />
                </template>
              </el-statistic>
            </el-col>
            <el-col :span="12">
              <el-statistic title="滤波算法" :value="20" suffix="个">
                <template #prefix>
                  <Icon icon="ep:cpu" />
                </template>
              </el-statistic>
            </el-col>
          </el-row>
          <el-row :gutter="20" style="margin-top: 20px">
            <el-col :span="12">
              <el-statistic title="代码行数" :value="4000" suffix="行">
                <template #prefix>
                  <Icon icon="ep:document" />
                </template>
              </el-statistic>
    </el-col>
            <el-col :span="12">
              <el-statistic title="API接口" :value="35" suffix="个">
                <template #prefix>
                  <Icon icon="ep:connection" />
        </template>
              </el-statistic>
            </el-col>
          </el-row>
      </el-card>
    </el-col>
  </el-row>

    <!-- 快速开始 -->
    <el-card class="quick-start-section">
      <template #header>
        <h2>🚀 快速开始</h2>
      </template>
      
      <el-steps :active="3" align-center>
        <el-step title="选择场景" description="选择应用场景" icon="ep:location" />
        <el-step title="配置设备" description="添加监测设备" icon="ep:setting" />
        <el-step title="实时监控" description="查看监测数据" icon="ep:monitor" />
        <el-step title="智能分析" description="AI分析报警" icon="ep:data-analysis" />
      </el-steps>
      
      <div class="quick-links" style="margin-top: 40px; text-align: center">
        <el-button type="primary" size="large" @click="navigateTo('/iot-data/sound-emission')">
          <Icon icon="ep:pie-chart" style="margin-right: 6px" />
          进入监控界面
        </el-button>
        <el-button type="success" size="large" @click="navigateTo('/iot-product/device-manage')">
          <Icon icon="ep:cpu" style="margin-right: 6px" />
          设备管理
        </el-button>
        <el-button type="warning" size="large" @click="navigateTo('/filter/adaptive')">
          <Icon icon="ep:data-analysis" style="margin-right: 6px" />
          滤波实验
        </el-button>
      </div>
    </el-card>

    <!-- 项目信息 -->
    <el-card class="info-section">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="项目名称">YuDao Cloud 工业健康监测平台</el-descriptions-item>
        <el-descriptions-item label="技术栈">Spring Cloud + Vue3 + ECharts</el-descriptions-item>
        <el-descriptions-item label="开发时间">2025-09-25 至 2025-10-14</el-descriptions-item>
        <el-descriptions-item label="前端框架">Vue 3.2 + TypeScript + Element Plus</el-descriptions-item>
        <el-descriptions-item label="后端框架">Spring Boot 2.7 + Nacos + Gateway</el-descriptions-item>
        <el-descriptions-item label="数据库">MySQL 8.0 + Redis 7.0</el-descriptions-item>
        <el-descriptions-item label="核心技术">声发射监测 + 自适应滤波 + AI识别</el-descriptions-item>
        <el-descriptions-item label="部署方式">Docker + K8S微服务编排</el-descriptions-item>
        <el-descriptions-item label="项目状态">
          <el-tag type="success">✅ 开发完成</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'

const router = useRouter()

const features = [
  {
    id: 1,
    title: '实时监控',
    description: '6个关键参数实时图表展示，毫秒级响应',
    icon: 'ep:monitor',
    color: '#409eff',
    path: '/iot-data/sound-emission'
  },
  {
    id: 2,
    title: '设备管理',
    description: '50+设备统一管理，远程控制操作',
    icon: 'ep:cpu',
    color: '#67c23a',
    path: '/iot-product/device-manage'
  },
  {
    id: 3,
    title: '智能告警',
    description: '多级告警系统，自动检测异常',
    icon: 'ep:bell',
    color: '#e6a23c',
    path: '/iot-alert'
  },
  {
    id: 4,
    title: '健康评级',
    description: 'AI评估设备健康，预测剩余寿命',
    icon: 'ep:star',
    color: '#f56c6c',
    path: '/iot-data/sound-rating'
  },
  {
    id: 5,
    title: '振动分析',
    description: '时域频域分析，三轴振动监测',
    icon: 'ep:odometer',
    color: '#409eff',
    path: '/iot-data/vibration'
  },
  {
    id: 6,
    title: '滤波实验',
    description: '20个滤波算法微服务对比测试',
    icon: 'ep:data-analysis',
    color: '#67c23a',
    path: '/filter/adaptive'
  },
  {
    id: 7,
    title: '相关性分析',
    description: '多参数相关性分析，发现数据规律',
    icon: 'ep:connection',
    color: '#e6a23c',
    path: '/iot-data/correlation'
  },
  {
    id: 8,
    title: 'AI识别',
    description: '模式识别和训练，智能分类',
    icon: 'ep:cpu',
    color: '#f56c6c',
    path: '/iot-data/pattern'
  }
]

const scenarios = [
  { id: 1, name: '航空航天', desc: '发动机健康监测', icon: 'ep:rocket' },
  { id: 2, name: '桥梁道路', desc: '结构健康检测', icon: 'ep:connection' },
  { id: 3, name: '文物保护', desc: '裂缝监测保护', icon: 'ep:trophy' },
  { id: 4, name: '工业制造', desc: '设备故障预警', icon: 'ep:tools' }
]

const navigateTo = (path: string) => {
  router.push(path)
}
</script>

<style scoped>
.home-page {
  min-height: calc(100vh - 100px);
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 40px 20px;
}

.hero-section {
  text-align: center;
  padding: 60px 20px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  margin-bottom: 40px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.project-title {
  margin: 0 0 16px 0;
  font-size: 48px;
  font-weight: bold;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.project-subtitle {
  margin: 0 0 24px 0;
  font-size: 20px;
  color: #606266;
}

.hero-tags {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}

.features-section {
  margin-bottom: 40px;
}

.feature-card {
  height: 100%;
  text-align: center;
  transition: transform 0.3s;
  background: rgba(255, 255, 255, 0.95);
}

.feature-card:hover {
  transform: translateY(-8px);
}

.feature-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 16px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.feature-card h3 {
  margin: 0 0 12px 0;
  font-size: 18px;
  color: #303133;
}

.feature-card p {
  margin: 0 0 16px 0;
  color: #909399;
  font-size: 14px;
  line-height: 1.6;
}

.architecture-section,
.scenarios-section,
.tech-section,
.quick-start-section,
.info-section {
  background: rgba(255, 255, 255, 0.95);
  margin-bottom: 24px;
}

.architecture-section h2,
.scenarios-section h2,
.quick-start-section h2 {
  margin: 0;
  font-size: 24px;
  color: #303133;
}

.arch-layer {
  padding: 24px;
  background: #f5f7fa;
  border-radius: 8px;
  height: 100%;
}

.arch-layer h4 {
  margin: 0 0 16px 0;
  font-size: 16px;
  color: #303133;
  text-align: center;
}

.arch-layer ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.arch-layer li {
  padding: 8px 0;
  color: #606266;
  font-size: 14px;
}

.arch-layer li:before {
  content: "✓";
  color: #67c23a;
  font-weight: bold;
  margin-right: 8px;
}

.scenarios-section {
  margin-top: 24px;
}

.scenario-item {
  text-align: center;
  padding: 24px;
  background: #f5f7fa;
  border-radius: 8px;
  transition: all 0.3s;
  cursor: pointer;
}

.scenario-item:hover {
  background: #ecf5ff;
  transform: translateY(-4px);
}

.scenario-icon {
  width: 60px;
  height: 60px;
  margin: 0 auto 16px;
  background: #409eff;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.scenario-item h4 {
  margin: 0 0 8px 0;
  font-size: 16px;
  color: #303133;
}

.scenario-item p {
  margin: 0;
  font-size: 14px;
  color: #909399;
}

.tech-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.tech-list li {
  padding: 12px 0;
  font-size: 15px;
  color: #606266;
  border-bottom: 1px solid #e4e7ed;
}

.tech-list li:last-child {
  border-bottom: none;
}

.quick-links {
  display: flex;
  justify-content: center;
  gap: 16px;
  flex-wrap: wrap;
}
</style>
