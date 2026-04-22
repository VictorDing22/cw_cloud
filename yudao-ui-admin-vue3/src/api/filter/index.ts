import request from '@/config/axios'

export interface FilterProcessReqVO {
  filterType: string
  filterOrder: number
  stepSize: number
  originalSignal: number[]
  noiseSignal: number[]
  desiredSignal: number[]
}

export const processAdaptiveFilter = (data: FilterProcessReqVO) => {
  return request.post({ url: '/filter-api/process/adaptive-filter', data })
}

export const detectAnomalies = (data: any) => {
  return request.post({ url: '/filter-api/anomaly/detect', data })
}

export const getFilterHealth = () => {
  return request.get({ url: '/actuator/health' })
}

export function generateTestSignal(length: number, frequency: number, amplitude: number): number[] {
  const signal: number[] = []
  for (let i = 0; i < length; i++) {
    signal.push(amplitude * Math.sin(2 * Math.PI * frequency * i / length))
  }
  return signal
}

export function generateNoise(length: number, noiseLevel: number): number[] {
  const noise: number[] = []
  for (let i = 0; i < length; i++) {
    const u = Math.random()
    const v = Math.random()
    const gaussian = Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v)
    noise.push(gaussian * noiseLevel)
  }
  return noise
}

export function getSignalStats(signal: number[]): { mean: number; rms: number; max: number; min: number } {
  if (!signal || signal.length === 0) return { mean: 0, rms: 0, max: 0, min: 0 }
  const n = signal.length
  let sum = 0
  let sumSquares = 0
  let max = -Infinity
  let min = Infinity
  for (const x of signal) {
    sum += x
    sumSquares += x * x
    if (x > max) max = x
    if (x < min) min = x
  }
  return { mean: sum / n, rms: Math.sqrt(sumSquares / n), max, min }
}
