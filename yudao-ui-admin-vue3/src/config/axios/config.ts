const config: {
  base_url: string
  result_code: number | string
  default_headers: AxiosHeaders
  request_timeout: number
} = {
  /**
   * api请求基础路径
   */
  // ⚠️ 重要：开发环境必须使用相对路径，让 Vite proxy 生效
  // Vite 代理只对相对路径生效，绝对 URL 会绕过代理直接请求
  // 开发环境：使用相对路径 /admin-api，由 Vite 代理转发到网关
  // 生产环境：使用完整 URL（VITE_BASE_URL + VITE_API_URL）
  base_url: import.meta.env.DEV 
    ? (() => {
        const apiUrl = import.meta.env.VITE_API_URL || '/admin-api'
        // 如果是绝对 URL，强制转换为相对路径
        if (apiUrl.startsWith('http://') || apiUrl.startsWith('https://')) {
          console.warn('[Axios Config] VITE_API_URL 是绝对 URL，已转换为相对路径以支持 Vite 代理')
          // 提取路径部分，例如 http://localhost:48080/admin-api -> /admin-api
          try {
            const url = new URL(apiUrl)
            return url.pathname || '/admin-api'
          } catch {
            return '/admin-api'
          }
        }
        return apiUrl
      })()
    : import.meta.env.VITE_BASE_URL + import.meta.env.VITE_API_URL,
  /**
   * 接口成功返回状态码
   */
  result_code: 200,

  /**
   * 接口请求超时时间
   */
  request_timeout: 30000,

  /**
   * 默认接口请求类型
   * 可选值：application/x-www-form-urlencoded multipart/form-data
   */
  default_headers: 'application/json'
}

export { config }
