# 📜 脚本使用指南

本项目提供了精简的管理脚本，方便快速启动和管理系统。

---

## 🚀 启动脚本

### 1️⃣ 快速启动（日常开发推荐）⭐

**脚本**: `quick-start.bat`

**包含服务**:
- MySQL、Redis、Nacos（基础设施）
- Gateway（网关，端口 48080）
- System（系统服务，端口 48081）
- Infra（基础设施服务，端口 48082）
- Frontend（前端，端口 3000）

**使用方法**:
```batch
双击运行: quick-start.bat
或命令行: cd e:\Code\CW_Cloud && quick-start.bat
```

**启动时间**: 约 2-3 分钟

**适用场景**: 日常开发，覆盖90%的功能需求

---

### 2️⃣ 完整启动（包含IoT和Filter模块）

**脚本**: `start-all-services.bat`

**包含服务**:
- 所有快速启动的服务 +
- IoT（物联网服务，端口 48083）
- Filter（滤波器服务，端口 48084）

**使用方法**:
```batch
双击运行: start-all-services.bat
```

**启动时间**: 约 4-5 分钟

**适用场景**: 
- 需要使用IoT设备管理功能
- 需要使用自适应滤波器功能
- 完整功能测试

**注意事项**:
- IoT和Filter模块需要先编译
- 需要更多内存资源（建议16GB+）

---

## 🛑 停止脚本

### 停止所有服务

**脚本**: `quick-stop.bat`

**功能**:
- 停止所有Java进程（后端服务）
- 停止所有Node进程（前端服务）
- 保留MySQL、Redis、Nacos继续运行

**使用方法**:
```batch
双击运行: quick-stop.bat
```

**说明**: 
- MySQL、Redis、Nacos作为基础设施保持运行，不会停止
- 下次启动会更快

---

## 🔧 诊断工具

### 系统诊断

**脚本**: `diagnose.bat`

**检查项目**:
1. ✅ 端口状态（3306, 6379, 8848, 48080-48084, 3000）
2. ✅ 进程状态（Java, Node）
3. ✅ Nacos服务注册状态
4. ✅ 后端API健康检查
5. ✅ 前端配置检查
6. ✅ 数据库连接测试

**使用方法**:
```batch
双击运行: diagnose.bat
```

**适用场景**:
- 服务启动后检查状态
- 系统出现问题时排查
- 验证服务是否正常注册到Nacos

---

### 菜单检查

**脚本**: `check-menus.sql`

**功能**: 
- 查询数据库中的菜单数据
- 检查菜单状态、可见性、权限分配

**使用方法**:
1. 打开MySQL客户端（Navicat/DBeaver/Workbench）
2. 连接到数据库：`ruoyi-vue-pro`
3. 打开并执行 `check-menus.sql`

**适用场景**:
- 登录后菜单不显示
- 新增菜单后不可见
- 检查菜单权限配置

---

## 📂 相关文档

### 完整指南

- **`START-GUIDE.md`** - 详细的启动指南
- **`SYSTEM-ANALYSIS-REPORT.md`** - 系统完整分析报告
- **`restore-iot-menus.md`** - IoT和滤波器菜单恢复指南
- **`CLEAR-CACHE.md`** - 清除浏览器缓存指南

### 前端清除缓存工具

访问以下URL可以快速清除浏览器缓存：
- http://localhost:3000/clear-and-relogin.html

---

## 🎯 常见使用场景

### 场景1: 日常开发
```bash
1. 双击运行: quick-start.bat
2. 等待2-3分钟
3. 访问: http://localhost:3000
4. 登录: admin / admin123
```

### 场景2: 需要IoT功能
```bash
1. 双击运行: start-all-services.bat
2. 等待4-5分钟
3. 访问: http://localhost:3000
4. 使用IoT设备管理和滤波器功能
```

### 场景3: 服务出现问题
```bash
1. 双击运行: diagnose.bat
2. 查看诊断报告
3. 根据提示修复问题
```

### 场景4: 菜单不显示
```bash
1. 访问: http://localhost:3000/clear-and-relogin.html
2. 清除缓存并重新登录
3. 如果还不行，使用 check-menus.sql 检查数据库
```

### 场景5: 结束工作
```bash
1. 双击运行: quick-stop.bat
2. 所有服务停止
3. 基础设施（MySQL/Redis/Nacos）保持运行
```

---

## ⚙️ 服务端口速查

| 服务 | 端口 | 说明 |
|------|------|------|
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |
| Nacos | 8848 | 注册中心/配置中心 |
| Gateway | 48080 | 网关（所有API入口） |
| System | 48081 | 系统管理服务 |
| Infra | 48082 | 基础设施服务 |
| IoT | 48083 | 物联网服务（可选） |
| Filter | 48084 | 滤波器服务（可选） |
| Frontend | 3000 | 前端开发服务器 |

---

## 🔑 默认登录信息

### 系统登录
- **URL**: http://localhost:3000
- **账号**: admin
- **密码**: admin123

### Nacos控制台
- **URL**: http://localhost:8848/nacos
- **账号**: nacos
- **密码**: nacos

---

## ❓ 常见问题

### Q1: 启动脚本运行后没有反应？
**A**: 检查以下几点：
1. 是否安装了Java（JDK 8或以上）
2. 是否安装了Node.js（16+）
3. 后端JAR文件是否已编译（位于各模块的target目录）
4. 前端依赖是否已安装（npm install）

### Q2: 端口被占用怎么办？
**A**: 
1. 运行 `diagnose.bat` 查看哪些端口被占用
2. 停止占用端口的程序
3. 或修改服务配置文件中的端口号

### Q3: 前端启动后验证码加载失败？
**A**: 
1. 确认后端服务已启动
2. 清除浏览器缓存
3. 检查 `.env.development` 配置是否正确

### Q4: 登录后菜单不显示？
**A**: 
1. 访问 http://localhost:3000/clear-and-relogin.html
2. 清除缓存并重新登录
3. 参考 `restore-iot-menus.md` 详细排查

### Q5: IoT和Filter模块启动失败？
**A**: 
1. 这两个模块需要先编译
2. Filter模块需要Java 14+
3. 如果不需要这些功能，使用 `quick-start.bat` 即可

---

## 💡 最佳实践

1. **日常开发**: 使用 `quick-start.bat`，启动快，资源占用少
2. **完整测试**: 使用 `start-all-services.bat`，包含所有模块
3. **定期诊断**: 出现问题时运行 `diagnose.bat`
4. **清理缓存**: 前端出现异常时，先清除浏览器缓存
5. **数据备份**: 重要数据定期备份MySQL数据库

---

## 🎉 快速开始

最简单的启动流程：

```bash
# 1. 启动服务
双击: quick-start.bat

# 2. 等待2-3分钟

# 3. 打开浏览器访问
http://localhost:3000

# 4. 登录
admin / admin123

# 5. 开始使用！
```

---

**文档更新时间**: 2025-11-24  
**维护者**: Cascade AI
