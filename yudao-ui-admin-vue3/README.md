# 🏭 工业健康监测云平台 - 前端项目

<p align="center">
  <img src="https://img.shields.io/badge/Vue-3.2-brightgreen.svg" alt="Vue">
  <img src="https://img.shields.io/badge/TypeScript-4.9-blue.svg" alt="TypeScript">
  <img src="https://img.shields.io/badge/Element%20Plus-2.4-409EFF.svg" alt="Element Plus">
  <img src="https://img.shields.io/badge/ECharts-5.4-red.svg" alt="ECharts">
</p>

## 📋 项目简介

工业健康监测云平台前端项目，基于 Vue3 + TypeScript + Element Plus + ECharts 构建的现代化工业监测管理界面。

### 🎯 核心功能页面

1. **首页** - 项目介绍与快速导航
2. **工业监控界面** - 6类声发射参数实时监控、数据表格、CSV导出
3. **设备管理** - 设备列表、状态监控、设备操作、批量管理
4. **告警管理** - 告警统计、告警列表、告警处理、级别标签
5. **滤波器实验** - 20种滤波算法、参数配置、对比实验、性能评估
6. **声发射评级** - 健康度评估、寿命预测、评级趋势、分布统计
7. **振动数据分析** - 时域/频域分析、三轴对比、异常检测、FFT变换
8. **相关图分析** - 多参数散点图、皮尔逊相关、线性回归、热力图
9. **模式识别** - AI模型训练（NN/SVM/RF/KNN）、实时识别、批量识别
10. **物联网应用** - 行业应用案例（航空航天、桥梁、文物、工业制造）
11. **设备分组** - 设备分组管理

---

## 🚀 快速开始

### 环境要求

- **Node.js**: 16.0+
- **npm**: 8.0+ 或 **pnpm**: 7.0+

### 安装依赖

```bash
# 使用 npm
npm install

# 或使用 pnpm（推荐）
pnpm install
```

### 开发运行

```bash
npm run dev
```

访问地址：http://localhost:3000

默认账号：`admin` / `admin123`

### 生产构建

```bash
npm run build
```

构建产物输出到 `dist` 目录

---

## 📦 技术栈

| 技术           | 版本   | 说明                     |
| -------------- | ------ | ------------------------ |
| Vue            | 3.2    | 渐进式 JavaScript 框架   |
| TypeScript     | 4.9    | JavaScript 的超集        |
| Element Plus   | 2.4    | Vue 3 组件库             |
| ECharts        | 5.4    | 数据可视化图表库         |
| Vite           | 4.5    | 新一代前端构建工具       |
| Axios          | 1.6    | HTTP 客户端              |
| Pinia          | 2.1    | Vue 3 状态管理库         |
| Vue Router     | 4.2    | Vue.js 官方路由          |

---

## 📁 项目结构

```
yudao-ui-admin-vue3/
├── public/                 # 静态资源
├── src/
│   ├── api/                # API 接口
│   │   ├── iot/            # 物联网相关API
│   │   └── filter/         # 滤波算法API
│   ├── assets/             # 资源文件
│   ├── components/         # 公共组件
│   ├── router/             # 路由配置
│   │   └── modules/        # 路由模块
│   │       ├── iot.ts      # 物联网路由
│   │       └── filter.ts   # 滤波器路由
│   ├── stores/             # Pinia 状态管理
│   ├── styles/             # 全局样式
│   ├── utils/              # 工具函数
│   │   └── iot-websocket.ts # WebSocket 工具类
│   ├── views/              # 页面组件
│   │   ├── Home/           # 首页
│   │   ├── iot/            # 物联网页面
│   │   │   ├── device/     # 设备管理
│   │   │   ├── deviceGroup/ # 设备分组
│   │   │   ├── alert/      # 告警管理
│   │   │   ├── soundRating/ # 声发射评级
│   │   │   ├── vibration/  # 振动数据
│   │   │   ├── correlation/ # 相关图
│   │   │   ├── pattern/    # 模式识别
│   │   │   └── application/ # 应用场景
│   │   └── filter/         # 滤波器
│   │       └── AdaptiveFilter.vue # 滤波实验
│   ├── App.vue             # 根组件
│   └── main.ts             # 入口文件
├── .env.development        # 开发环境配置
├── .env.production         # 生产环境配置
├── index.html              # HTML 模板
├── package.json            # 项目依赖
├── tsconfig.json           # TypeScript 配置
└── vite.config.ts          # Vite 配置
```

---

## 🔧 配置说明

### 环境变量

`.env.development` (开发环境):

