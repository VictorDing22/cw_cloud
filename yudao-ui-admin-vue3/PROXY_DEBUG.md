# Vite 代理调试指南

## 当前问题
- 前端运行在: 3003 端口
- baseURL 配置正确: `/admin-api`（相对路径）
- 但请求仍然发送到: `127.0.0.1:3003`（前端服务器）
- 期望发送到: `127.0.0.1:48080`（网关）

## 关键检查点

### 1. 检查 Vite 终端日志
**重要**: 查看运行 `npm run dev` 的终端窗口，应该看到：

#### 启动时的日志：
```
[Vite Config] 代理配置:
  VITE_BASE_URL: http://localhost:48080
  VITE_API_URL: /admin-api
  代理规则: /admin-api -> http://localhost:48080
[Vite Proxy] 代理配置已加载
[Vite Proxy] Target: http://localhost:48080
[Vite Proxy] Path: /admin-api
```

#### 请求时的日志：
```
[Vite Proxy] ✅ 转发请求到网关: POST /admin-api/api/monitor/realtime/analyze
[Vite Proxy] 完整目标 URL: http://localhost:48080/admin-api/api/monitor/realtime/analyze
[Vite Proxy] ✅ 收到网关响应: 200 /admin-api/api/monitor/realtime/analyze
```

**如果没有看到这些日志，说明代理配置没有生效！**

### 2. 可能的原因

#### 原因 A: Vite 配置没有正确加载
- 检查 `vite.config.ts` 文件是否有语法错误
- 确认 Vite 服务已完全重启

#### 原因 B: 代理配置格式问题
- Vite 5.x 的代理配置可能需要不同的格式
- 尝试移除 `configure` 函数，使用最简单的配置

#### 原因 C: 请求绕过了代理
- 虽然 baseURL 是相对路径，但可能请求时被转换成了绝对 URL
- 检查浏览器 Network 标签中的实际请求 URL

### 3. 快速测试方法

在浏览器控制台运行：
```javascript
// 测试 1: 直接 fetch 测试
fetch('/admin-api/system/auth/get-permission-info')
  .then(r => {
    console.log('✅ 代理工作，状态码:', r.status);
    console.log('Remote Address 应该是 127.0.0.1:48080');
  })
  .catch(e => {
    console.error('❌ 代理失败:', e);
  });

// 测试 2: 检查实际请求
const xhr = new XMLHttpRequest();
xhr.open('GET', '/admin-api/system/auth/get-permission-info');
xhr.onload = () => console.log('状态码:', xhr.status);
xhr.send();
```

### 4. 如果代理仍然不工作

尝试以下步骤：

1. **完全停止 Vite 服务**
   ```powershell
   # 找到运行 npm run dev 的终端，按 Ctrl+C
   # 确认进程已停止
   ```

2. **清除所有缓存**
   ```powershell
   Remove-Item -Recurse -Force node_modules\.vite
   Remove-Item -Recurse -Force node_modules\.cache
   ```

3. **简化代理配置**（临时测试）
   在 `vite.config.ts` 中，尝试最简单的配置：
   ```typescript
   proxy: {
     '/admin-api': {
       target: 'http://localhost:48080',
       changeOrigin: true,
     },
   }
   ```

4. **重新启动**
   ```powershell
   npm run dev
   ```

## 请提供以下信息

1. **Vite 终端是否有 `[Vite Proxy]` 日志？**
   - 如果有，请提供完整的日志
   - 如果没有，说明代理配置没有加载

2. **浏览器 Network 标签中的实际请求信息**
   - Request URL
   - Remote Address
   - Status Code

3. **Vite 启动时的完整日志**
   - 特别是 `[Vite Config]` 相关的日志
