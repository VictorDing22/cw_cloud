# 🎯 TDMS信号查看器使用指南

## 📋 功能概述

在Web前端实现了完整的TDMS信号文件选择、分析和可视化功能，集成到现有系统中。

### 核心功能
- ✅ 浏览和选择 Signal-1 和 Signal-2 文件
- ✅ 上传自定义TDMS文件
- ✅ 实时信号分析和滤波
- ✅ 4个子图展示（原始、加噪、滤波、对比）
- ✅ 性能指标计算（MSE改善、相关系数）
- ✅ 图表交互（时间窗口调整、时域/频域切换）

---

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────┐
│  前端 Vue3 组件                                 │
│  TDMSSignalViewer.vue                           │
│  - 文件选择界面                                 │
│  - ECharts 信号可视化                           │
│  - 性能指标展示                                 │
└────────────────┬────────────────────────────────┘
                 │ HTTP API
                 ↓
┌─────────────────────────────────────────────────┐
│  后端 Node.js API Server                        │
│  tdms-api-server.js (Port 3002)                 │
│  - GET  /api/tdms/files     获取文件列表        │
│  - GET  /api/tdms/info      获取文件信息        │
│  - POST /api/tdms/analyze   分析信号            │
└────────────────┬────────────────────────────────┘
                 │ spawn Python
                 ↓
┌─────────────────────────────────────────────────┐
│  Python 分析脚本                                │
│  - tdms-info.py      提取文件信息               │
│  - tdms-analyzer.py  信号分析与滤波             │
│  - 使用 nptdms, scipy, numpy                    │
└─────────────────────────────────────────────────┘
```

---

## 🚀 快速启动

### 步骤1：启动后端API服务器

```bash
# 方式一：双击运行
start-tdms-api-server.bat

# 方式二：命令行运行
node tdms-api-server.js
```

**验证服务器运行**：
访问 http://localhost:3002/api/health

应该返回：
```json
{
  "status": "ok",
  "service": "TDMS Analysis API",
  "timestamp": "2025-12-09T..."
}
```

### 步骤2：启动前端服务

```bash
cd yudao-ui-admin-vue3
npm run dev
```

### 步骤3：访问TDMS信号查看器

1. 打开浏览器访问前端地址（通常是 http://localhost:80）
2. 登录系统
3. 进入菜单：**实时监控 → TDMS信号查看器**

---

## 📖 使用说明

### 1. 选择信号文件

#### Signal-1 数据
- **文件**: `ae_sim_2s.tdms`
- **特点**: 单文件包含多个通道
  - `time_s` - 时间轴
  - `sine` - 原始正弦波
  - `noise` - 纯噪声
  - `sine_plus_noise` - 混合信号

#### Signal-2 数据
- **文件1**: `ae_sine_2s.tdms` - 纯正弦波
- **文件2**: `ae_noise_2s.tdms` - 纯噪声  
- **文件3**: `ae_mix_2s.tdms` - 混合信号

#### 操作步骤
1. 在左侧或中间面板点击文件名
2. 查看右侧显示的文件信息（大小、采样率、通道数等）
3. 点击"开始分析"按钮

### 2. 上传自定义文件

1. 在右侧"上传自定义文件"区域
2. 拖拽 `.tdms` 文件或点击上传
3. 文件限制：
   - 格式：`.tdms`
   - 大小：< 100MB
4. 上传后会自动添加到文件列表

### 3. 查看分析结果

分析完成后，页面会显示：

#### 4个信号图表
1. **① 原始信号** - 蓝色曲线，纯净的正弦波
2. **② 加噪信号** - 红色曲线，被噪声污染的信号
3. **③ 滤波后信号** - 绿色曲线，滤波器处理后的结果
4. **④ 效果对比** - 蓝色实线（原始）vs 绿色虚线（滤波后）

#### 性能指标卡片
- **MSE 改善**: 滤波后均方误差改善百分比（越高越好）
- **滤波前 MSE**: 加噪信号与原始信号的差异
- **滤波后 MSE**: 滤波信号与原始信号的差异
- **相关系数**: 滤波后信号与原始信号的相似度（越接近1越好）

### 4. 交互功能

#### 时间窗口选择
- 前 5ms
- 前 10ms
- 前 20ms
- 前 50ms

调整可查看不同时间范围的信号细节。

#### 时域/频域切换
- **时域**: 显示信号随时间的变化
- **频域**: 显示信号的频率分量（开发中）

#### 下载图片
点击"下载图片"按钮，保存当前分析结果（开发中）。

---

## 🔧 配置选项

### 滤波器参数

当前默认配置（在后端 `tdms-analyzer.py` 中）：
```python
sample_rate = 100000    # 采样率 100 kHz
cutoff_freq = 10000     # 截止频率 10 kHz
filter_order = 6        # 滤波器阶数
```

**修改方法**：
编辑前端代码 `TDMSSignalViewer.vue` 中的 `handleAnalyze` 函数：

```javascript
const res = await axios.post('/api/tdms/analyze', {
  filePath: selectedFile.value,
  sampleRate: 100000,     // 修改采样率
  cutoffFreq: 10000,      // 修改截止频率
  filterOrder: 6          // 修改滤波器阶数
})
```

### API服务器端口

默认端口：`3002`

**修改方法**：
编辑 `tdms-api-server.js`：
```javascript
const PORT = 3002;  // 修改为其他端口
```

---

## 📊 API接口文档

### 1. 获取文件列表

```http
GET /api/tdms/files
```

**响应**:
```json
{
  "signal1": [
    {
      "name": "ae_sim_2s.tdms",
      "path": "/floatdata/signal-1/ae_sim_2s.tdms",
      "size": 3200487
    }
  ],
  "signal2": [
    {
      "name": "ae_sine_2s.tdms",
      "path": "/floatdata/signal-2/ae_sine_2s.tdms",
      "size": 1600396
    }
  ]
}
```

### 2. 获取文件信息

```http
GET /api/tdms/info?path=/floatdata/signal-1/ae_sim_2s.tdms
```

**响应**:
```json
{
  "name": "ae_sim_2s.tdms",
  "size": 3200487,
  "sampleRate": 100000,
  "channels": 4,
  "channelNames": ["time_s", "sine", "noise", "sine_plus_noise"],
  "samples": 200000,
  "duration": 2.0
}
```

### 3. 分析信号

```http
POST /api/tdms/analyze
Content-Type: application/json

