/**
 * 数据源适配器
 * 将不同数据源（实时、历史、上传）统一为标准格式
 */

import axios from 'axios'

export interface RawSignalData {
  time: number[]
  signal: number[]
  noisy?: number[]
  filtered?: number[]
  sampleRate: number
  source: 'realtime' | 'history' | 'upload'
  metadata?: any
}

export interface StandardSignalData {
  time: number[]
  original: number[]  // 原始信号（或干净信号）
  noisy?: number[]    // 加噪信号（如果有）
  sampleRate: number
  source: 'realtime' | 'history' | 'upload'
  metadata: {
    fileName?: string
    fileSize?: number
    duration?: number
    channels?: number
    device?: string
  }
}

class DataAdapter {
  private apiBase = typeof window !== 'undefined' ? `${window.location.origin}/api/tdms` : 'http://localhost:3002/api/tdms'
  private wsUrl = typeof window !== 'undefined' 
    ? `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/realtime`
    : 'ws://localhost:8081/realtime'
  private ws: WebSocket | null = null

  /**
   * 从历史TDMS文件加载数据
   */
  async loadHistoryData(folder: string): Promise<StandardSignalData> {
    try {
      const response = await axios.post(`${this.apiBase}/analyze-folder`, {
        folder,
        sampleRate: 100000,
        cutoffFreq: 5000,
        filterOrder: 6
      })

      const data = response.data

      return {
        time: data.signals.time,
        original: data.signals.sine,
        noisy: data.signals.noisy,
        sampleRate: data.parameters.sampleRate,
        source: 'history',
        metadata: {
          fileName: folder,
          duration: data.signals.time.length / data.parameters.sampleRate,
          channels: 4
        }
      }
    } catch (error) {
      console.error('加载历史数据失败:', error)
      throw error
    }
  }

  /**
   * 从上传文件加载数据
   */
  async loadUploadData(file: File): Promise<StandardSignalData> {
    const formData = new FormData()
    formData.append('file', file)

    try {
      const response = await axios.post(`${this.apiBase}/upload`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })

      return {
        time: response.data.time,
        original: response.data.signal,
        sampleRate: response.data.sampleRate,
        source: 'upload',
        metadata: {
          fileName: file.name,
          fileSize: file.size
        }
      }
    } catch (error) {
      console.error('上传文件失败:', error)
      throw error
    }
  }

  /**
   * 连接实时数据流
   */
  connectRealtimeData(
    deviceId: string,
    onData: (data: StandardSignalData) => void,
    onError: (error: any) => void
  ): void {
    if (this.ws) {
      this.ws.close()
    }

    try {
      this.ws = new WebSocket(this.wsUrl)

      this.ws.onopen = () => {
        console.log('WebSocket连接成功')
        // 订阅指定设备
        this.ws?.send(JSON.stringify({ type: 'subscribe', deviceId }))
      }

      this.ws.onmessage = (event) => {
        try {
          const rawData = JSON.parse(event.data)

          // 转换为标准格式
          const standardData: StandardSignalData = {
            time: rawData.time || [],
            original: rawData.noisy || rawData.signal || [],
            noisy: rawData.noisy,
            sampleRate: rawData.sampleRate || 100000,
            source: 'realtime',
            metadata: {
              device: deviceId
            }
          }

          onData(standardData)
        } catch (error) {
          console.error('解析实时数据失败:', error)
        }
      }

      this.ws.onerror = (error) => {
        console.error('WebSocket错误:', error)
        onError(error)
      }

      this.ws.onclose = () => {
        console.log('WebSocket连接关闭')
      }
    } catch (error) {
      console.error('WebSocket连接失败:', error)
      onError(error)
    }
  }

  /**
   * 断开实时数据流
   */
  disconnectRealtimeData(): void {
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
  }

  /**
   * 获取可用设备列表
   */
  async getAvailableDevices(): Promise<Array<{ id: string; name: string }>> {
    // 模拟数据，实际应从后端获取
    return [
      { id: 'DEVICE_001', name: '设备 001' },
      { id: 'DEVICE_002', name: '设备 002' },
      { id: 'DEVICE_TEST', name: '测试设备' }
    ]
  }

  /**
   * 获取可用历史文件
   */
  async getAvailableHistoryFiles(): Promise<Array<{ id: string; name: string; size: number }>> {
    try {
      await axios.get(`${this.apiBase}/files`)
      return [
        { id: 'signal-1', name: 'Signal-1 (单文件多通道)', size: 3200487 },
        { id: 'signal-2', name: 'Signal-2 (多文件组合)', size: 3200418 }
      ]
    } catch (error) {
      console.error('获取文件列表失败:', error)
      return []
    }
  }
}

export const dataAdapter = new DataAdapter()