```env
# API 基础地址
VITE_API_URL=http://localhost:48080

# WebSocket 地址
VITE_WS_URL=ws://localhost:48080/ws

# 应用标题
VITE_APP_TITLE=工业健康监测云平台
```

`.env.production` (生产环境):

```env
VITE_API_URL=https://your-api-domain.com
VITE_WS_URL=wss://your-api-domain.com/ws
VITE_APP_TITLE=工业健康监测云平台
```

### Vite 配置

`vite.config.ts` - 代理配置、构建优化等

---

## 🎨 主要功能模块

### 1. 工业监控界面 (`/iot/device`)

- **实时图表**：6个 ECharts 时序图，动态展示声发射参数
- **筛选器**：产品/设备/参数/时间范围筛选
- **数据表格**：分页展示、多选、详情、删除
- **CSV 导出**：一键导出数据到 CSV 文件
- **设备配置**：AE参数配置、滤波配置对话框

### 2. 滤波器实验 (`/filter/adaptive`)

- **算法选择**：20种滤波算法多选
- **参数配置**：每种算法独立参数表单
- **对比实验**：多算法并行执行
- **结果展示**：MSE误差、处理时间、最优算法推荐

### 3. 振动数据分析 (`/iot/vibration`)

- **时域波形**：实时波形图
- **频域频谱**：FFT频谱分析
- **三轴对比**：X/Y/Z轴振动对比
- **统计特征**：RMS、峰值、均值、标准差

### 4. 相关图分析 (`/iot/correlation`)

- **散点图**：任意两参数关联
- **相关系数**：皮尔逊相关计算
- **线性回归**：趋势线与方程
- **热力图**：多参数相关矩阵

### 5. AI模式识别 (`/iot/pattern`)

- **模式库**：模式定义与管理
- **模型训练**：支持NN、SVM、RF、KNN
- **实时识别**：实时数据模式匹配
- **性能评估**：准确率、混淆矩阵

---

## 🌐 API 集成

### REST API

所有 API 接口定义在 `src/api/iot/index.ts` 和 `src/api/filter/index.ts`

```typescript
// 示例：获取设备列表
import { getDeviceList } from '@/api/iot'

const devices = await getDeviceList({
  pageNo: 1,
  pageSize: 10,
  status: 'online'
})
```

### WebSocket 实时推送

使用 `src/utils/iot-websocket.ts` 工具类：

```typescript
import IoTWebSocket from '@/utils/iot-websocket'

// 创建连接
const ws = new IoTWebSocket('ws://localhost:48080/ws')

// 订阅设备
ws.subscribe('device-123')

// 监听数据
ws.on('ae-data', (data) => {
  console.log('收到声发射数据:', data)
})

// 监听告警
ws.on('alert', (alert) => {
  console.log('收到告警:', alert)
})
```

---

## 📊 图表组件

### ECharts 配置

项目使用 ECharts 5.4 进行数据可视化：

- **时序图**：用于声发射数据、振动数据
- **散点图**：用于相关图分析
- **热力图**：用于相关性矩阵
- **饼图**：用于评级分布、告警统计
- **柱状图**：用于频率分布、统计对比
- **仪表盘**：用于健康度展示

---

## 🛠️ 开发指南

### 新增页面

1. 在 `src/views` 下创建 Vue 组件
2. 在 `src/router/modules` 添加路由配置
3. 在 `src/api` 添加API接口定义

### 组件开发规范

- 使用 `<script setup lang="ts">` 语法
- 使用 TypeScript 类型注解
- 使用 Element Plus 组件库
- 遵循 Vue 3 Composition API

### 样式规范

- 使用 SCSS 预处理器
- 遵循 BEM 命名规范
- 使用 CSS 变量定义主题色

---

## 🐛 常见问题

### 1. 端口被占用

修改 `vite.config.ts` 中的端口配置：

```typescript
export default defineConfig({
  server: {
    port: 3001  // 更改端口
  }
})
```

### 2. API 请求跨域

开发环境已配置代理，生产环境需要后端支持 CORS

### 3. WebSocket 连接失败

检查 WebSocket 地址配置和后端 WebSocket 服务状态

---

## 📞 相关链接

- **后端项目**: https://github.com/sheng-dev/CW_Cloud/tree/backend
- **完整文档**: 参考后端项目 `docs` 目录
- **问题反馈**: https://github.com/sheng-dev/CW_Cloud/issues

---

## 📄 开源协议

本项目采用 [MIT License](../LICENSE) 开源协议。

---

<p align="center">
  <sub>基于 YuDao Admin Vue3 构建</sub>
</p>
