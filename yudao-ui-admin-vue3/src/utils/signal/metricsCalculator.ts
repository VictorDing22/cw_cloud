/**
 * 信号指标计算器
 * 统一计算各种性能指标：MSE、SNR、相关系数等
 */

export interface SignalMetrics {
  // 均方误差相关
  mseBefore: number
  mseAfter: number
  mseImprovement: number  // 百分比

  // 信噪比相关
  snrBefore?: number
  snrAfter?: number
  snrImprovement?: number  // dB

  // 相关系数
  correlationBefore: number
  correlationAfter: number

  // 处理性能
  processingTime?: number  // ms
  throughput?: number      // samples/s

  // 异常检测
  anomalyCount?: number
  residualAnomalies?: number
  suddenChangeAnomalies?: number
}

class MetricsCalculator {
  /**
   * 计算均方误差 (MSE)
   */
  calculateMSE(signal1: number[], signal2: number[]): number {
    if (signal1.length !== signal2.length) {
      throw new Error('信号长度不匹配')
    }

    let sum = 0
    for (let i = 0; i < signal1.length; i++) {
      const diff = signal1[i] - signal2[i]
      sum += diff * diff
    }

    return sum / signal1.length
  }

  /**
   * 计算信噪比 (SNR) - dB
   */
  calculateSNR(signal: number[], noise: number[]): number {
    const signalPower = this.calculatePower(signal)
    const noisePower = this.calculatePower(noise)

    if (noisePower === 0) return Infinity

    return 10 * Math.log10(signalPower / noisePower)
  }

  /**
   * 计算信号功率
   */
  private calculatePower(signal: number[]): number {
    let sum = 0
    for (let i = 0; i < signal.length; i++) {
      sum += signal[i] * signal[i]
    }
    return sum / signal.length
  }

  /**
   * 计算相关系数
   */
  calculateCorrelation(signal1: number[], signal2: number[]): number {
    if (signal1.length !== signal2.length) {
      throw new Error('信号长度不匹配')
    }

    const n = signal1.length
    let sum1 = 0,
      sum2 = 0,
      sum1Sq = 0,
      sum2Sq = 0,
      pSum = 0

    for (let i = 0; i < n; i++) {
      sum1 += signal1[i]
      sum2 += signal2[i]
      sum1Sq += signal1[i] * signal1[i]
      sum2Sq += signal2[i] * signal2[i]
      pSum += signal1[i] * signal2[i]
    }

    const num = pSum - (sum1 * sum2) / n
    const den = Math.sqrt((sum1Sq - (sum1 * sum1) / n) * (sum2Sq - (sum2 * sum2) / n))

    if (den === 0) return 0

    return num / den
  }

  /**
   * 计算综合指标
   */
  calculateComprehensiveMetrics(
    original: number[],
    noisy: number[],
    filtered: number[],
    processingTime?: number
  ): SignalMetrics {
    // 计算MSE
    const mseBefore = this.calculateMSE(original, noisy)
    const mseAfter = this.calculateMSE(original, filtered)
    const mseImprovement = mseBefore > 0 ? ((mseBefore - mseAfter) / mseBefore) * 100 : 0

    // 计算相关系数
    const correlationBefore = this.calculateCorrelation(original, noisy)
    const correlationAfter = this.calculateCorrelation(original, filtered)

    // 计算SNR（如果可能）
    let snrBefore: number | undefined
    let snrAfter: number | undefined
    let snrImprovement: number | undefined

    try {
      // 假设噪声 = noisy - original
      const noiseBefore = noisy.map((v, i) => v - original[i])
      const noiseAfter = filtered.map((v, i) => v - original[i])

      snrBefore = this.calculateSNR(original, noiseBefore)
      snrAfter = this.calculateSNR(original, noiseAfter)
      snrImprovement = snrAfter - snrBefore
    } catch (error) {
      // SNR计算失败，忽略
    }

    return {
      mseBefore,
      mseAfter,
      mseImprovement,
      snrBefore,
      snrAfter,
      snrImprovement,
      correlationBefore,
      correlationAfter,
      processingTime
    }
  }

  /**
   * 实时指标更新（用于实时数据流）
   */
  calculateRealtimeMetrics(
    currentError: number,
    throughput: number,
    snrImprovement: number
  ): Partial<SignalMetrics> {
    return {
      mseAfter: currentError,
      throughput,
      snrImprovement
    }
  }

  /**
   * 格式化指标显示
   */
  formatMetrics(metrics: SignalMetrics): Record<string, string> {
    return {
      'MSE改善': `${metrics.mseImprovement.toFixed(2)}%`,
      'MSE滤波前': metrics.mseBefore.toFixed(6),
      'MSE滤波后': metrics.mseAfter.toFixed(6),
      '相关系数(前)': metrics.correlationBefore.toFixed(4),
      '相关系数(后)': metrics.correlationAfter.toFixed(4),
      ...(metrics.snrImprovement !== undefined && {
        'SNR改善': `${metrics.snrImprovement.toFixed(2)} dB`
      }),
      ...(metrics.processingTime !== undefined && {
        '处理时间': `${metrics.processingTime.toFixed(2)} ms`
      }),
      ...(metrics.throughput !== undefined && {
        '处理速度': `${metrics.throughput.toFixed(0)} samples/s`
      })
    }
  }
}

export const metricsCalculator = new MetricsCalculator()
