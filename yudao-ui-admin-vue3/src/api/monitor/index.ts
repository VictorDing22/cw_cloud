import request from '@/config/axios'

export type FilterType = 'LMS' | 'KALMAN'

export interface MonitorUploadResp {
  jobId: string
  websocketPath: string
  playbackSeconds: number
  channel: {
    name: string
    unit: string
    sampleRate: number
    startTimestamp: number
    endTimestamp: number
    sampleCount: number
  }
}

export interface HistoryAnalysisPoint {
  timestamp: number
  rawValue: number
  filteredValue: number
  residualValue: number
  isAnomaly: boolean
  anomalyType?: string
  channelName: string
}

export interface MonitorStreamMessage {
  jobId: string
  timestamp: number
  originalValue: number
  filteredValue: number
  anomaly: boolean
  energy: number
  snrBeforeDb: number
  snrAfterDb: number
  snrDeltaDb: number
  throughputKps: number
  processingDelayMs: number
  anomalyCount: number
  channel: {
    name: string
    unit: string
    sampleRate: number
    startTimestamp: number
    endTimestamp: number
    sampleCount: number
  }
}

export interface HistoryAnalysisResult {
  channel: {
    name: string
    unit: string
  }
  points: HistoryAnalysisPoint[]
  anomalyCount: number
}

export type KalmanParams = {
  kalmanQ?: number
  kalmanR?: number
  kalmanP0?: number
  kalmanX0N?: number
}

export type FilterParams = {
  filterType?: FilterType
} & KalmanParams

const appendFilterParams = (formData: FormData, params?: FilterParams) => {
  if (!params) return
  if (params.filterType) formData.append('filterType', params.filterType)
  if (params.kalmanQ != null) formData.append('kalmanQ', String(params.kalmanQ))
  if (params.kalmanR != null) formData.append('kalmanR', String(params.kalmanR))
  if (params.kalmanP0 != null) formData.append('kalmanP0', String(params.kalmanP0))
  if (params.kalmanX0N != null) formData.append('kalmanX0N', String(params.kalmanX0N))
}

export const uploadTdms = (formData: FormData, filterParams?: FilterParams) => {
  // 必须用 multipart/form-data，否则后端收不到 file
  appendFilterParams(formData, filterParams)
  return request.post<MonitorUploadResp>({
    url: '/monitor/upload',
    data: formData,
    headersType: 'multipart/form-data'
  })
}

export const analyzeTdmsHistory = (formData: FormData, filterParams?: FilterParams) => {
  appendFilterParams(formData, filterParams)
  return request.post<HistoryAnalysisResult>({
    url: '/monitor/history/analyze',
    data: formData,
    headersType: 'multipart/form-data'
  })
}

export const updateAnomalyConfig = (jobId: string, threshold: number, enabled: boolean) => {
  return request.post({ url: `/monitor/${jobId}/anomaly`, params: { threshold, enabled } })
}

export const updateFilterConfig = (jobId: string, filterParams: FilterParams) => {
  return request.post({ url: `/monitor/${jobId}/filter`, params: filterParams })
}

export const stopMonitorJob = (jobId: string) => {
  return request.delete({ url: `/monitor/${jobId}` })
}

export const analyzeRealtime = (formData: FormData, filterParams?: FilterParams) => {
  appendFilterParams(formData, filterParams)
  return request.post<HistoryAnalysisResult>({
    url: '/monitor/realtime/analyze',
    data: formData,
    headersType: 'multipart/form-data'
  })
}

/**
 * 流式传输版本的实时分析接口
 * 使用 NDJSON 格式，逐行返回 JSON 对象
 * 第一行是元数据，后续行是 points 数组（每批 1000 个点）
 */
export const analyzeRealtimeStream = async (
  formData: FormData,
  filterParams?: FilterParams,
  onProgress?: (progress: { metadata?: any; points: HistoryAnalysisPoint[]; totalPoints: number; receivedPoints: number }) => void
): Promise<HistoryAnalysisResult> => {
  appendFilterParams(formData, filterParams)
  
  const baseURL = import.meta.env.DEV ? '/admin-api' : import.meta.env.VITE_BASE_URL + import.meta.env.VITE_API_URL
  const url = `${baseURL}/monitor/realtime/analyze-stream`
  
  // 获取 token
  const { getAccessToken, getTenantId } = await import('@/utils/auth')
  const token = getAccessToken()
  const tenantId = getTenantId()
  
  // 构建 headers
  const headers: HeadersInit = {}
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  if (tenantId) {
    headers['tenant-id'] = tenantId
  }
  
  // 使用 fetch API 进行流式读取
  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: formData
  })
  
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }
  
  // 读取流式响应
  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('无法读取响应流')
  }
  
  const decoder = new TextDecoder()
  let buffer = ''
  let metadata: any = null
  const allPoints: HistoryAnalysisPoint[] = []
  let receivedPoints = 0
  
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    
    buffer += decoder.decode(value, { stream: true })
    
    // 按行分割处理
    const lines = buffer.split('\n')
    buffer = lines.pop() || '' // 保留最后不完整的行
    
    for (const line of lines) {
      if (!line.trim()) continue
      
      try {
        const json = JSON.parse(line)
        
        // 第一行是元数据
        if (json.type === 'metadata') {
          metadata = json
          if (onProgress) {
            onProgress({
              metadata,
              points: [],
              totalPoints: json.pointCount || 0,
              receivedPoints: 0
            })
          }
        } else if (Array.isArray(json)) {
          // 后续行是 points 数组
          allPoints.push(...json)
          receivedPoints += json.length
          
          if (onProgress) {
            onProgress({
              metadata,
              points: json,
              totalPoints: metadata?.pointCount || 0,
              receivedPoints
            })
          }
        }
      } catch (e) {
        console.warn('解析 JSON 行失败:', line, e)
      }
    }
  }
  
  // 处理最后一行
  if (buffer.trim()) {
    try {
      const json = JSON.parse(buffer)
      if (json.type === 'metadata') {
        metadata = json
      } else if (Array.isArray(json)) {
        allPoints.push(...json)
        receivedPoints += json.length
      }
    } catch (e) {
      console.warn('解析最后一行 JSON 失败:', buffer, e)
    }
  }
  
  // 构建最终结果
  if (!metadata) {
    throw new Error('未收到元数据')
  }
  
  return {
    channel: metadata.channel,
    points: allPoints,
    anomalyCount: metadata.anomalyCount || 0
  }
}
