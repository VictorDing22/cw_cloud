import request from '@/config/axios'

// ============ 类型定义 ============

export interface Device {
  id: string
  name: string
  product: string
  status: 'online' | 'offline' | 'warning'
  collecting: boolean
  version: string
  dataCount: number
  alertCount: number
  lastUpdate: number
  createTime: number
}

export interface DevicePageReq {
  deviceName?: string
  product?: string
  status?: string
  pageNo: number
  pageSize: number
}

export interface AEData {
  id: number
  deviceId: string
  timestamp: number
  duration: number
  ringCount: number
  riseTime: number
  riseCount: number
  amplitude: number
  avgSignalLevel: number
  energy: number
  rms: number
}

export interface AEDataPageReq {
  deviceId?: string
  startTime?: number
  endTime?: number
  param?: string
  pageNo: number
  pageSize: number
}

export interface Alert {
  id: string
  deviceId: string
  deviceName: string
  level: 'critical' | 'warning' | 'info'
  alertType: string
  message: string
  value: number
  threshold: number
  status: 'pending' | 'processing' | 'resolved'
  alertTime: number
}

export interface AlertPageReq {
  deviceId?: string
  level?: string
  status?: string
  startTime?: number
  endTime?: number
  pageNo: number
  pageSize: number
}

// ============ 设备管理API ============

// 获取设备列表
export const getDevicePage = (params: DevicePageReq) => {
  return request.get({ url: '/iot/device/page', params })
}

// 获取设备详情
export const getDeviceDetail = (id: string) => {
  return request.get({ url: '/iot/device/get', params: { id } })
}

// 启动采集
export const startCollect = (deviceId: string) => {
  return request.post({ url: '/iot/device/start-collect', data: { deviceId } })
}

// 停止采集
export const stopCollect = (deviceId: string) => {
  return request.post({ url: '/iot/device/stop-collect', data: { deviceId } })
}

// 重启设备
export const restartDevice = (deviceId: string) => {
  return request.post({ url: '/iot/device/restart', data: { deviceId } })
}

// 更新设备配置
export const updateDeviceConfig = (deviceId: string, config: any) => {
  return request.post({ url: '/iot/device/update-config', data: { deviceId, config } })
}

// 删除设备
export const deleteDevice = (id: string) => {
  return request.delete({ url: '/iot/device/delete', params: { id } })
}

// ============ 声发射数据API ============

// 获取声发射数据
export const getAEDataPage = (params: AEDataPageReq) => {
  return request.get({ url: '/iot/ae-data/page', params })
}

// 获取最新数据
export const getLatestAEData = (deviceId: string, limit: number = 100) => {
  return request.get({ url: '/iot/ae-data/latest', params: { deviceId, limit } })
}

// 删除数据
export const deleteAEData = (ids: number[]) => {
  return request.delete({ url: '/iot/ae-data/delete-batch', data: { ids } })
}

// 导出CSV
export const exportAEDataCSV = (params: any) => {
  return request.post({ 
    url: '/iot/ae-data/export/csv', 
    data: params,
    responseType: 'blob'
  })
}

// 导出SWAE
export const exportAEDataSWAE = (params: any) => {
  return request.post({ 
    url: '/iot/ae-data/export/swae', 
    data: params,
    responseType: 'blob'
  })
}

// ============ 告警管理API ============

// 获取告警列表
export const getAlertPage = (params: AlertPageReq) => {
  return request.get({ url: '/iot/alert/page', params })
}

// 获取告警统计
export const getAlertStatistics = () => {
  return request.get({ url: '/iot/alert/statistics' })
}

// 处理告警
export const processAlert = (alertId: string, handleNote: string) => {
  return request.post({ url: '/iot/alert/process', data: { alertId, handleNote } })
}

// 批量处理告警
export const batchProcessAlert = (alertIds: string[], handleNote: string) => {
  return request.post({ url: '/iot/alert/batch-process', data: { alertIds, handleNote } })
}

// 删除告警
export const deleteAlert = (id: string) => {
  return request.delete({ url: '/iot/alert/delete', params: { id } })
}

// ============ 滤波算法API ============

export interface FilterRequest {
  filterType: 'LMS' | 'NLMS' | 'Kalman'
  filterOrder: number
  stepSize: number
  originalSignal: number[]
  noiseSignal: number[]
  desiredSignal: number[]
}

// 调用滤波算法（使用现有的SimpleFilterServer）
export const callFilterAlgorithm = (data: FilterRequest) => {
  // 注意：这个API是独立的滤波服务，不走网关
  return request.post({ 
    url: 'http://localhost:48083/filter-api/process/adaptive-filter',
    data 
  })
}

// 异常检测
export const detectAnomaly = (signal: number[], threshold: number, windowSize: number = 10) => {
  return request.post({
    url: 'http://localhost:48083/filter-api/anomaly/detect',
    data: { signal, threshold, windowSize }
  })
}

// ============ 声发射评级API ============

export interface RatingData {
  deviceId: string
  deviceName: string
  rating: 'excellent' | 'good' | 'fair' | 'poor'
  ratingStars: number
  healthScore: number
  remainingLife: number
  evaluateTime: number
}

// 计算设备评级
export const calculateRating = (deviceId: string, startTime?: number, endTime?: number) => {
  return request.post({ 
    url: '/iot/rating/calculate', 
    data: { deviceId, startTime, endTime } 
  })
}

// 获取评级历史
export const getRatingHistory = (deviceId: string, limit: number = 30) => {
  return request.get({ 
    url: '/iot/rating/history', 
    params: { deviceId, limit } 
  })
}

// 获取评级概览
export const getRatingOverview = () => {
  return request.get({ url: '/iot/rating/overview' })
}

// ============ 振动数据API ============

export interface VibrationData {
  deviceId: string
  axis: 'x' | 'y' | 'z'
  sampleRate: number
  samples: number
  timeData: number[]
  timestamp: number
}

// 获取振动数据
export const getVibrationData = (deviceId: string, axis: string, startTime: number, samples: number = 1000) => {
  return request.get({ 
    url: '/iot/vibration/data', 
    params: { deviceId, axis, startTime, samples } 
  })
}

// FFT频谱分析
export const calculateFFT = (timeData: number[], sampleRate: number) => {
  return request.post({ 
    url: '/iot/vibration/fft', 
    data: { timeData, sampleRate } 
  })
}

// 振动统计分析
export const getVibrationStatistics = (deviceId: string, axis: string, startTime?: number, endTime?: number) => {
  return request.get({ 
    url: '/iot/vibration/statistics', 
    params: { deviceId, axis, startTime, endTime } 
  })
}

