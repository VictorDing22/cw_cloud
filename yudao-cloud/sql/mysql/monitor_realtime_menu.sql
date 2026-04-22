-- ==========================================
-- 工业故障监测平台 - 实时监控/实时检测 菜单初始化
-- 说明：左侧菜单由 system-server 从 system_menu 表加载生成；仅改前端路由不会自动出现菜单
-- 执行库：MySQL（与 ruoyi-vue-pro.sql 同库）
-- ==========================================

-- 1) 如需重跑，先清理（谨慎执行）
-- DELETE FROM system_role_menu WHERE menu_id IN (SELECT id FROM system_menu WHERE name IN ('实时监控','实时检测'));
-- DELETE FROM system_menu WHERE name IN ('实时监控','实时检测');

-- 2) 创建一级目录：实时监控（parent_id=0, type=1）
INSERT INTO `system_menu` (
  `name`, `permission`, `type`, `sort`, `parent_id`,
  `path`, `icon`, `component`, `component_name`, `status`,
  `visible`, `keep_alive`, `always_show`, `creator`,
  `create_time`, `updater`, `update_time`, `deleted`
) VALUES (
  '实时监控',
  '',
  1,
  90,
  0,
  '/monitor',
  'ep:monitor',
  NULL,
  NULL,
  0,
  b'1',
  b'1',
  b'1',
  '1',
  NOW(),
  '1',
  NOW(),
  b'0'
);

SET @monitor_dir_id = LAST_INSERT_ID();

-- 3) 创建二级菜单：实时检测（type=2）
INSERT INTO `system_menu` (
  `name`, `permission`, `type`, `sort`, `parent_id`,
  `path`, `icon`, `component`, `component_name`, `status`,
  `visible`, `keep_alive`, `always_show`, `creator`,
  `create_time`, `updater`, `update_time`, `deleted`
) VALUES (
  '实时检测',
  'monitor:realtime-detection:query',
  2,
  1,
  @monitor_dir_id,
  'realtime-detection',
  'ep:data-line',
  'monitor/RealtimeDetection',
  'RealtimeDetection',
  0,
  b'1',
  b'1',
  b'0',
  '1',
  NOW(),
  '1',
  NOW(),
  b'0'
);

SET @realtime_detection_menu_id = LAST_INSERT_ID();

-- 4) 赋权给超级管理员角色（role_id=1）
INSERT INTO `system_role_menu` (
  `role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
) VALUES
  (1, @monitor_dir_id, '1', NOW(), '1', NOW(), b'0', 1),
  (1, @realtime_detection_menu_id, '1', NOW(), '1', NOW(), b'0', 1);

-- 5) 验证
SELECT id, name, parent_id, type, path, component, component_name, permission
FROM system_menu
WHERE id IN (@monitor_dir_id, @realtime_detection_menu_id);