{
  "filePath": "/floatdata/signal-1/ae_sim_2s.tdms",
  "sampleRate": 100000,
  "cutoffFreq": 10000,
  "filterOrder": 6
}
```

**响应**:
```json
{
  "signals": {
    "time": [0, 0.01, 0.02, ...],
    "sine": [0.0, 0.951, ...],
    "noisy": [0.123, 0.834, ...],
    "filtered": [0.045, 0.923, ...]
  },
  "metrics": {
    "mseImprovement": 85.5,
    "mseBefore": 0.062500,
    "mseAfter": 0.009063,
    "correlation": 0.9823
  }
}
```

---

## 🐛 故障排查

### 问题1: API服务器无法启动

**症状**: 
```
Error: listen EADDRINUSE :::3002
```

**解决**:
```bash
# 端口被占用，查找并结束进程
netstat -ano | findstr :3002
taskkill /PID <进程ID> /F

# 或修改端口号
# 编辑 tdms-api-server.js，修改 PORT 变量
```

### 问题2: Python脚本执行失败

**症状**:
```
Python分析错误: ModuleNotFoundError: No module named 'nptdms'
```

**解决**:
```bash
# 安装Python依赖
pip install nptdms numpy scipy
```

### 问题3: 前端无法连接API

**症状**: 
前端控制台显示 `Network Error` 或 `CORS Error`

**解决**:
1. 确认API服务器正在运行（访问 http://localhost:3002/api/health）
2. 检查前端代码中的API地址是否正确
3. 确认CORS已启用（tdms-api-server.js 中已配置）

### 问题4: 文件列表为空

**症状**: 
前端显示"未找到TDMS文件"

**解决**:
1. 确认文件路径正确：
   - `e:\Code\CW_Cloud\floatdata\signal-1\ae_sim_2s.tdms`
   - `e:\Code\CW_Cloud\floatdata\signal-2\*.tdms`
2. 检查文件权限
3. 查看API服务器控制台日志

---

## 📦 文件清单

### 前端文件
```
yudao-ui-admin-vue3/src/
├── views/realtime/
│   └── TDMSSignalViewer.vue          # 主页面组件
└── router/modules/
    └── realtime.ts                    # 路由配置（已更新）
```

### 后端文件
```
e:\Code\CW_Cloud/
├── tdms-api-server.js                # Node.js API服务器
├── start-tdms-api-server.bat         # 启动脚本
└── floatdata/floatdata-streaming/
    ├── tdms-info.py                  # 文件信息提取脚本
    └── tdms-analyzer.py              # 信号分析脚本
```

### 数据文件
```
floatdata/
├── signal-1/
│   └── ae_sim_2s.tdms                # Signal-1 数据
└── signal-2/
    ├── ae_sine_2s.tdms               # 纯正弦波
    ├── ae_noise_2s.tdms              # 纯噪声
    └── ae_mix_2s.tdms                # 混合信号
```

---

## 🎉 功能特点总结

### ✅ 已实现
1. **文件管理**
   - 浏览 Signal-1 和 Signal-2 文件
   - 显示文件信息（大小、通道数、采样率）
   - 支持文件上传（.tdms格式）

2. **信号分析**
   - 自动识别原始信号和加噪信号
   - 应用巴特沃斯低通滤波器
   - 计算滤波性能指标

3. **可视化**
   - 4个子图实时渲染
   - 时间窗口调整
   - 响应式图表（自适应窗口大小）

4. **集成**
   - 无缝集成到现有Vue3前端
   - 添加到实时监控菜单
   - 保持系统架构一致性

### 🚧 待开发
1. **频域分析** - FFT频谱显示
2. **图片导出** - 高清图片下载功能
3. **批量分析** - 同时分析多个文件
4. **实时预览** - 上传时实时预览波形

---

## 💡 使用技巧

1. **快速对比**: 选择不同时间窗口，观察信号细节
2. **性能评估**: 关注MSE改善百分比，通常>80%表示滤波效果良好
3. **信号质量**: 查看相关系数，>0.95表示滤波后信号与原始信号高度相似

---

## 📞 技术支持

遇到问题？
1. 查看控制台日志（浏览器F12 + Node.js控制台）
2. 检查故障排查章节
3. 查看Python脚本输出

**完整集成到现有系统，可以直接在Web前端选择和分析TDMS信号文件！** 🎉
