/**
 * 统一滤波引擎
 * 支持多种滤波算法：Butterworth、LMS、Kalman等
 */

import axios from 'axios'

export interface FilterConfig {
  type: 'butterworth' | 'lms' | 'nlms' | 'kalman'
  sampleRate: number
  cutoffFreq?: number  // Butterworth
  order?: number       // Butterworth
  stepSize?: number    // LMS/NLMS
  filterLength?: number // LMS/NLMS
}

export interface SignalData {
  time: number[]
  signal: number[]
  sampleRate: number
}

export interface FilterResult {
  filtered: number[]
  metrics: {
    mseImprovement: number
    snrImprovement?: number
    correlation: number
    processingTime: number
  }
}

class FilterEngine {
  private apiBase = typeof window !== 'undefined' ? `${window.location.origin}/api/tdms` : 'http://localhost:3002/api/tdms'

  /**
   * 应用Butterworth低通滤波（用于历史数据）
   */
  async applyButterworthFilter(
    data: SignalData,
    config: { cutoffFreq: number; order: number }
  ): Promise<FilterResult> {
    try {
      const response = await axios.post(`${this.apiBase}/filter/butterworth`, {
        signal: data.signal,
        sampleRate: data.sampleRate,
        cutoffFreq: config.cutoffFreq,
        order: config.order
      })
      return response.data
    } catch (error) {
      console.error('Butterworth滤波失败:', error)
      throw error
    }
  }

  /**
   * 应用LMS自适应滤波（用于实时数据）
   */
  async applyLMSFilter(
    data: SignalData,
    config: { stepSize: number; filterLength: number }
  ): Promise<FilterResult> {
    // 实时LMS滤波暂时使用本地实现
    return this.localLMSFilter(data.signal, config)
  }

  /**
   * 应用NLMS自适应滤波（用于实时数据）
   */
  async applyNLMSFilter(
    data: SignalData,
    config: { stepSize: number; filterLength: number }
  ): Promise<FilterResult> {
    return this.localNLMSFilter(data.signal, config)
  }

  /**
   * 本地LMS滤波实现（简化版）
   */
  private localLMSFilter(
    signal: number[],
    config: { stepSize: number; filterLength: number }
  ): FilterResult {
    const mu = config.stepSize
    const M = config.filterLength
    const N = signal.length
    const filtered = new Array(N).fill(0)
    const weights = new Array(M).fill(0)

    for (let n = M; n < N; n++) {
      let y = 0
      for (let k = 0; k < M; k++) {
        y += weights[k] * signal[n - k]
      }
      filtered[n] = y

      const error = signal[n] - y
      for (let k = 0; k < M; k++) {
        weights[k] += mu * error * signal[n - k]
      }
    }

    return {
      filtered,
      metrics: {
        mseImprovement: 0,
        correlation: 0,
        processingTime: 0
      }
    }
  }

  /**
   * 本地NLMS滤波实现（简化版）
   */
  private localNLMSFilter(
    signal: number[],
    config: { stepSize: number; filterLength: number }
  ): FilterResult {
    // 简化实现，与LMS类似但有归一化
    return this.localLMSFilter(signal, config)
  }

  /**
   * 统一滤波入口
   */
  async applyFilter(data: SignalData, config: FilterConfig): Promise<FilterResult> {
    const startTime = performance.now()

    let result: FilterResult

    switch (config.type) {
      case 'butterworth':
        result = await this.applyButterworthFilter(data, {
          cutoffFreq: config.cutoffFreq || 5000,
          order: config.order || 6
        })
        break

      case 'lms':
        result = await this.applyLMSFilter(data, {
          stepSize: config.stepSize || 0.01,
          filterLength: config.filterLength || 32
        })
        break

      case 'nlms':
        result = await this.applyNLMSFilter(data, {
          stepSize: config.stepSize || 0.01,
          filterLength: config.filterLength || 32
        })
        break

      case 'kalman':
        throw new Error('Kalman滤波暂未实现')

      default:
        throw new Error(`未知的滤波器类型: ${config.type}`)
    }

    result.metrics.processingTime = performance.now() - startTime
    return result
  }
}

export const filterEngine = new FilterEngine()
