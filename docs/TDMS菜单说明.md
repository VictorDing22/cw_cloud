# TDMS信号查看器菜单添加说明

## 问题描述
前端找不到"TDMS信号查看器"菜单，因为菜单数据还没有写入数据库。

## 解决方案

### 方式一：使用批处理脚本（推荐）

1. 双击运行 `execute-tdms-menu.bat`
2. 输入 MySQL root 密码
3. 等待执行完成
4. 刷新浏览器页面

### 方式二：使用 Navicat 或其他 MySQL 客户端

1. 打开 Navicat（或 MySQL Workbench、DBeaver 等）
2. 连接到数据库 `ruoyi-vue-pro`
3. 打开查询窗口
4. 复制并执行 `add-tdms-viewer-menu.sql` 文件的内容
5. 刷新浏览器页面

### 方式三：命令行直接执行

```bash
# Windows PowerShell
Get-Content add-tdms-viewer-menu.sql | mysql -u root -p ruoyi-vue-pro

# 或使用 cmd
mysql -u root -p ruoyi-vue-pro < add-tdms-viewer-menu.sql
```

---

## SQL 脚本内容说明

### 1. 检查并创建"实时监控"父菜单
```sql
-- 确保"实时监控"菜单存在
INSERT INTO system_menu (...)
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE name = '实时监控');
```

### 2. 添加"TDMS信号查看器"子菜单
```sql
INSERT INTO system_menu (
    name: 'TDMS信号查看器',
    path: 'tdms-viewer',
    component: 'realtime/TDMSSignalViewer',
    sort: 60,
    ...
)
```

### 3. 分配权限给管理员角色
```sql
-- 给角色ID=1（管理员）分配菜单权限
INSERT INTO system_role_menu (role_id, menu_id, ...)
```

---

## 菜单配置详情

| 字段 | 值 | 说明 |
|------|-----|------|
| **name** | TDMS信号查看器 | 菜单名称 |
| **permission** | realtime:tdms:viewer | 权限标识 |
| **type** | 2 | 菜单类型（2=菜单） |
| **sort** | 60 | 排序值 |
| **parent_id** | 实时监控菜单ID | 父菜单 |
| **path** | tdms-viewer | 路由路径 |
| **icon** | ep:data-analysis | 菜单图标 |
| **component** | realtime/TDMSSignalViewer | Vue组件路径 |
| **status** | 0 | 状态（0=启用） |
| **visible** | 1 | 可见（1=是） |
| **keep_alive** | 1 | 缓存（1=是） |

---

## 验证步骤

### 1. 检查菜单是否已添加

```sql
-- 查询菜单
SELECT 
    id, name, parent_id, path, component, sort, status
FROM system_menu 
WHERE name = 'TDMS信号查看器';
```

期望结果：
```
id  | name            | parent_id | path        | component                  | sort | status
----|-----------------|-----------|-------------|----------------------------|------|-------
xxx | TDMS信号查看器   | xxx       | tdms-viewer | realtime/TDMSSignalViewer | 60   | 0
```

### 2. 检查权限是否已分配

```sql
-- 查询角色菜单关联
SELECT 
    r.name AS role_name,
    m.name AS menu_name,
    rm.create_time
FROM system_role r
INNER JOIN system_role_menu rm ON r.id = rm.role_id
INNER JOIN system_menu m ON rm.menu_id = m.id
WHERE m.name = 'TDMS信号查看器';
```

期望结果：
```
role_name | menu_name       | create_time
----------|-----------------|-------------------
超级管理员 | TDMS信号查看器   | 2025-12-09 15:xx:xx
```

### 3. 前端验证

1. **刷新浏览器页面** (F5 或 Ctrl+R)
2. 查看左侧菜单
3. 找到"实时监控"
4. 展开后应该能看到"TDMS信号查看器"

---

## 故障排查

### 问题1：刷新后仍然看不到菜单

**解决方法**：
1. 退出登录，重新登录
2. 清除浏览器缓存 (Ctrl+Shift+Delete)
3. 检查用户是否有权限

### 问题2：点击菜单报404错误

**原因**：前端组件文件不存在

**解决方法**：
确认文件存在：
```
yudao-ui-admin-vue3/src/views/realtime/TDMSSignalViewer.vue
```

### 问题3：菜单显示但是空白页面

**原因**：组件加载失败

**解决方法**：
1. 检查浏览器控制台错误信息 (F12)
2. 确认路由配置正确：
```typescript
// yudao-ui-admin-vue3/src/router/modules/realtime.ts
{
  path: 'tdms-viewer',
  component: () => import('@/views/realtime/TDMSSignalViewer.vue'),
  name: 'TDMSSignalViewer',
  meta: { title: 'TDMS信号查看器', noCache: true }
}
```

### 问题4：SQL执行报错

**常见错误**：
```sql
ERROR 1064: You have an error in your SQL syntax
```

**解决方法**：
1. 确认数据库名称正确（ruoyi-vue-pro）
2. 确认表名正确（system_menu, system_role_menu）
3. 使用 Navicat 逐条执行 SQL 查看具体哪一句报错

---

## 手动添加菜单步骤

如果SQL脚本执行失败，可以手动在前端添加：

### 1. 登录系统
- URL: http://localhost:80
- 账号: admin / admin123

### 2. 进入菜单管理
- 系统管理 → 菜单管理

### 3. 找到"实时监控"菜单
- 如果没有，先创建父菜单

### 4. 添加子菜单
点击"新增"，填写：
- **上级菜单**: 实时监控
- **菜单名称**: TDMS信号查看器
- **菜单图标**: data-analysis
- **路由地址**: tdms-viewer
- **组件路径**: realtime/TDMSSignalViewer
- **权限标识**: realtime:tdms:viewer
- **显示排序**: 60
- **菜单状态**: 启用
- **是否缓存**: 是

### 5. 保存并刷新

---

## 完整文件列表

### 前端文件
```
yudao-ui-admin-vue3/
└── src/
    ├── views/realtime/
    │   └── TDMSSignalViewer.vue        ← Vue组件（已创建）
    └── router/modules/
        └── realtime.ts                  ← 路由配置（已更新）
```

### 后端文件
```
e:\Code\CW_Cloud/
├── tdms-api-server.js                  ← API服务器（已创建）
├── add-tdms-viewer-menu.sql            ← 菜单SQL（新建）
└── execute-tdms-menu.bat               ← 执行脚本（新建）
```

---

## 快速检查清单

- [ ] SQL脚本已执行
- [ ] 数据库中已有菜单记录
- [ ] 权限已分配给管理员
- [ ] 前端组件文件存在
- [ ] 路由配置正确
- [ ] 浏览器已刷新
- [ ] 用户已重新登录

---

## 联系方式

如果以上方法都不能解决问题，请：

1. 检查浏览器控制台错误 (F12 → Console)
2. 检查网络请求 (F12 → Network)
3. 查看后端日志
4. 提供错误截图

---

**执行完SQL后，记得刷新浏览器页面！** 🔄
