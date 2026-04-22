# 🎬 真实TDMS数据 + 动态显示 - 使用说明

## ✅ 已实现的功能

### 核心特性

1. **真实TDMS数据**
   - 从Signal-1/Signal-2文件读取真实信号
   - 不是模拟的100Hz正弦波
   - 真实的采样数据（2000个点）

2. **动态播放效果**
   - 数据分批推送（每批50个点）
   - 每500ms推送一批
   - 波形图逐渐延伸
   - 像实时数据一样动态显示

3. **播放控制**
   - [加载文件] 按钮 - 开始播放
   - [停止播放] 按钮 - 随时停止
   - 自动播放完成提示

---

## 🚀 使用方法

### 步骤1：切换到历史文件模式
```
点击：[● 历史文件]
```

### 步骤2：选择文件
```
历史文件下拉框：
- Signal-1 (单文件)
- Signal-2 (多文件)
```

### 步骤3：开始播放
```
点击：[加载文件]
```

### 预期效果

✅ **加载提示**
```
✅ Signal-1 加载成功！开始动态播放...
共 2000 个采样点，将以 50 个点/批次动态显示
```

✅ **波形动态延伸**
- 波形图从左到右逐渐延伸
- 每500ms增加50个点
- 总共40批次（2000÷50=40）
- 持续约20秒（40×0.5s=20s）

✅ **播放控制**
- 播放中显示红色[停止播放]按钮
- [加载文件]按钮禁用
- 随时可以点击[停止播放]中断

✅ **播放完成**
```
播放完成
Signal-1 的所有数据已播放完毕
```

---

## 📊 数据流程

### 完整流程

```
点击[加载文件]
    ↓
调用TDMS API
    ↓
Python读取真实TDMS文件
    ↓
返回2000个采样点的noisy信号
    ↓
前端分成40批（每批50点）
    ↓
每500ms推送一批
    ↓
handleRealtimeData()处理
    ↓
LMS滤波 → 更新波形图
    ↓
（循环40次）
    ↓
播放完成
```

### 数据格式

#### TDMS API返回（真实数据）
```javascript
{
  signals: {
    time: [0, 0.01, 0.02, ...],      // 2000个时间点
    sine: [1.0, 0.95, ...],          // 原始干净信号
    noisy: [1.05, 0.88, ...],        // 加噪信号（真实数据！）
    filtered: [1.02, 0.94, ...]      // Butterworth预滤波
  },
  parameters: {
    sampleRate: 100000,
    totalSamples: 200000
  }
}
```

#### 分批推送（动态效果）
```javascript
// 批次1（0-50）
{
  originalSamples: noisy[0:50],
  filteredSamples: noisy[0:50]
}

// 批次2（50-100）
{
  originalSamples: noisy[50:100],
  filteredSamples: noisy[50:100]
}

// ... 共40批次
```

---

## ⚙️ 技术细节

### 关键参数

```javascript
// 分批参数
const batchSize = 50           // 每批50个采样点
const interval = 500           // 每500ms一批
const totalSamples = 2000      // 总共2000个点

// 计算
const totalBatches = 40        // 2000 ÷ 50 = 40批
const totalTime = 20           // 40 × 0.5s = 20秒
```

### 滤波参数

```javascript
// 调用TDMS API时使用高截止频率
{
  folder: 'signal-1',
  sampleRate: 100000,
  cutoffFreq: 45000,   // ← 接近奈奎斯特频率（50kHz）
  filterOrder: 2       // ← 低阶数，减少影响
}

// 目的：相当于不滤波，保留原始信号特征
// 让前端LMS滤波器处理
```

### 定时器管理

```javascript
let historyDataInterval = null
const isPlayingHistory = ref(false)

// 开始播放
historyDataInterval = setInterval(() => {
  // 推送一批数据
  handleRealtimeData(batchData)
}, 500)
isPlayingHistory.value = true

// 停止播放
clearInterval(historyDataInterval)
historyDataInterval = null
isPlayingHistory.value = false
```

---

## 🎯 与实时数据对比

### 实时数据流
```
Backend.jar
    ↓
每500ms生成一批新数据
    ↓
WebSocket推送
    ↓
前端接收并显示
    ↓
持续不断...
```

