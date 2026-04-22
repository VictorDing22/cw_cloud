# 🚀 工业健康监测系统 - 启动脚本总结

> **最后更新**: 2025-11-29  
> **状态**: ✅ 系统已成功启动，演示模式已关闭

---

## ✅ 当前系统状态

| 服务 | 端口 | 状态 | 说明 |
|------|------|------|------|
| MySQL | 3306 | ✅ 运行中 | 数据库 |
| Redis | 6379 | ✅ 运行中 | 缓存 |
| Nacos | 8848 | ✅ 运行中 | 服务注册中心 |
| **Gateway** | **48080** | ✅ **运行中** | API网关 |
| **System** | **48081** | ✅ **运行中** | **演示模式：关闭** |
| **Infra** | **48082** | ✅ **运行中** | **演示模式：关闭** |
| **Frontend** | **3000** | ✅ **运行中** | Vue3前端 |

---

## 🎯 推荐使用的脚本（已验证）

### ✅ 主要脚本（英文，无编码问题）

#### 启动系统
```batch
START.bat  ⭐⭐⭐ 强烈推荐
```
- ✅ 已测试通过
- ✅ 自动关闭演示模式
- ✅ 完整的状态检查
- ✅ 自动打开浏览器

#### 停止系统
```batch
STOP.bat
```
- ✅ 停止所有Java和Node进程
- ✅ 简单可靠

#### 导入菜单
```batch
import-all-menus.bat
```
- ✅ 导入所有隐藏页面菜单
- ✅ 自动分配权限

#### 检查菜单状态
```batch
check-menu-status.bat
```
- ✅ 查看菜单是否已导入

---

## 📋 备用脚本（中文，已修复编码）

以下脚本的编码已从UTF-8转换为GBK，中文应该可以正常显示：

- `启动系统.bat` - 启动核心服务
- `停止系统.bat` - 停止所有服务
- `start-all.bat` - 启动完整系统（包括Kafka等）
- `stop-all.bat` - 停止完整系统
- `start-simple.bat` - 快速启动
- `restart-with-no-demo.bat` - 重启并关闭演示模式

**注意**: 如果这些脚本仍然有问题，请使用英文版本（START.bat）

---

## 🔧 编码修复工具

如果未来从GitHub同步代码后批处理脚本再次出现乱码，运行：

```powershell
powershell.exe -ExecutionPolicy Bypass -File fix-bat-encoding.ps1
```

这个脚本会自动将所有.bat文件从UTF-8转换为GBK编码。

---

## 📝 完整启动流程

### 第1步：确保基础服务运行
```bash
# 检查MySQL（端口3306）
# 检查Redis（端口6379）
```

### 第2步：启动系统
```batch
双击运行: START.bat
```

等待约2-3分钟，所有服务将自动启动。

### 第3步：访问系统
- **前端**: http://localhost:3000
  - 账号: `admin`
  - 密码: `admin123`
- **Nacos**: http://localhost:8848/nacos
  - 账号: `nacos`
  - 密码: `nacos`

### 第4步：导入菜单（可选）
```batch
双击运行: import-all-menus.bat
```

### 第5步：清除浏览器缓存
访问: http://localhost:3000/clear-and-relogin.html

点击"清除缓存并重新登录"按钮

---

## 🔍 故障排查

### 问题1: "演示模式，无法进行写操作"
**解决**: 
- 使用 `START.bat` 启动（已自动关闭演示模式）
- 或运行 `restart-with-no-demo.bat`（如编码已修复）

### 问题2: 批处理脚本显示乱码
**解决**:
```powershell
# 运行编码修复脚本
powershell.exe -ExecutionPolicy Bypass -File fix-bat-encoding.ps1
```

### 问题3: 菜单不显示
**解决**:
1. 运行 `import-all-menus.bat` 导入菜单
2. 清除浏览器缓存
3. 退出登录，重新登录

### 问题4: 服务启动失败
**检查**:
- MySQL是否运行（端口3306）
- Redis是否运行（端口6379）
- 端口是否被占用
- JAR文件是否存在（target目录下）

---

## 📂 重要文件说明

### 核心启动脚本
- ✅ `START.bat` - 主启动脚本（推荐）
- ✅ `STOP.bat` - 主停止脚本
- ✅ `import-all-menus.bat` - 菜单导入
- ✅ `check-menu-status.bat` - 菜单状态检查

### 工具脚本
- `fix-bat-encoding.ps1` - 批量修复批处理文件编码
- `quick-rebuild.bat` - 快速重新编译
- `disable-demo-mode-all.bat` - 批量关闭演示模式

### 配置文件
- `yudao-module-system-server/application-dev.yaml` - System配置（demo=false）
- `yudao-module-infra-server/application-dev.yaml` - Infra配置（demo=false）

### SQL文件
- `add-hidden-menus.sql` - 21个隐藏页面菜单
- `add-realtime-menus.sql` - 实时监控菜单
- `add-backend-filter-menu.sql` - Backend滤波服务菜单

### 文档
- `最终启动指南.md` - 详细启动说明
- `菜单导入指南.md` - 菜单导入说明
- `快速启动指南.txt` - 快速参考
- `README-启动脚本总结.md` - 本文档

---

## 🎯 关键修复总结

### ✅ 已完成的修复

1. **演示模式关闭**
   - 源码配置修改：`application-dev.yaml` (demo: false)
   - 启动参数覆盖：`-Dyudao.demo=false`
   - 状态：✅ 已生效

2. **Redis配置修复**
   - System模块：localhost:6379
   - Infra模块：localhost:6379
   - 状态：✅ 已修复并重新编译

3. **批处理脚本编码**
   - 44个.bat文件已转换为GBK编码
   - 状态：✅ 已修复

4. **启动脚本优化**
   - 创建无编码问题的英文版脚本
   - 状态：✅ START.bat 已验证可用

5. **菜单配置**
   - 21个隐藏页面菜单SQL
   - 实时监控菜单SQL
   - Backend滤波服务菜单SQL
   - 状态：✅ SQL文件已准备

---

## 💡 使用建议

### 日常开发
- **启动**: `START.bat`
- **停止**: `STOP.bat`
- **重启**: 先运行 STOP.bat，再运行 START.bat

### 菜单管理
- **导入**: `import-all-menus.bat`
- **检查**: `check-menu-status.bat`
- **清除缓存**: 访问 http://localhost:3000/clear-and-relogin.html

### 问题诊断
- 查看Nacos控制台确认服务注册
- 检查端口占用情况
- 查看服务日志（logs目录）

---

## 📞 技术支持

### 访问地址
- 前端管理后台: http://localhost:3000
- Nacos控制台: http://localhost:8848/nacos

### 默认账号
- 前端: admin / admin123
- Nacos: nacos / nacos

### 服务端口
- MySQL: 3306
- Redis: 6379
- Nacos: 8848
- Gateway: 48080
- System: 48081 (演示模式已关闭)
- Infra: 48082 (演示模式已关闭)
- Frontend: 3000

---

## ✅ 完成状态

- [x] 系统成功启动
- [x] 演示模式已关闭
- [x] Redis配置已修复
- [x] 批处理脚本编码已修复
- [x] 菜单SQL已准备
- [x] 启动脚本已优化
- [ ] 菜单导入（按需执行）
- [ ] Kafka启动（按需执行）

---

**系统已就绪，可以正常使用！** 🎉
