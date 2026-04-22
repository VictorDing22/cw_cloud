import request from '@/config/axios'

// ==================== 告警用户相关接口 ====================

export interface AlertUserQuery {
  pageNo: number
  pageSize: number
  contactName?: string
  gatewayId?: number
}

export interface AlertUserVO {
  id: number
  contactName: string
  contactType: string // email, phone
  gatewayId: number
  gatewayName: string
  gatewayLocation: string
  language: string // zh-CN, en-US
  phone: string
  email: string
  receiverCount: number
  status: number // 0-禁用 1-启用
  remark: string
  createTime: string
  updateTime: string
}

export interface AlertUserForm {
  id?: number
  contactName: string
  gatewayId: number
  language: string
  phone: string
  email: string
  receiverCount: number
  remark?: string
}

// 获取告警用户列表
export const getAlertUserList = (params: AlertUserQuery) => {
  return request.get<{ list: AlertUserVO[]; total: number }>({
    url: '/iot/alert/user/list',
    params
  })
}

// 获取告警用户详情
export const getAlertUser = (id: number) => {
  return request.get<AlertUserVO>({
    url: `/iot/alert/user/${id}`
  })
}

// 创建告警用户
export const createAlertUser = (data: AlertUserForm) => {
  return request.post({
    url: '/iot/alert/user/create',
    data
  })
}

// 更新告警用户
export const updateAlertUser = (data: AlertUserForm) => {
  return request.put({
    url: '/iot/alert/user/update',
    data
  })
}

// 删除告警用户
export const deleteAlertUser = (ids: number[]) => {
  return request.delete({
    url: '/iot/alert/user/delete',
    data: { ids }
  })
}

// 更新告警用户状态
export const updateAlertUserStatus = (id: number, status: number) => {
  return request.put({
    url: '/iot/alert/user/update-status',
    data: { id, status }
  })
}

// ==================== 告警场景相关接口 ====================

export interface AlertSceneQuery {
  pageNo: number
  pageSize: number
  sceneName?: string
  sceneType?: string
  gatewayId?: number
}

export interface AlertRule {
  enabled: boolean
  parameter: string // amplitude, energy, rms
  condition: string // gt, lt, eq, gte, lte
  threshold: number
}

export interface AlertSceneVO {
  id: number
  sceneName: string
  sceneType: string // intensity, temperature, other
  gatewayId: number
  gatewayName: string
  gatewayLocation: string
  alertLevel: number // 1-5级
  triggerDuration: number // 触发时间(秒)
  status: number // 0-禁用 1-启用
  ratingType: string // auto-自动评级, manual-自定义规则
  rules: AlertRule[]
  evaluationRule: string // any-任一条件, all-所有条件
  statisticsDuration: number // 统计时长(秒)
  thresholdType: string // unlimited, level1, level2, level3
  bmwThreshold: number // 宝马规则上限阈值(秒)
  notifyMethod: string[] // email, sms
  notifyUsers: number[]
  remark: string
  createTime: string
  updateTime: string
}

export interface AlertSceneForm {
  id?: number
  sceneName: string
  sceneType: string
  gatewayId: number
  alertLevel: number
  triggerDuration: number
  status: number
  ratingType: string
  rules: AlertRule[]
  evaluationRule: string
  statisticsDuration: number
  thresholdType: string
  bmwThreshold: number
  notifyMethod: string[]
  notifyUsers: number[]
  remark?: string
}

// 获取告警场景列表
export const getAlertSceneList = (params: AlertSceneQuery) => {
  return request.get<{ list: AlertSceneVO[]; total: number }>({
    url: '/iot/alert/scene/list',
    params
  })
}

// 获取告警场景详情
export const getAlertScene = (id: number) => {
  return request.get<AlertSceneVO>({
    url: `/iot/alert/scene/${id}`
  })
}

// 创建告警场景
export const createAlertScene = (data: AlertSceneForm) => {
  return request.post({
    url: '/iot/alert/scene/create',
    data
  })
}

