/**
 * IoT WebSocket管理类
 * 用于实时接收设备数据和告警
 */

export class IoTWebSocket {
  private ws: WebSocket | null = null
  private reconnectTimer: any = null
  private heartbeatTimer: any = null
  private url: string
  private token: string
  private listeners: Map<string, Function[]> = new Map()
  private reconnectAttempts = 0
  private maxReconnectAttempts = 5

  constructor(url: string, token: string) {
    this.url = url
    this.token = token
  }

  /**
   * 连接WebSocket
   */
  connect(): void {
    try {
      const wsUrl = `${this.url}?token=${this.token}`
      this.ws = new WebSocket(wsUrl)
      
      this.ws.onopen = this.onOpen.bind(this)
      this.ws.onmessage = this.onMessage.bind(this)
      this.ws.onerror = this.onError.bind(this)
      this.ws.onclose = this.onClose.bind(this)
      
      console.log('[WebSocket] 正在连接...', wsUrl)
    } catch (error) {
      console.error('[WebSocket] 连接失败:', error)
      this.emit('error', error)
    }
  }

  /**
   * 订阅设备数据
   */
  subscribe(deviceId: string, params: string[]): void {
    this.send({
      type: 'subscribe',
      deviceId,
      params
    })
    console.log(`[WebSocket] 已订阅设备: ${deviceId}`)
  }

  /**
   * 取消订阅设备
   */
  unsubscribe(deviceId: string): void {
    this.send({
      type: 'unsubscribe',
      deviceId
    })
    console.log(`[WebSocket] 已取消订阅: ${deviceId}`)
  }

  /**
   * 发送消息
   */
  send(data: any): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
    } else {
      console.warn('[WebSocket] 连接未就绪，无法发送消息')
    }
  }

  /**
   * 监听消息类型
   */
  on(type: string, callback: Function): void {
    if (!this.listeners.has(type)) {
      this.listeners.set(type, [])
    }
    this.listeners.get(type)!.push(callback)
  }

  /**
   * 移除监听器
   */
  off(type: string, callback?: Function): void {
    if (!callback) {
      this.listeners.delete(type)
    } else {
      const callbacks = this.listeners.get(type) || []
      const index = callbacks.indexOf(callback)
      if (index > -1) {
        callbacks.splice(index, 1)
      }
    }
  }

  /**
   * 断开连接
   */
  disconnect(): void {
    console.log('[WebSocket] 主动断开连接')
    
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    
    this.stopHeartbeat()
    
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    
    this.reconnectAttempts = 0
  }

  // ========== 私有方法 ==========

  private onOpen(): void {
    console.log('[WebSocket] 连接成功')
    this.reconnectAttempts = 0
    this.startHeartbeat()
    this.emit('connected', {})
  }

  private onMessage(event: MessageEvent): void {
    try {
      const message = JSON.parse(event.data)
      console.log('[WebSocket] 收到消息:', message.type)
      
      // 触发对应的监听器
      this.emit(message.type, message)
      
      // 触发通用消息监听器
      this.emit('message', message)
    } catch (error) {
      console.error('[WebSocket] 消息解析失败:', error)
    }
  }

  private onError(error: Event): void {
    console.error('[WebSocket] 连接错误:', error)
    this.emit('error', error)
  }

  private onClose(event: CloseEvent): void {
    console.log('[WebSocket] 连接关闭:', event.code, event.reason)
    this.stopHeartbeat()
    this.emit('disconnected', event)
    
    // 尝试重连
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnect()
    } else {
      console.error('[WebSocket] 达到最大重连次数，停止重连')
      this.emit('reconnect_failed', {})
    }
  }

  private emit(type: string, data: any): void {
    const callbacks = this.listeners.get(type) || []
    callbacks.forEach(cb => {
      try {
        cb(data)
      } catch (error) {
        console.error(`[WebSocket] 回调函数执行失败 (${type}):`, error)
      }
    })
  }

  private startHeartbeat(): void {
    this.heartbeatTimer = setInterval(() => {
      this.send({ type: 'ping' })
    }, 30000) // 每30秒发送心跳
    
    console.log('[WebSocket] 心跳已启动')
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
      console.log('[WebSocket] 心跳已停止')
    }
  }

  private reconnect(): void {
    if (this.reconnectTimer) return
    
    this.reconnectAttempts++
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000) // 指数退避，最大30秒
    
    console.log(`[WebSocket] ${delay/1000}秒后尝试第${this.reconnectAttempts}次重连...`)
    
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, delay)
  }

  /**
   * 获取连接状态
   */
  getReadyState(): number {
    return this.ws?.readyState ?? WebSocket.CLOSED
  }

  /**
   * 是否已连接
   */
  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN
  }
}

export default IoTWebSocket

