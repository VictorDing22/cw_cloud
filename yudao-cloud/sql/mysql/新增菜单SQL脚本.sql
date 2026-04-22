-- ==========================================
-- 工业故障监测平台 - 新增菜单SQL脚本
-- 创建时间: 2025-10-20
-- 说明: 添加物联网数据和告警管理相关菜单
-- ==========================================

-- 注意：执行前请先查询当前最大的菜单ID，替换下面的起始ID
-- SELECT MAX(id) FROM system_menu;

-- ==========================================
-- 1. 物联网数据 - 参数数据对比菜单
-- ==========================================

-- 主菜单ID: 假设从 2100 开始
-- 如果 "物联网数据" 父菜单已存在，请使用已有的parent_id

-- 1.1 添加 "参数数据对比" 菜单
INSERT INTO `system_menu` (
    `name`, `permission`, `type`, `sort`, `parent_id`,
    `path`, `icon`, `component`, `component_name`, `status`,
    `visible`, `keep_alive`, `always_show`, `creator`,
    `create_time`, `updater`, `update_time`, `deleted`
) VALUES (
    '参数数据对比',                      -- name: 菜单名称
    'iot:data:compare',                 -- permission: 权限标识
    2,                                  -- type: 2=菜单
    7,                                  -- sort: 排序（在物联网数据下第7个）
    2036,                               -- parent_id: 父菜单ID（物联网数据的ID，需要替换为实际ID）
    'compare',                          -- path: 路由地址
    'ep:data-analysis',                 -- icon: 图标
    'iot/data/compare',                 -- component: 组件路径
    'IotDataCompare',                   -- component_name: 组件名
    0,                                  -- status: 0=正常
    true,                               -- visible: 是否显示
    true,                               -- keep_alive: 是否缓存
    false,                              -- always_show: 是否总是显示
    '1',                                -- creator: 创建者
    NOW(),                              -- create_time: 创建时间
    '1',                                -- updater: 更新者
    NOW(),                              -- update_time: 更新时间
    0                                   -- deleted: 是否删除
);

-- ==========================================
-- 2. 告警管理菜单（一级菜单）
-- ==========================================

-- 2.1 添加 "告警管理" 一级菜单
INSERT INTO `system_menu` (
    `name`, `permission`, `type`, `sort`, `parent_id`,
    `path`, `icon`, `component`, `component_name`, `status`,
    `visible`, `keep_alive`, `always_show`, `creator`,
    `create_time`, `updater`, `update_time`, `deleted`
) VALUES (
    '告警管理',                          -- name: 菜单名称
    '',                                 -- permission: 一级菜单无需权限标识
    1,                                  -- type: 1=目录
    4,                                  -- sort: 排序（建议放在物联网产品后面）
    0,                                  -- parent_id: 0表示一级菜单
    '/iot-alert',                       -- path: 路由地址
    'ep:bell',                          -- icon: 图标（铃铛）
    '',                                 -- component: 目录无需组件
    '',                                 -- component_name: 目录无需组件名
    0,                                  -- status: 0=正常
    true,                               -- visible: 是否显示
    true,                               -- keep_alive: 是否缓存
    true,                               -- always_show: 总是显示（有子菜单）
    '1',                                -- creator: 创建者
    NOW(),                              -- create_time: 创建时间
    '1',                                -- updater: 更新者
    NOW(),                              -- update_time: 更新时间
    0                                   -- deleted: 是否删除
);

-- 获取刚插入的告警管理菜单ID（用于后续子菜单）
-- 假设ID为 2200，实际使用时需要查询: SELECT LAST_INSERT_ID();

-- 2.2 添加 "告警管理" 子菜单
INSERT INTO `system_menu` (
    `name`, `permission`, `type`, `sort`, `parent_id`,
    `path`, `icon`, `component`, `component_name`, `status`,
    `visible`, `keep_alive`, `always_show`, `creator`,
    `create_time`, `updater`, `update_time`, `deleted`
) VALUES (
    '告警管理',                          -- name: 菜单名称
    'iot:alert:query',                  -- permission: 权限标识
    2,                                  -- type: 2=菜单
    1,                                  -- sort: 排序
    2200,                               -- parent_id: 父菜单ID（告警管理一级菜单ID，需替换）
    'list',                             -- path: 路由地址
    'ep:bell',                          -- icon: 图标
    'iot/alert/index',                  -- component: 组件路径
    'IotAlert',                         -- component_name: 组件名
    0,                                  -- status: 0=正常
    true,                               -- visible: 是否显示
    true,                               -- keep_alive: 是否缓存
    false,                              -- always_show: 是否总是显示
    '1',                                -- creator: 创建者
    NOW(),                              -- create_time: 创建时间
    '1',                                -- updater: 更新者
    NOW(),                              -- update_time: 更新时间
    0                                   -- deleted: 是否删除
);

