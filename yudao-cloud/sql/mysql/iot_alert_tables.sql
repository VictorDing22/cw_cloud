-- ==========================================
-- 工业故障监测平台 - 告警管理数据库表
-- 创建时间: 2025-10-20
-- ==========================================

-- ----------------------------
-- 1. 告警用户表
-- ----------------------------
DROP TABLE IF EXISTS `iot_alert_user`;
CREATE TABLE `iot_alert_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `contact_name` varchar(50) NOT NULL COMMENT '联系人姓名',
  `contact_type` varchar(20) DEFAULT 'email' COMMENT '联系类型(email,phone)',
  `gateway_id` bigint DEFAULT NULL COMMENT '门户ID',
  `gateway_name` varchar(100) DEFAULT NULL COMMENT '门户名称',
  `gateway_location` varchar(200) DEFAULT NULL COMMENT '门户位置',
  `language` varchar(10) DEFAULT 'zh-CN' COMMENT '使用语言(zh-CN,en-US)',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `receiver_count` int DEFAULT 10 COMMENT '接收器数量(min)',
  `status` tinyint DEFAULT 1 COMMENT '状态(0-禁用 1-启用)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_contact_name` (`contact_name`),
  KEY `idx_gateway_id` (`gateway_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警用户表';

-- ----------------------------
-- 2. 告警场景表
-- ----------------------------
DROP TABLE IF EXISTS `iot_alert_scene`;
CREATE TABLE `iot_alert_scene` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '场景ID',
  `scene_name` varchar(100) NOT NULL COMMENT '场景名称',
  `scene_type` varchar(20) NOT NULL COMMENT '场景类型(intensity,temperature,other)',
  `gateway_id` bigint DEFAULT NULL COMMENT '门户ID',
  `gateway_name` varchar(100) DEFAULT NULL COMMENT '门户名称',
  `gateway_location` varchar(200) DEFAULT NULL COMMENT '门户位置',
  `alert_level` tinyint NOT NULL DEFAULT 1 COMMENT '告警等级(1-5级)',
  `trigger_duration` int NOT NULL DEFAULT 20 COMMENT '触发时间(秒)',
  `status` tinyint DEFAULT 1 COMMENT '状态(0-禁用 1-启用)',
  `rating_type` varchar(20) DEFAULT 'auto' COMMENT '评级类型(auto-自动,manual-自定义)',
  `rules` json DEFAULT NULL COMMENT '触发规则(JSON)',
  `evaluation_rule` varchar(20) DEFAULT 'any' COMMENT '评估规则(any-任一,all-所有)',
  `statistics_duration` int DEFAULT 20 COMMENT '统计时长(秒)',
  `threshold_type` varchar(20) DEFAULT 'unlimited' COMMENT '阈值类型',
  `bmw_threshold` int DEFAULT 1 COMMENT '宝马规则上限阈值(秒)',
  `notify_method` json DEFAULT NULL COMMENT '通知方式(JSON数组)',
  `notify_users` json DEFAULT NULL COMMENT '通知用户ID列表(JSON数组)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_scene_name` (`scene_name`),
  KEY `idx_scene_type` (`scene_type`),
  KEY `idx_gateway_id` (`gateway_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警场景表';

-- ----------------------------
-- 3. 用户消息表
-- ----------------------------
DROP TABLE IF EXISTS `iot_alert_message`;
CREATE TABLE `iot_alert_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `message_type` varchar(20) NOT NULL COMMENT '消息类型(alert,system,device)',
  `title` varchar(200) NOT NULL COMMENT '标题',
  `content` text COMMENT '内容',
  `level` varchar(20) DEFAULT 'low' COMMENT '级别(high,medium,low)',
  `read_status` tinyint DEFAULT 0 COMMENT '阅读状态(0-未读,1-已读)',
  `device_info` varchar(200) DEFAULT NULL COMMENT '设备信息',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_message_type` (`message_type`),
  KEY `idx_read_status` (`read_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户消息表';

-- ----------------------------
-- 4. 告警日志表
-- ----------------------------
DROP TABLE IF EXISTS `iot_alert_log`;
CREATE TABLE `iot_alert_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `scene_id` bigint NOT NULL COMMENT '场景ID',
  `scene_name` varchar(100) DEFAULT NULL COMMENT '场景名称',
  `device_key` varchar(50) NOT NULL COMMENT '设备标识',
  `device_name` varchar(100) DEFAULT NULL COMMENT '设备名称',
  `alert_level` tinyint NOT NULL COMMENT '告警级别(1-5)',
  `alert_params` json DEFAULT NULL COMMENT '告警参数(JSON)',
  `threshold` decimal(10,3) DEFAULT NULL COMMENT '阈值',
  `actual_value` decimal(10,3) DEFAULT NULL COMMENT '实际值',
  `handle_status` tinyint DEFAULT 0 COMMENT '处理状态(0-未处理,1-处理中,2-已处理)',
  `handle_user` varchar(64) DEFAULT NULL COMMENT '处理人',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `handle_remark` varchar(500) DEFAULT NULL COMMENT '处理备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_scene_id` (`scene_id`),
  KEY `idx_device_key` (`device_key`),
  KEY `idx_alert_level` (`alert_level`),
  KEY `idx_handle_status` (`handle_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警日志表';

-- ----------------------------
-- 插入测试数据
-- ----------------------------

-- 告警用户测试数据
INSERT INTO `iot_alert_user` (`contact_name`, `contact_type`, `gateway_id`, `gateway_name`, `gateway_location`, `language`, `phone`, `email`, `receiver_count`, `status`, `remark`) VALUES
('张三', 'email', 1, '消化炉关键（广州）', '研究公司', 'zh-CN', '13800138000', 'zhangsan@example.com', 10, 1, '主要联系人'),
('李四', 'phone', 1, '消化炉关键（广州）', '研究公司', 'zh-CN', '13900139000', 'lisi@example.com', 5, 1, '备用联系人');

-- 告警场景测试数据
INSERT INTO `iot_alert_scene` (`scene_name`, `scene_type`, `gateway_id`, `gateway_name`, `gateway_location`, `alert_level`, `trigger_duration`, `status`, `rating_type`, `rules`, `evaluation_rule`, `statistics_duration`, `threshold_type`, `bmw_threshold`, `notify_method`, `notify_users`, `remark`) VALUES
('10级超压', 'intensity', 1, '消化炉关键（广州）', '研究公司', 2, 20, 1, 'manual', 
 '[{"enabled":true,"parameter":"amplitude","condition":"gt","threshold":40}]', 
 'any', 20, 'unlimited', 1, '["email"]', '[1]', '高压告警场景'),
('11级超压', 'intensity', 1, '消化炉关键（广州）', '研究公司', 3, 20, 1, 'manual',
 '[{"enabled":true,"parameter":"amplitude","condition":"gt","threshold":50}]',
 'any', 20, 'unlimited', 1, '["email","sms"]', '[1,2]', '超高压告警场景');

-- 用户消息测试数据
INSERT INTO `iot_alert_message` (`user_id`, `message_type`, `title`, `content`, `level`, `read_status`, `device_info`) VALUES
(1, 'alert', '告警通知', '设备JF_RAEM1_WP1_03触发10级超压告警', 'high', 0, 'JF_RAEM1_WP1_03'),
(1, 'system', '系统通知', '系统将于今晚22:00进行维护', 'medium', 0, NULL);

-- 告警日志测试数据
INSERT INTO `iot_alert_log` (`scene_id`, `scene_name`, `device_key`, `device_name`, `alert_level`, `alert_params`, `threshold`, `actual_value`, `handle_status`) VALUES
(1, '10级超压', 'JF_RAEM1_WP1_03', 'JF_RAEM1_WP1_03', 1, '{"amplitude":73.358,"energy":74.073,"rms":0.831}', 40.000, 73.358, 0),
(1, '10级超压', 'JF_RAEM1_WP1_03', 'JF_RAEM1_WP1_03', 2, '{"amplitude":85.5,"energy":80.2,"rms":0.95}', 40.000, 85.500, 1);

