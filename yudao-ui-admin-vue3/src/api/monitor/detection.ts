import request from '@/config/axios'

// 检测 API
export const DetectionApi = {
  // 上传文件并检测
  uploadAndDetect: (file: File, algorithm: string) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('algorithm', algorithm)
    return request.post({ 
      url: '/detection/realtime/upload', 
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  // 获取任务状态
  getTaskStatus: (taskId: string) => {
    return request.get({ url: '/detection/realtime/task-status', params: { taskId } })
  },

  // 获取任务检测结果
  getTaskResults: (taskId: string) => {
    return request.get({ url: '/detection/realtime/results', params: { taskId } })
  },

  // 获取实时处理统计 (从 Flink 获取)
  getRealtimeStats: () => {
    return request.get({ url: '/detection/stats' })
  },

  // 获取历史异常记录
  getAnomalyHistory: (params: any) => {
    return request.get({ url: '/detection/history', params })
  }
}
