# 🏭 工业健康监测系统 - 完整API文档

> 基于老师需求和工业监测实际场景的完整API设计

**版本**: v2.0
**更新时间**: 2025-10-14 00:45
**状态**: 🟢 完整规划

---

## 📋 系统架构理解（基于录音）

### 核心理念

**老师的构想**:
1. **微服务架构**: 每个微服务实现一种滤波算法（共20个）
2. **实时性**: 数据采集→处理→报警，要快速响应
3. **Docker部署**: 每个算法一个容器，易于扩展
4. **Web平台**: 可视化展示，用户友好
5. **应用场景**: 航天、航空、桥梁、道路、文物保护

### 系统组成

```
工业健康监测系统
├── 数据采集层
│   └── 声发射传感器 → 采集设备数据
│
├── 微服务层（Spring Boot）
│   ├── 微服务1: 卡尔曼滤波
│   ├── 微服务2: LMS自适应滤波
│   ├── 微服务3: NLMS滤波
│   ├── 微服务4: 均值滤波
│   ├── ...
│   └── 微服务20: 其他算法
│
├── 数据存储层
│   ├── MySQL: 设备信息、历史数据、告警记录
│   └── Redis: 实时数据缓存
│
├── Web平台层（Vue3）
│   ├── 实时监控界面
│   ├── 设备管理
│   ├── 告警管理
│   └── 数据分析
│
└── 编排层
    ├── Spring Cloud Gateway: 统一网关
    ├── Nacos: 服务发现
    └── Docker/K8S: 容器编排
```

---

## 🔌 完整API接口定义

### A. 核心业务API

#### A1. 设备管理模块

##### 1.1 获取设备列表（分页）

```http
GET /admin-api/iot/device/page
```

**请求参数**:
```typescript
{
  deviceName?: string    // 设备名称（模糊搜索）
  product?: string       // 产品型号（RAEM1/RAEM2等）
  status?: string        // 状态（online/offline/warning）
  collecting?: boolean   // 是否采集中
  pageNo: number        // 页码（从1开始）
  pageSize: number      // 每页条数
}
```

