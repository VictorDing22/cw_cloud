# Vite 代理问题诊断指南

## 问题现象
- 请求 URL: `http://localhost:3002/admin-api/api/monitor/realtime/analyze`
- 远程地址: `127.0.0.1:3002` (错误，应该是 `127.0.0.1:48080`)
- 状态码: 404 Not Found

## 诊断步骤

### 步骤 1: 检查浏览器控制台
打开浏览器开发者工具 (F12) → Console 标签，查看：
1. 是否有 `[Axios Config] 配置检查` 日志？
2. `baseURL` 的值是什么？应该是 `/admin-api`（相对路径）
3. 如果看到绝对 URL（如 `http://localhost:3002`），说明配置有问题

### 步骤 2: 检查 Vite 终端日志
查看运行 `npm run dev` 的终端窗口：
1. 是否有 `Sending Request to the Target: POST /admin-api/api/monitor/realtime/analyze` 日志？
2. 如果没有，说明 Vite 代理没有生效

### 步骤 3: 检查环境变量
查看 `.env.development` 文件：
```env
VITE_API_URL=/admin-api  # 应该是相对路径，不能是绝对 URL
VITE_BASE_URL=http://localhost:48080  # 网关地址
```

### 步骤 4: 检查实际请求
在浏览器 Network 标签中：
1. 找到失败的请求
2. 查看 Request URL：应该是 `http://localhost:3002/admin-api/api/monitor/realtime/analyze`
3. 查看 Remote Address：应该是 `127.0.0.1:48080`（网关），而不是 `127.0.0.1:3002`

## 可能的原因和解决方案

### 原因 1: 前端服务没有重启
**解决方案**: 完全停止前端服务（Ctrl+C），然后重新运行 `npm run dev`

### 原因 2: baseURL 是绝对 URL
**检查方法**: 浏览器控制台查看 `[Axios Config]` 日志
**解决方案**: 确保 `VITE_API_URL=/admin-api`（相对路径）

### 原因 3: Vite 代理配置未生效
**检查方法**: Vite 终端是否有代理日志
**解决方案**: 检查 `vite.config.ts` 中的 proxy 配置

### 原因 4: 请求使用了绝对 URL
**检查方法**: 在代码中搜索 `http://localhost:3002` 或 `window.location.origin`
**解决方案**: 确保所有 API 调用使用相对路径

## 验证配置正确的标志

✅ **正确的表现**:
- 浏览器控制台: `baseURL: /admin-api`（相对路径）
- Vite 终端: `Sending Request to the Target: POST /admin-api/...`
- Network 标签: Remote Address = `127.0.0.1:48080`

❌ **错误的表现**:
- 浏览器控制台: `baseURL: http://localhost:3002`（绝对 URL）
- Vite 终端: 没有代理日志
- Network 标签: Remote Address = `127.0.0.1:3002`