### 历史数据播放
```
TDMS文件（已存在）
    ↓
一次性读取全部2000个点
    ↓
分批模拟推送（每500ms一批）
    ↓
前端接收并显示
    ↓
播放完40批后停止
```

**关键区别：**
- 实时：数据实时生成，无限持续
- 历史：数据预先存在，有限播放

**相同点：**
- 都是每500ms一批
- 都使用相同的handleRealtimeData()
- 都使用相同的LMS滤波
- 界面显示完全相同

---

## 🎨 界面状态

### 状态1：未播放
```
历史文件: [Signal-1 ▼]
滤波器类型: [LMS ▼]
[加载文件]  ← 蓝色，可点击
```

### 状态2：播放中
```
历史文件: [Signal-1 ▼]  （禁用）
滤波器类型: [LMS ▼]
[加载文件]（灰色，禁用） [停止播放]（红色）
```

### 状态3：播放完成
```
历史文件: [Signal-1 ▼]
滤波器类型: [LMS ▼]
[加载文件]  ← 恢复可点击

提示：Signal-1 的所有数据已播放完毕
```

---

## 🔧 调整参数

### 调整播放速度

#### 更快（250ms/批）
```javascript
historyDataInterval = setInterval(() => {
  // ...
}, 250)  // ← 改为250ms

// 效果：2000点 ÷ 50 × 0.25s = 10秒
```

#### 更慢（1000ms/批）
```javascript
historyDataInterval = setInterval(() => {
  // ...
}, 1000)  // ← 改为1000ms

// 效果：2000点 ÷ 50 × 1s = 40秒
```

### 调整批次大小

#### 更小批次（25点/批）
```javascript
const batchSize = 25  // ← 改为25

// 效果：
// 80批次（2000 ÷ 25）
// 持续40秒（80 × 0.5s）
// 更平滑的动画
```

#### 更大批次（100点/批）
```javascript
const batchSize = 100  // ← 改为100

// 效果：
// 20批次（2000 ÷ 100）
// 持续10秒（20 × 0.5s）
// 更快完成
```

---

## 📋 故障排查

### 问题1：加载失败（500错误）

**症状：** 点击加载文件后提示"加载失败"

**原因：** TDMS API未运行或返回错误

**解决：**
```bash
# 检查TDMS API
netstat -ano | findstr ":3002"

# 如果没有，启动它
node tdms-api-server.js

# 测试API
curl http://localhost:3002/api/tdms/analyze-folder -X POST -H "Content-Type: application/json" -d "{\"folder\":\"signal-1\",\"sampleRate\":100000,\"cutoffFreq\":45000,\"filterOrder\":2}"
```

### 问题2：波形不动

**症状：** 点击加载后波形立即全部显示，不是逐渐延伸

**原因：** 定时器未启动或批次设置错误

**检查：**
```javascript
// 打开浏览器控制台（F12）
// 应该看到每500ms一条日志：
"处理实时数据: { originalSamplesLength: 50, ... }"
```

### 问题3：无法停止播放

**症状：** 点击停止播放无反应

**解决：**
```javascript
// 检查浏览器控制台是否有错误
// 尝试刷新页面
Ctrl + Shift + R
```

---

## 🎉 总结

### ✅ 已实现

1. ✅ 读取真实TDMS文件数据
2. ✅ 分批动态显示（50点/批次）
3. ✅ 模拟实时效果（500ms间隔）
4. ✅ 播放控制（开始/停止）
5. ✅ 使用统一的LMS滤波
6. ✅ 界面显示与实时数据相同

### 🎯 效果

- **真实数据：** Signal-1/Signal-2的真实信号
- **动态显示：** 波形逐渐延伸，像实时一样
- **完全统一：** 使用相同的处理函数和UI

### 📊 数据来源

- **实时模式：** Backend.jar实时生成
- **历史模式：** TDMS文件真实数据

**两者都经过前端LMS滤波，显示效果完全相同！** ✅

---

## 🚀 立即体验

**Vite已自动更新，直接测试！**

```
1. 切换到：[● 历史文件]
2. 选择：Signal-1
3. 点击：[加载文件]
4. 观察：波形逐渐延伸
5. 点击：[停止播放]（随时可停止）
```

**体验真实TDMS数据的动态播放效果！** 🎊