// 更新告警场景
export const updateAlertScene = (data: AlertSceneForm) => {
  return request.put({
    url: '/iot/alert/scene/update',
    data
  })
}

// 删除告警场景
export const deleteAlertScene = (ids: number[]) => {
  return request.delete({
    url: '/iot/alert/scene/delete',
    data: { ids }
  })
}

// 复制告警场景
export const copyAlertScene = (id: number) => {
  return request.post({
    url: '/iot/alert/scene/copy',
    data: { id }
  })
}

// ==================== 用户消息相关接口 ====================

export interface AlertMessageQuery {
  pageNo: number
  pageSize: number
  messageType?: string // alert, system, device
  readStatus?: number // 0-未读, 1-已读
  dateRange?: string[]
}

export interface AlertMessageVO {
  id: number
  messageType: string // alert, system, device
  title: string
  content: string
  level: string // high, medium, low
  readStatus: number // 0-未读, 1-已读
  deviceInfo?: string
  createTime: string
  readTime?: string
}

// 获取消息列表
export const getAlertMessageList = (params: AlertMessageQuery) => {
  return request.get<{ list: AlertMessageVO[]; total: number }>({
    url: '/iot/alert/message/list',
    params
  })
}

// 标记消息为已读
export const markMessageAsRead = (id: number) => {
  return request.put({
    url: '/iot/alert/message/mark-read',
    data: { id }
  })
}

// 全部标记为已读
export const markAllMessagesAsRead = () => {
  return request.put({
    url: '/iot/alert/message/mark-all-read'
  })
}

// 删除消息
export const deleteAlertMessage = (ids: number[]) => {
  return request.delete({
    url: '/iot/alert/message/delete',
    data: { ids }
  })
}

// ==================== 告警日志相关接口 ====================

export interface AlertLogQuery {
  pageNo: number
  pageSize: number
  sceneId?: number
  deviceKey?: string
  alertLevel?: number
  handleStatus?: number // 0-未处理, 1-处理中, 2-已处理
  dateRange?: string[]
}

export interface AlertLogVO {
  id: number
  sceneId: number
  sceneName: string
  deviceKey: string
  deviceName: string
  alertLevel: number // 1-5级
  alertParams: any // 告警参数对象
  threshold: number
  actualValue: number
  handleStatus: number // 0-未处理, 1-处理中, 2-已处理
  handleUser?: string
  handleTime?: string
  handleRemark?: string
  createTime: string
}

export interface ProcessAlertLogForm {
  id: number
  handleStatus: number // 1-处理中, 2-已处理
  handleRemark: string
}

// 获取告警日志列表
export const getAlertLogList = (params: AlertLogQuery) => {
  return request.get<{ list: AlertLogVO[]; total: number }>({
    url: '/iot/alert/log/list',
    params
  })
}

// 获取告警日志详情
export const getAlertLog = (id: number) => {
  return request.get<AlertLogVO>({
    url: `/iot/alert/log/${id}`
  })
}

// 处理告警
export const processAlertLog = (data: ProcessAlertLogForm) => {
  return request.put({
    url: '/iot/alert/log/process',
    data
  })
}

// 删除告警日志
export const deleteAlertLog = (ids: number[]) => {
  return request.delete({
    url: '/iot/alert/log/delete',
    data: { ids }
  })
}

// 导出告警日志
export const exportAlertLog = (params: AlertLogQuery) => {
  return request.download({
    url: '/iot/alert/log/export',
    params
  })
}

// ==================== 统计相关接口 ====================

// 获取告警统计数据
export const getAlertStatistics = (params: { timeRange?: string; deviceKey?: string }) => {
  return request.get({
    url: '/iot/alert/statistics',
    params
  })
}

// 获取告警趋势数据
export const getAlertTrend = (params: { timeRange?: string; type?: string }) => {
  return request.get({
    url: '/iot/alert/trend',
    params
  })
}