**响应**:
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "list": [
      {
        "id": "qc_raem1_4g_107",
        "name": "航天发动机监测点A",
        "product": "RAEM1",
        "status": "online",
        "collecting": true,
        "version": "V1.0.55_20220425",
        "location": "车间A-3号位",
        "installDate": "2024-01-15",
        "ipAddress": "192.168.1.107",
        "dataCount": 15234,
        "alertCount": 3,
        "lastUpdate": 1697180000000,
        "createTime": 1695000000000,
        "config": {
          "threshold": 45,
          "sampleRate": 100,
          "collectMode": "envelope"
        }
      }
    ],
    "total": 50
  }
}
```

##### 1.2 获取设备详情

```http
GET /admin-api/iot/device/get?id={deviceId}
```

##### 1.3 创建设备

```http
POST /admin-api/iot/device/create
```

**请求**:
```json
{
  "name": "设备名称",
  "product": "RAEM1",
  "location": "安装位置",
  "ipAddress": "192.168.1.107"
}
```

##### 1.4 更新设备信息

```http
PUT /admin-api/iot/device/update
```

##### 1.5 删除设备

```http
DELETE /admin-api/iot/device/delete?id={deviceId}
```

##### 1.6 设备操作

```http
POST /admin-api/iot/device/start-collect     # 启动采集
POST /admin-api/iot/device/stop-collect      # 停止采集
POST /admin-api/iot/device/restart           # 重启设备
POST /admin-api/iot/device/update-config     # 更新配置
```

**请求示例**（启动采集）:
```json
{
  "deviceId": "qc_raem1_4g_107"
}
```

**请求示例**（更新配置）:
```json
{
  "deviceId": "qc_raem1_4g_107",
  "config": {
    "threshold": 45,
    "sampleRate": 100,
    "collectMode": "envelope",
    "eet": 1000,
    "dt": 500,
    "hlt": 300,
    "paramSendEnable": true,
    "waveformSendEnable": true,
    "filterType": "LMS",
    "filterOrder": 16,
    "stepSize": 0.01,
    "filterEnable": true
  }
}
```

---

#### A2. 声发射数据模块

##### 2.1 查询声发射数据（分页）

```http
GET /admin-api/iot/ae-data/page
```

**请求参数**:
```typescript
{
  deviceId?: string      // 设备ID
  startTime?: number     // 开始时间（毫秒时间戳）
  endTime?: number       // 结束时间
  param?: string         // 参数类型（duration/ringCount/amplitude等）
  minValue?: number      // 最小值（用于筛选）
  maxValue?: number      // 最大值
  pageNo: number
  pageSize: number
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "list": [
      {
        "id": 1001,
        "deviceId": "qc_raem1_4g_107",
        "deviceName": "航天发动机监测点A",
        "timestamp": 1697180000000,
        "duration": 5234.56,      // 持续时间(μs)
        "ringCount": 234,          // 振铃计数
        "riseTime": 123.45,        // 上升时间(μs)
        "riseCount": 89,           // 上升计数
        "amplitude": 85.6,         // 幅度(dB)
        "avgSignalLevel": 45.3,    // 平均信号电平(dB)
        "energy": 56.7,            // 能量(KpJ)
        "rms": 345.8,              // RMS(mV)
        "filtered": false,         // 是否已滤波
        "filterId": null,          // 使用的滤波器ID
        "createTime": 1697180000000
      }
    ],
    "total": 1000
  }
}
```

##### 2.2 获取最新数据

```http
GET /admin-api/iot/ae-data/latest?deviceId={deviceId}&limit=100
```

**用途**: 获取设备最新的N条数据，用于实时图表显示

##### 2.3 获取数据统计

```http
GET /admin-api/iot/ae-data/statistics
```

**请求参数**:
```json
{
  "deviceId": "qc_raem1_4g_107",
  "startTime": 1697000000000,
  "endTime": 1697180000000,
  "param": "amplitude"
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "count": 1000,
    "mean": 75.5,
    "max": 120.3,
    "min": 40.2,
    "std": 15.6,
    "rms": 78.9
  }
}
```

##### 2.4 批量删除数据

```http
DELETE /admin-api/iot/ae-data/delete-batch
```

**请求**:
```json
{
  "ids": [1001, 1002, 1003]
}
```

##### 2.5 导出数据

**CSV格式**:
```http
POST /admin-api/iot/ae-data/export/csv
Content-Type: application/json
```

**SWAE格式**（声发射专业格式）:
```http
POST /admin-api/iot/ae-data/export/swae
```

**请求**:
```json
{
  "deviceId": "qc_raem1_4g_107",
  "startTime": 1697000000000,
  "endTime": 1697180000000,
  "params": ["duration", "ringCount", "amplitude", "rms"]
}
```

**响应**: 文件流（`Content-Type: application/octet-stream`）

---

#### A3. 告警管理模块

##### 3.1 获取告警列表

```http
GET /admin-api/iot/alert/page
```

**请求参数**:
```typescript
{
  deviceId?: string
  level?: 'critical' | 'warning' | 'info'
  status?: 'pending' | 'processing' | 'resolved'
  alertType?: string
  startTime?: number
  endTime?: number
  pageNo: number
  pageSize: number
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "list": [
      {
        "id": "alert_20241014_001",
        "deviceId": "qc_raem1_4g_107",
        "deviceName": "航天发动机监测点A",
        "level": "critical",
        "alertType": "幅度超限",
        "message": "检测到幅度异常超限，当前值120.5dB，设定阈值100dB，超出20.5%",
        "value": 120.5,
        "threshold": 100.0,
        "deviation": 20.5,
        "status": "pending",
        "alertTime": 1697180000000,
        "handleUser": null,
        "handleNote": null,
        "handleTime": null,
        "createTime": 1697180000000
      }
    ],
    "total": 100
  }
}
```

##### 3.2 获取告警统计

```http
GET /admin-api/iot/alert/statistics
```

**可选参数**: `deviceId`, `startTime`, `endTime`

**响应**:
```json
{
  "code": 0,
  "data": {
    "total": 150,
    "critical": 25,
    "warning": 60,
    "info": 65,
    "pending": 30,
    "processing": 20,
    "resolved": 100,
    "todayNew": 12,
    "todayResolved": 8
  }
}
```

##### 3.3 处理告警

```http
POST /admin-api/iot/alert/process
```

**请求**:
```json
{
  "alertId": "alert_20241014_001",
  "handleNote": "已检查设备，发现传感器松动，已重新紧固。参数调整后恢复正常。"
}
```

##### 3.4 批量处理告警

```http
POST /admin-api/iot/alert/batch-process
```

**请求**:
```json
{
  "alertIds": ["alert_001", "alert_002"],
  "handleNote": "批量处理：参数调整完成"
}
```

##### 3.5 创建告警规则

```http
POST /admin-api/iot/alert/rule/create
```

**请求**:
```json
{
  "name": "航天发动机幅度超限告警",
  "deviceId": "qc_raem1_4g_107",
  "parameter": "amplitude",
  "condition": "greater_than",
  "threshold": 100,
  "level": "critical",
  "enabled": true,
  "notifyMethods": ["web", "sms", "email"]
}
```

##### 3.6 获取告警规则列表

```http
GET /admin-api/iot/alert/rule/list?deviceId={deviceId}
```

---

#### A4. 滤波算法服务（20个微服务）

##### 4.1 调用滤波算法（统一接口）

```http
POST /filter-api/process/{algorithmId}
```

**路径参数**: 
- `algorithmId`: 1-20（微服务编号）
  - 1: 卡尔曼滤波
  - 2: LMS自适应滤波  
  - 3: NLMS归一化滤波
  - 4: 均值滤波
  - 5: 中值滤波
  - 6-20: 其他算法

**请求**（以LMS为例）:
```json
{
  "filterType": "LMS",
  "filterOrder": 16,
  "stepSize": 0.01,
  "originalSignal": [1.0, 2.0, 3.0, ...],
  "noiseSignal": [0.1, 0.2, 0.1, ...],
  "desiredSignal": [1.0, 2.0, 3.0, ...]
}
```

**响应**:
```json
{
  "code": 0,
  "msg": "滤波处理成功",
  "data": {
    "filteredSignal": [1.05, 2.03, 2.98, ...],
    "finalWeights": [0.12, 0.34, 0.56, ...],
    "mse": 0.0234,
    "convergenceSteps": 50,
    "processingTime": 12,
    "algorithmName": "LMS自适应滤波",
    "parameters": {
      "filterOrder": 16,
      "stepSize": 0.01
    }
  }
}
```

##### 4.2 异常检测服务

```http
POST /filter-api/anomaly/detect
```

**请求**:
```json
{
  "signal": [1.0, 2.0, 100.0, 3.0, 2.5, ...],
  "threshold": 3.0,
  "windowSize": 10,
  "method": "statistical"  // statistical/ml/threshold
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "anomalies": [2, 15, 34],
    "anomalyScores": [5.2, 4.8, 3.9],
    "threshold": 3.0,
    "anomalyCount": 3,
    "anomalyRate": 0.03
  }
}
```

##### 4.3 批量调用多个滤波算法（编排）

```http
POST /filter-api/batch-process
```

**请求**:
```json
{
  "algorithms": [1, 2, 3, 4],  // 调用1-4号微服务
  "signal": [1.0, 2.0, 3.0, ...],
  "compareResults": true
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "results": [
      {
        "algorithmId": 1,
        "algorithmName": "卡尔曼滤波",
        "filteredSignal": [...],
        "mse": 0.0234,
        "processingTime": 15
      },
      {
        "algorithmId": 2,
        "algorithmName": "LMS滤波",
        "filteredSignal": [...],
        "mse": 0.0256,
        "processingTime": 12
      }
    ],
    "bestAlgorithm": {
      "id": 1,
      "name": "卡尔曼滤波",
      "mse": 0.0234
    }
  }
}
```

---

#### A5. 声发射评级模块

##### 5.1 计算设备健康评级

```http
POST /admin-api/iot/rating/calculate
```

**请求**:
```json
{
  "deviceId": "qc_raem1_4g_107",
  "startTime": 1697000000000,
  "endTime": 1697180000000
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "deviceId": "qc_raem1_4g_107",
    "rating": "good",
    "ratingStars": 4,
    "healthScore": 82.5,
    "remainingLife": 285,
    "evaluateTime": 1697180000000,
    "indicators": {
      "amplitude": { score: 85, weight: 0.3 },
      "duration": { score: 80, weight: 0.2 },
      "ringCount": { score: 83, weight: 0.2 },
      "rms": { score: 81, weight: 0.3 }
    },
    "trend": "stable",  // improving/stable/declining
    "recommendation": "设备运行状态良好，建议保持当前维护计划"
  }
}
```

##### 5.2 获取评级历史

```http
GET /admin-api/iot/rating/history?deviceId={deviceId}&limit=30
```

**响应**: 返回最近30次评级记录，用于趋势分析

##### 5.3 获取所有设备评级概览

```http
GET /admin-api/iot/rating/overview
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "totalDevices": 50,
    "ratingDistribution": {
      "excellent": 12,
      "good": 25,
      "fair": 10,
      "poor": 3
    },
    "averageScore": 78.5,
    "criticalDevices": ["qc_raem1_4g_105", "qc_raem1_4g_112"]
  }
}
```

---

#### A6. 振动数据模块

##### 6.1 获取振动数据

```http
GET /admin-api/iot/vibration/data
```

**请求参数**:
```json
{
  "deviceId": "qc_raem1_4g_107",
  "axis": "x",  // x/y/z
  "startTime": 1697180000000,
  "samples": 1000
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "deviceId": "qc_raem1_4g_107",
    "axis": "x",
    "sampleRate": 1000,
    "samples": 1000,
    "timeData": [0.1, 0.2, -0.3, 0.5, ...],
    "timestamp": 1697180000000
  }
}
```

##### 6.2 振动频谱分析（FFT）

```http
POST /admin-api/iot/vibration/fft
```

**请求**:
```json
{
  "timeData": [0.1, 0.2, -0.3, ...],
  "sampleRate": 1000
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "frequencies": [0, 2, 4, 6, ..., 500],
    "amplitudes": [0.5, 2.3, 1.8, 0.9, ...],
    "dominantFrequencies": [
      { freq: 25, amplitude: 3.5 },
      { freq: 50, amplitude: 2.8 },
      { freq: 100, amplitude: 1.9 }
    ]
  }
}
```

##### 6.3 振动统计分析

```http
GET /admin-api/iot/vibration/statistics
```

**请求参数**: `deviceId`, `axis`, `startTime`, `endTime`

**响应**:
```json
{
  "code": 0,
  "data": {
    "rms": 2.345,
    "peak": 8.567,
    "mean": 0.123,
    "std": 1.234,
    "kurtosis": 3.2,
    "skewness": 0.5
  }
}
```

---

### B. WebSocket实时推送

#### B1. 连接WebSocket

**URL**: `ws://localhost:48080/ws/iot/realtime`

