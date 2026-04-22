# 物联网数据和告警管理API文档

> 更新时间：2025-10-20  
> 版本：v2.0

## 目录

1. [参数数据对比接口](#1-参数数据对比接口)
2. [告警用户管理接口](#2-告警用户管理接口)
3. [告警场景管理接口](#3-告警场景管理接口)
4. [用户消息管理接口](#4-用户消息管理接口)
5. [告警日志管理接口](#5-告警日志管理接口)

---

## 1. 参数数据对比接口

### 1.1 获取参数对比数据

**接口地址**: `GET /iot/data/parameter/compare`

**请求参数**:
```typescript
{
  productId: string           // 产品ID
  deviceIds: string[]         // 设备ID列表（最多3个）
  parameters: string[]        // 参数列表（amplitude, energy, rms）（最多3个）
  timeRange?: string          // 时间范围（1h, 6h, 12h, 24h, 7d）
  dateRange?: string[]        // 自定义时间范围 [开始时间, 结束时间]
  pageNo?: number            // 页码
  pageSize?: number          // 每页数量
}
```

**响应数据**:
```typescript
{
  code: 0,
  data: {
    chartData: [              // 图表数据
      {
        time: string          // 时间点
        deviceId_parameter: number  // 设备_参数的值
      }
    ],
    tableData: [              // 表格数据
      {
        time: string          // 时间点
        deviceId_parameter: number  // 设备_参数的值
      }
    ],
    total: number             // 总数
  }
}
```

### 1.2 导出参数对比数据

**接口地址**: `GET /iot/data/parameter/export`

**请求参数**: 同 1.1

**响应**: Excel文件下载

---

## 2. 告警用户管理接口

### 2.1 获取告警用户列表

**接口地址**: `GET /iot/alert/user/list`

**请求参数**:
```typescript
{
  pageNo: number             // 页码
  pageSize: number           // 每页数量
  contactName?: string       // 联系人姓名（模糊查询）
  gatewayId?: number         // 门户ID
}
```

**响应数据**:
```typescript
{
  code: 0,
  data: {
    list: [
      {
        id: number                // 用户ID
        contactName: string       // 联系人姓名
        contactType: string       // 联系类型（email, phone）
        gatewayId: number         // 门户ID
        gatewayName: string       // 门户名称
        gatewayLocation: string   // 门户位置
        language: string          // 使用语言（zh-CN, en-US）
        phone: string             // 手机号
        email: string             // 邮箱
        receiverCount: number     // 接收器数量(min)
        status: number            // 状态（0-禁用 1-启用）
        remark: string            // 备注
        createTime: string        // 创建时间
        updateTime: string        // 更新时间
      }
    ],
    total: number               // 总数
  }
}
```

### 2.2 获取告警用户详情

**接口地址**: `GET /iot/alert/user/{id}`

**路径参数**:
- `id`: 用户ID

**响应数据**: 单个用户对象（同2.1）

### 2.3 创建告警用户

**接口地址**: `POST /iot/alert/user/create`

**请求体**:
```typescript
{
  contactName: string         // 联系人姓名（必填）
  gatewayId: number           // 门户ID（必填）
  language: string            // 使用语言（必填，zh-CN/en-US）
  phone: string               // 手机号（选填，需符合格式）
  email: string               // 邮箱（选填，需符合格式）
  receiverCount: number       // 接收器数量（必填，默认10）
  remark?: string             // 备注
}
```

**响应数据**:
```typescript
{
  code: 0,
  data: number                // 新创建的用户ID
}
```

### 2.4 更新告警用户

**接口地址**: `PUT /iot/alert/user/update`

**请求体**: 同2.3，需包含`id`字段

### 2.5 删除告警用户

**接口地址**: `DELETE /iot/alert/user/delete`

**请求体**:
```typescript
{
  ids: number[]               // 用户ID列表
}
```

### 2.6 更新告警用户状态

**接口地址**: `PUT /iot/alert/user/update-status`

**请求体**:
```typescript
{
  id: number                  // 用户ID
  status: number              // 状态（0-禁用 1-启用）
}
```

---

## 3. 告警场景管理接口

### 3.1 获取告警场景列表

**接口地址**: `GET /iot/alert/scene/list`

**请求参数**:
```typescript
{
  pageNo: number             // 页码
  pageSize: number           // 每页数量
  sceneName?: string         // 场景名称（模糊查询）
  sceneType?: string         // 场景类型（intensity, temperature, other）
  gatewayId?: number         // 门户ID
}
```

**响应数据**:
```typescript
{
  code: 0,
  data: {
    list: [
      {
        id: number                    // 场景ID
        sceneName: string             // 场景名称
        sceneType: string             // 场景类型
        gatewayId: number             // 门户ID
        gatewayName: string           // 门户名称
        gatewayLocation: string       // 门户位置
        alertLevel: number            // 告警等级（1-5级）
        triggerDuration: number       // 触发时间(秒)
        status: number                // 状态（0-禁用 1-启用）
        ratingType: string            // 评级类型（auto, manual）
        rules: [                      // 触发规则列表
          {
            enabled: boolean          // 是否启用
            parameter: string         // 参数（amplitude, energy, rms）
            condition: string         // 条件（gt, lt, eq, gte, lte）
            threshold: number         // 阈值
          }
        ],
        evaluationRule: string        // 评估规则（any-任一, all-所有）
        statisticsDuration: number    // 统计时长(秒)
        thresholdType: string         // 阈值类型
        bmwThreshold: number          // 宝马规则上限阈值(秒)
        notifyMethod: string[]        // 通知方式（email, sms）
        notifyUsers: number[]         // 通知用户ID列表
        remark: string                // 备注
        createTime: string            // 创建时间
        updateTime: string            // 更新时间
      }
    ],
    total: number                     // 总数
  }
}
```

### 3.2 获取告警场景详情

**接口地址**: `GET /iot/alert/scene/{id}`

**路径参数**:
- `id`: 场景ID

**响应数据**: 单个场景对象（同3.1）

### 3.3 创建告警场景

**接口地址**: `POST /iot/alert/scene/create`

**请求体**:
```typescript
{
  sceneName: string             // 场景名称（必填）
  sceneType: string             // 场景类型（必填）
  gatewayId: number             // 门户ID（必填）
  alertLevel: number            // 告警等级（必填，1-5）
  triggerDuration: number       // 触发时间（必填）
  status: number                // 状态（0-禁用 1-启用）
  ratingType: string            // 评级类型（auto, manual）
  rules: [                      // 触发规则列表
    {
      enabled: boolean          // 是否启用
      parameter: string         // 参数
      condition: string         // 条件
      threshold: number         // 阈值
    }
  ],
  evaluationRule: string        // 评估规则（any, all）
  statisticsDuration: number    // 统计时长(秒)
  thresholdType: string         // 阈值类型
  bmwThreshold: number          // 宝马规则上限阈值(秒)
  notifyMethod: string[]        // 通知方式
  notifyUsers: number[]         // 通知用户ID列表
  remark?: string               // 备注
}
```

**响应数据**:
```typescript
{
  code: 0,
  data: number                  // 新创建的场景ID
}
```

### 3.4 更新告警场景

**接口地址**: `PUT /iot/alert/scene/update`

**请求体**: 同3.3，需包含`id`字段

### 3.5 删除告警场景

**接口地址**: `DELETE /iot/alert/scene/delete`

**请求体**:
```typescript
{
  ids: number[]                 // 场景ID列表
}
```

### 3.6 复制告警场景

**接口地址**: `POST /iot/alert/scene/copy`

**请求体**:
```typescript
{
  id: number                    // 场景ID
}
```

**响应数据**:
```typescript
{
  code: 0,
  data: number                  // 新创建的场景ID
}
```

---

## 4. 用户消息管理接口

### 4.1 获取消息列表

**接口地址**: `GET /iot/alert/message/list`

**请求参数**:
```typescript
{
  pageNo: number              // 页码
  pageSize: number            // 每页数量
  messageType?: string        // 消息类型（alert, system, device）
  readStatus?: number         // 阅读状态（0-未读, 1-已读）
  dateRange?: string[]        // 时间范围 [开始时间, 结束时间]
}
```

**响应数据**:
```typescript
{
  code: 0,
  data: {
    list: [
      {
        id: number              // 消息ID
        messageType: string     // 消息类型
        title: string           // 标题
        content: string         // 内容
        level: string           // 级别（high, medium, low）
        readStatus: number      // 阅读状态（0-未读, 1-已读）
        deviceInfo?: string     // 设备信息
        createTime: string      // 创建时间
        readTime?: string       // 阅读时间
      }
    ],
    total: number             // 总数
  }
}
```

### 4.2 标记消息为已读

**接口地址**: `PUT /iot/alert/message/mark-read`

**请求体**:
```typescript
{
  id: number                  // 消息ID
}
```

### 4.3 全部标记为已读

**接口地址**: `PUT /iot/alert/message/mark-all-read`

**请求体**: 无

### 4.4 删除消息

**接口地址**: `DELETE /iot/alert/message/delete`

**请求体**:
```typescript
{
  ids: number[]               // 消息ID列表
}
```

---

## 5. 告警日志管理接口

### 5.1 获取告警日志列表

**接口地址**: `GET /iot/alert/log/list`

**请求参数**:
```typescript
{
  pageNo: number              // 页码
  pageSize: number            // 每页数量
  sceneId?: number            // 场景ID
  deviceKey?: string          // 设备标识
  alertLevel?: number         // 告警级别（1-5）
  handleStatus?: number       // 处理状态（0-未处理, 1-处理中, 2-已处理）
  dateRange?: string[]        // 时间范围 [开始时间, 结束时间]
}
```

**响应数据**:
```typescript
{
  code: 0,
  data: {
    list: [
      {
        id: number              // 日志ID
        sceneId: number         // 场景ID
        sceneName: string       // 场景名称
        deviceKey: string       // 设备标识
        deviceName: string      // 设备名称
        alertLevel: number      // 告警级别（1-5）
        alertParams: object     // 告警参数对象
        threshold: number       // 阈值
        actualValue: number     // 实际值
        handleStatus: number    // 处理状态（0-未处理, 1-处理中, 2-已处理）
        handleUser?: string     // 处理人
        handleTime?: string     // 处理时间
        handleRemark?: string   // 处理备注
        createTime: string      // 创建时间
      }
    ],
    total: number             // 总数
  }
}
```

### 5.2 获取告警日志详情

**接口地址**: `GET /iot/alert/log/{id}`

**路径参数**:
- `id`: 日志ID

**响应数据**: 单个日志对象（同5.1）

### 5.3 处理告警

**接口地址**: `PUT /iot/alert/log/process`

**请求体**:
```typescript
{
  id: number                  // 日志ID
  handleStatus: number        // 处理状态（1-处理中, 2-已处理）
  handleRemark: string        // 处理备注（必填）
}
```

### 5.4 删除告警日志

**接口地址**: `DELETE /iot/alert/log/delete`

**请求体**:
```typescript
{
  ids: number[]               // 日志ID列表
}
```

### 5.5 导出告警日志

**接口地址**: `GET /iot/alert/log/export`

**请求参数**: 同5.1

**响应**: Excel文件下载

---

## 6. 统计分析接口

### 6.1 获取告警统计数据

**接口地址**: `GET /iot/alert/statistics`

**请求参数**:
```typescript
{
  timeRange?: string          // 时间范围（1h, 6h, 12h, 24h, 7d, 30d）
  deviceKey?: string          // 设备标识
}
```

**响应数据**:
```typescript
{
  code: 0,
  data: {
    total: number             // 总告警数
    unhandled: number         // 未处理数
    processing: number        // 处理中数
    handled: number           // 已处理数
    levelDistribution: {      // 级别分布
      level1: number,
      level2: number,
      level3: number,
      level4: number,
      level5: number
    }
  }
}
```

### 6.2 获取告警趋势数据

**接口地址**: `GET /iot/alert/trend`

**请求参数**:
```typescript
{
  timeRange?: string          // 时间范围
  type?: string               // 趋势类型（day, week, month）
}
```

**响应数据**:
```typescript
{
  code: 0,
  data: [
    {
      time: string            // 时间点
      count: number           // 告警数量
      level1: number,         // 1级告警数
      level2: number,         // 2级告警数
      level3: number,         // 3级告警数
      level4: number,         // 4级告警数
      level5: number          // 5级告警数
    }
  ]
}
```

---

## 附录

### A. 通用响应结构

所有接口都遵循以下响应结构：

```typescript
{
  code: number              // 状态码（0-成功，其他-失败）
  data: any                 // 响应数据
  msg: string               // 响应消息
}
```

### B. 错误码说明

| 错误码 | 说明 |
|--------|------|
| 0 | 成功 |
| 400 | 参数错误 |
| 401 | 未授权 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### C. 参数说明

#### 告警参数类型
- `amplitude`: 幅度 (dB)
- `energy`: 能量 (Kcal)
- `rms`: RMS (mV)

#### 条件类型
- `gt`: 大于
- `lt`: 小于
- `eq`: 等于
- `gte`: 大于等于
- `lte`: 小于等于

#### 时间范围
- `1h`: 最近1小时
- `6h`: 最近6小时
- `12h`: 最近12小时
- `24h`: 最近24小时
- `7d`: 最近7天
- `30d`: 最近30天

---

**文档维护**: 开发团队  
**联系方式**: dev@example.com

