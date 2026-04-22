import request from '@/config/axios'

// ==================== 参数数据对比相关接口 ====================

export interface ParameterCompareQuery {
  productId: string
  deviceIds: string[]
  parameters: string[]
  timeRange?: string
  dateRange?: string[]
  pageNo?: number
  pageSize?: number
}

export interface ParameterCompareData {
  chartData: any[]
  tableData: any[]
  total: number
}

// 获取参数对比数据
export const getParameterCompareData = (params: ParameterCompareQuery) => {
  return request.get<ParameterCompareData>({
    url: '/iot/data/parameter/compare',
    params
  })
}

// 导出参数对比数据
export const exportParameterCompareData = (params: ParameterCompareQuery) => {
  return request.download({
    url: '/iot/data/parameter/export',
    params
  })
}

// ==================== 声发射数据相关接口 ====================

export interface AEDataQuery {
  deviceKey?: string
  startTime?: string
  endTime?: string
  pageNo?: number
  pageSize?: number
}

export interface AEDataVO {
  id: number
  deviceKey: string
  deviceName: string
  amplitude: number // 幅度 (dB)
  energy: number // 能量 (Kcal)
  rms: number // RMS (mV)
  frequency: number // 频率 (kHz)
  duration: number // 持续时间 (ms)
  collectTime: string
  createTime: string
}

// 获取声发射数据列表
export const getAEDataList = (params: AEDataQuery) => {
  return request.get<{ list: AEDataVO[]; total: number }>({
    url: '/iot/data/ae/list',
    params
  })
}

// 获取声发射数据详情
export const getAEDataDetail = (id: number) => {
  return request.get<AEDataVO>({
    url: `/iot/data/ae/${id}`
  })
}

// 获取声发射统计数据
export const getAEDataStatistics = (params: { deviceKey?: string; timeRange?: string }) => {
  return request.get({
    url: '/iot/data/ae/statistics',
    params
  })
}

// ==================== 振动数据相关接口 ====================

export interface VibrationDataQuery {
  deviceKey?: string
  axis?: string // x, y, z
  startTime?: string
  endTime?: string
  pageNo?: number
  pageSize?: number
}

export interface VibrationDataVO {
  id: number
  deviceKey: string
  deviceName: string
  xAxis: number
  yAxis: number
  zAxis: number
  frequency: number
  amplitude: number
  collectTime: string
  createTime: string
}

// 获取振动数据列表
export const getVibrationDataList = (params: VibrationDataQuery) => {
  return request.get<{ list: VibrationDataVO[]; total: number }>({
    url: '/iot/data/vibration/list',
    params
  })
}

// 获取振动数据统计
export const getVibrationStatistics = (params: { deviceKey?: string; timeRange?: string }) => {
  return request.get({
    url: '/iot/data/vibration/statistics',
    params
  })
}

// 获取振动异常检测结果
export const getVibrationAnomalyDetection = (params: { deviceKey: string; timeRange?: string }) => {
  return request.get({
    url: '/iot/data/vibration/anomaly',
    params
  })
}

// ==================== 相关性分析相关接口 ====================

export interface CorrelationAnalysisQuery {
  deviceKeys: string[]
  parameters: string[]
  startTime?: string
  endTime?: string
}

// 获取相关性分析数据
export const getCorrelationAnalysis = (params: CorrelationAnalysisQuery) => {
  return request.get({
    url: '/iot/data/correlation/analysis',
    params
  })
}

// 获取相关性热力图数据
export const getCorrelationHeatmap = (params: CorrelationAnalysisQuery) => {
  return request.get({
    url: '/iot/data/correlation/heatmap',
    params
  })
}