**认证**: 连接时携带token
```
ws://localhost:48080/ws/iot/realtime?token={accessToken}
```

#### B2. 消息协议

##### 订阅设备数据

**客户端发送**:
```json
{
  "type": "subscribe",
  "deviceId": "qc_raem1_4g_107",
  "params": ["duration", "ringCount", "amplitude", "rms"],
  "interval": 1000  // 推送间隔(ms)
}
```

**服务端确认**:
```json
{
  "type": "subscribe_ack",
  "deviceId": "qc_raem1_4g_107",
  "status": "success"
}
```

##### 接收实时AE数据

**服务端推送**:
```json
{
  "type": "ae_data",
  "deviceId": "qc_raem1_4g_107",
  "timestamp": 1697180123456,
  "data": {
    "duration": 5234.56,
    "ringCount": 234,
    "riseTime": 123.45,
    "amplitude": 85.6,
    "avgSignalLevel": 45.3,
    "rms": 345.8
  },
  "filtered": false
}
```

##### 接收实时告警

**服务端推送**:
```json
{
  "type": "alert",
  "alert": {
    "id": "alert_realtime_001",
    "deviceId": "qc_raem1_4g_107",
    "deviceName": "航天发动机监测点A",
    "level": "critical",
    "alertType": "幅度超限",
    "message": "检测到幅度异常超限，当前值120.5dB",
    "value": 120.5,
    "threshold": 100.0,
    "alertTime": 1697180123456
  }
}
```