-- 2.3 添加 "告警用户" 菜单
INSERT INTO `system_menu` (
    `name`, `permission`, `type`, `sort`, `parent_id`,
    `path`, `icon`, `component`, `component_name`, `status`,
    `visible`, `keep_alive`, `always_show`, `creator`,
    `create_time`, `updater`, `update_time`, `deleted`
) VALUES (
    '告警用户',                          -- name: 菜单名称
    'iot:alert:user',                   -- permission: 权限标识
    2,                                  -- type: 2=菜单
    2,                                  -- sort: 排序
    2200,                               -- parent_id: 父菜单ID（告警管理一级菜单ID，需替换）
    'user',                             -- path: 路由地址
    'ep:user',                          -- icon: 图标
    'iot/alert/user',                   -- component: 组件路径
    'IotAlertUser',                     -- component_name: 组件名
    0,                                  -- status: 0=正常
    true,                               -- visible: 是否显示
    true,                               -- keep_alive: 是否缓存
    false,                              -- always_show: 是否总是显示
    '1',                                -- creator: 创建者
    NOW(),                              -- create_time: 创建时间
    '1',                                -- updater: 更新者
    NOW(),                              -- update_time: 更新时间
    0                                   -- deleted: 是否删除
);

-- 2.4 添加 "告警场景" 菜单
INSERT INTO `system_menu` (
    `name`, `permission`, `type`, `sort`, `parent_id`,
    `path`, `icon`, `component`, `component_name`, `status`,
    `visible`, `keep_alive`, `always_show`, `creator`,
    `create_time`, `updater`, `update_time`, `deleted`
) VALUES (
    '告警场景',                          -- name: 菜单名称
    'iot:alert:scene',                  -- permission: 权限标识
    2,                                  -- type: 2=菜单
    3,                                  -- sort: 排序
    2200,                               -- parent_id: 父菜单ID（告警管理一级菜单ID，需替换）
    'scene',                            -- path: 路由地址
    'ep:setting',                       -- icon: 图标
    'iot/alert/scene',                  -- component: 组件路径
    'IotAlertScene',                    -- component_name: 组件名
    0,                                  -- status: 0=正常
    true,                               -- visible: 是否显示
    true,                               -- keep_alive: 是否缓存
    false,                              -- always_show: 是否总是显示
    '1',                                -- creator: 创建者
    NOW(),                              -- create_time: 创建时间
    '1',                                -- updater: 更新者
    NOW(),                              -- update_time: 更新时间
    0                                   -- deleted: 是否删除
);

-- 2.5 添加 "用户消息" 菜单
INSERT INTO `system_menu` (
    `name`, `permission`, `type`, `sort`, `parent_id`,
    `path`, `icon`, `component`, `component_name`, `status`,
    `visible`, `keep_alive`, `always_show`, `creator`,
    `create_time`, `updater`, `update_time`, `deleted`
) VALUES (
    '用户消息',                          -- name: 菜单名称
    'iot:alert:message',                -- permission: 权限标识
    2,                                  -- type: 2=菜单
    4,                                  -- sort: 排序
    2200,                               -- parent_id: 父菜单ID（告警管理一级菜单ID，需替换）
    'message',                          -- path: 路由地址
    'ep:message',                       -- icon: 图标
    'iot/alert/message',                -- component: 组件路径
    'IotAlertMessage',                  -- component_name: 组件名
    0,                                  -- status: 0=正常
    true,                               -- visible: 是否显示
    true,                               -- keep_alive: 是否缓存
    false,                              -- always_show: 是否总是显示
    '1',                                -- creator: 创建者
    NOW(),                              -- create_time: 创建时间
    '1',                                -- updater: 更新者
    NOW(),                              -- update_time: 更新时间
    0                                   -- deleted: 是否删除
);

-- 2.6 添加 "告警日志" 菜单
INSERT INTO `system_menu` (
    `name`, `permission`, `type`, `sort`, `parent_id`,
    `path`, `icon`, `component`, `component_name`, `status`,
    `visible`, `keep_alive`, `always_show`, `creator`,
    `create_time`, `updater`, `update_time`, `deleted`
) VALUES (
    '告警日志',                          -- name: 菜单名称
    'iot:alert:log',                    -- permission: 权限标识
    2,                                  -- type: 2=菜单
    5,                                  -- sort: 排序
    2200,                               -- parent_id: 父菜单ID（告警管理一级菜单ID，需替换）
    'log',                              -- path: 路由地址
    'ep:document',                      -- icon: 图标
    'iot/alert/log',                    -- component: 组件路径
    'IotAlertLog',                      -- component_name: 组件名
    0,                                  -- status: 0=正常
    true,                               -- visible: 是否显示
    true,                               -- keep_alive: 是否缓存
    false,                              -- always_show: 是否总是显示
    '1',                                -- creator: 创建者
    NOW(),                              -- create_time: 创建时间
    '1',                                -- updater: 更新者
    NOW(),                              -- update_time: 更新时间
    0                                   -- deleted: 是否删除
);

-- ==========================================
-- 查询验证SQL
-- ==========================================

-- 查询所有新增的菜单
SELECT id, name, parent_id, path, component, sort, visible, status
FROM system_menu
WHERE name IN ('参数数据对比', '告警管理', '告警用户', '告警场景', '用户消息', '告警日志')
AND deleted = 0
ORDER BY parent_id, sort;

-- ==========================================
-- 清理SQL（如果需要删除重新添加）
-- ==========================================

-- DELETE FROM system_menu WHERE name IN ('参数数据对比', '告警管理', '告警用户', '告警场景', '用户消息', '告警日志') AND deleted = 0;

