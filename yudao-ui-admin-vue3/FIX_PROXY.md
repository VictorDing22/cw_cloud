# Vite 代理问题修复指南

## 当前状态
✅ baseURL 配置正确：`/admin-api`（相对路径）
✅ 环境变量正确：`VITE_API_URL=/admin-api`, `VITE_BASE_URL=http://localhost:48080`
❌ 但请求仍然发送到 `127.0.0.1:3002` 而不是网关

## 问题根源
**Vite 代理配置没有生效！** 即使配置正确，如果前端服务没有重启，代理不会生效。

## 解决方案

### 步骤 1: 完全停止前端服务
1. 找到运行 `npm run dev` 的终端窗口
2. 按 `Ctrl + C` 完全停止服务
3. 确认进程已停止（端口 3002 不再被占用）

### 步骤 2: 清除缓存（可选但推荐）
```powershell
cd D:\CW_Cloud-main\yudao-ui-admin-vue3
# 删除 node_modules/.vite 缓存
Remove-Item -Recurse -Force node_modules\.vite -ErrorAction SilentlyContinue
```

### 步骤 3: 重新启动前端服务
```powershell
npm run dev
```

### 步骤 4: 验证代理是否生效

#### 4.1 检查 Vite 终端日志
启动后，应该看到：
```
[Vite Config] 代理配置:
  VITE_BASE_URL: http://localhost:48080
  VITE_API_URL: /admin-api
  代理规则: /admin-api -> http://localhost:48080
[Vite Proxy] 代理配置已加载: { target: 'http://localhost:48080', path: '/admin-api' }
```

#### 4.2 测试请求
在浏览器中测试请求，Vite 终端应该显示：
```
[Vite Proxy] 转发请求: { method: 'POST', url: '/admin-api/api/monitor/realtime/analyze', target: 'http://localhost:48080/admin-api/api/monitor/realtime/analyze' }
[Vite Proxy] 收到响应: { status: 200, url: '/admin-api/api/monitor/realtime/analyze' }
```

#### 4.3 检查浏览器 Network
- Request URL: `http://localhost:3002/admin-api/api/monitor/realtime/analyze`
- **Remote Address: `127.0.0.1:48080`** ✅（应该是网关地址，不再是 3002）

## 如果仍然不工作

### 检查 1: 确认 Vite 版本
```powershell
npm list vite
```
确保使用的是 Vite 3.x 或更高版本

### 检查 2: 检查是否有其他代理配置冲突
搜索项目中是否有其他代理配置：
```powershell
Get-ChildItem -Recurse -Include *.ts,*.js,*.json | Select-String "proxy|Proxy" | Select-Object -First 10
```

### 检查 3: 手动测试代理
在浏览器控制台运行：
```javascript
fetch('/admin-api/system/auth/get-permission-info')
  .then(r => console.log('代理工作:', r.status))
  .catch(e => console.error('代理失败:', e))
```

如果返回 401 或 200，说明代理工作；如果返回 404，说明代理没有生效。

## 关键点
⚠️ **Vite 代理配置更改后，必须重启开发服务器才能生效！**
⚠️ **相对路径请求才会走代理，绝对 URL 会绕过代理！**