##### 接收滤波结果

**服务端推送**（当启用实时滤波时）:
```json
{
  "type": "filter_result",
  "deviceId": "qc_raem1_4g_107",
  "algorithmId": 2,
  "algorithmName": "LMS滤波",
  "timestamp": 1697180123456,
  "original": 5.67,
  "filtered": 5.23,
  "improvement": 7.8
}
```

##### 心跳保活

**客户端发送**:
```json
{
  "type": "ping"
}
```

**服务端响应**:
```json
{
  "type": "pong",
  "timestamp": 1697180123456,
  "serverTime": "2024-10-14 00:55:23"
}
```

---

## 🎯 应用场景API（基于录音需求）

### 场景一：航空航天发动机监测

**API设计**:
```http
GET /admin-api/iot/scenario/aerospace
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "scenarioName": "航空航天发动机健康监测",
    "devices": [
      {
        "id": "aerospace_engine_001",
        "name": "某型号航天发动机_1号",
        "location": "试验台A",
        "status": "monitoring",
        "criticalParams": ["振动", "温度", "声发射"],
        "alertLevel": "normal"
      }
    ],
    "monitoringStrategy": {
      "samplingRate": 10000,  // 10kHz高采样率
      "filterAlgorithms": [1, 2, 5],  // 使用卡尔曼、LMS、小波
      "alertThresholds": {
        "amplitude": 100,
        "vibration": 5.0
      }
    }
  }
}
```

### 场景二：桥梁道路健康检测

**API设计**:
```http
GET /admin-api/iot/scenario/bridge
```

### 场景三：文物保护裂缝监测

**API设计**:
```http
GET /admin-api/iot/scenario/heritage
```

---

## 📊 数据库表设计（完整）

### 1. 设备表 (iot_device)

```sql
CREATE TABLE `iot_device` (
  `id` varchar(50) NOT NULL COMMENT '设备ID',
  `name` varchar(100) NOT NULL COMMENT '设备名称',
  `product` varchar(50) NOT NULL COMMENT '产品型号',
  `status` varchar(20) NOT NULL DEFAULT 'offline' COMMENT '状态:online/offline/warning',
  `collecting` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否采集中',
  `version` varchar(50
