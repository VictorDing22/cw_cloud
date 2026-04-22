-- =============================================
-- 滤波系统数据表结构 - 工业故障监测平台
-- 包含滤波结果、异常检测、设备状态、性能监控表
-- =============================================

USE ruoyi_vue_pro;

-- 1. 滤波结果记录表
CREATE TABLE IF NOT EXISTS `filter_result_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(64) NOT NULL COMMENT '设备ID',
  `device_name` varchar(128) DEFAULT NULL COMMENT '设备名称',
  `session_id` varchar(64) DEFAULT NULL COMMENT '会话ID',
  
  -- 信号数据
  `original_samples` json DEFAULT NULL COMMENT '原始信号数据(JSON数组)',
  `filtered_samples` json DEFAULT NULL COMMENT '滤波后数据(JSON数组)',
  `sample_rate` int DEFAULT 1000000 COMMENT '采样率(Hz)',
  `sample_count` int DEFAULT 0 COMMENT '采样点数',
  
  -- 滤波参数
  `filter_type` varchar(32) DEFAULT 'LMS' COMMENT '滤波器类型(LMS/NLMS)',
  `filter_order` int DEFAULT 32 COMMENT '滤波器阶数',
  `learning_rate` decimal(10,6) DEFAULT 0.010000 COMMENT '学习率',
  `convergence_factor` decimal(10,6) DEFAULT NULL COMMENT '收敛因子',
  
  -- 性能指标
  `snr_improvement` decimal(10,4) DEFAULT NULL COMMENT 'SNR改善值(dB)',
  `mse_error` decimal(15,8) DEFAULT NULL COMMENT '均方误差',
  `processing_time` int DEFAULT NULL COMMENT '处理时间(ms)',
  
  -- 统计信息
  `original_rms` decimal(15,8) DEFAULT NULL COMMENT '原始信号RMS',
  `filtered_rms` decimal(15,8) DEFAULT NULL COMMENT '滤波信号RMS',
  `noise_reduction` decimal(10,4) DEFAULT NULL COMMENT '噪声抑制率(%)',
  
  -- 系统字段
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator` varchar(64) DEFAULT 'system' COMMENT '创建者',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  
  PRIMARY KEY (`id`),
  KEY `idx_device_create_time` (`device_id`,`create_time`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_filter_type` (`filter_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='滤波结果记录表';

-- 2. 异常检测记录表
CREATE TABLE IF NOT EXISTS `anomaly_detection_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(64) NOT NULL COMMENT '设备ID',
  `device_name` varchar(128) DEFAULT NULL COMMENT '设备名称',
  `filter_record_id` bigint DEFAULT NULL COMMENT '关联的滤波记录ID',
  
  -- 异常信息
  `anomaly_type` varchar(32) NOT NULL COMMENT '异常类型(FREQUENCY/AMPLITUDE/PATTERN)',
  `anomaly_score` decimal(10,6) NOT NULL COMMENT '异常分数(0-1)',
  `confidence_level` decimal(10,4) DEFAULT NULL COMMENT '置信度(%)',
  
  -- 告警级别
  `alert_level` varchar(16) NOT NULL DEFAULT 'INFO' COMMENT '告警级别(INFO/WARN/ERROR/CRITICAL)',
  `threshold_value` decimal(15,8) DEFAULT NULL COMMENT '阈值',
  `detected_value` decimal(15,8) DEFAULT NULL COMMENT '检测值',
  
  -- 详细信息
  `detection_algorithm` varchar(64) DEFAULT NULL COMMENT '检测算法',
  `feature_vector` json DEFAULT NULL COMMENT '特征向量(JSON)',
  `context_data` json DEFAULT NULL COMMENT '上下文数据(JSON)',
  `description` text DEFAULT NULL COMMENT '异常描述',
  
  -- 处理状态
  `status` varchar(16) DEFAULT 'PENDING' COMMENT '处理状态(PENDING/CONFIRMED/IGNORED/RESOLVED)',
  `handled_by` varchar(64) DEFAULT NULL COMMENT '处理人',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `handle_note` text DEFAULT NULL COMMENT '处理备注',
  
  -- 系统字段  
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator` varchar(64) DEFAULT 'system' COMMENT '创建者',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  
  PRIMARY KEY (`id`),
  KEY `idx_device_create_time` (`device_id`,`create_time`),
  KEY `idx_anomaly_type` (`anomaly_type`),
  KEY `idx_alert_level` (`alert_level`),
  KEY `idx_status` (`status`),
  KEY `idx_filter_record` (`filter_record_id`),
  CONSTRAINT `fk_filter_record` FOREIGN KEY (`filter_record_id`) REFERENCES `filter_result_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异常检测记录表';

-- 3. 设备实时状态表
CREATE TABLE IF NOT EXISTS `device_realtime_status` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(64) NOT NULL COMMENT '设备ID',
  `device_name` varchar(128) DEFAULT NULL COMMENT '设备名称',
  
  -- 连接状态
  `connection_status` varchar(16) DEFAULT 'OFFLINE' COMMENT '连接状态(ONLINE/OFFLINE/ERROR)',
  `last_heartbeat` datetime DEFAULT NULL COMMENT '最后心跳时间',
  `session_start_time` datetime DEFAULT NULL COMMENT '会话开始时间',
  
  -- 数据统计
  `total_packets` bigint DEFAULT 0 COMMENT '总数据包数',
  `packets_per_second` decimal(10,2) DEFAULT 0.00 COMMENT '每秒数据包数',
  `data_quality_score` decimal(5,2) DEFAULT 100.00 COMMENT '数据质量分数',
  
  -- 性能指标
  `avg_processing_time` decimal(10,4) DEFAULT NULL COMMENT '平均处理时间(ms)',
  `current_snr` decimal(10,4) DEFAULT NULL COMMENT '当前SNR(dB)',
  `error_rate` decimal(8,4) DEFAULT 0.0000 COMMENT '错误率(%)',
  
  -- 告警统计
  `total_alerts` int DEFAULT 0 COMMENT '总告警数',
  `critical_alerts` int DEFAULT 0 COMMENT '严重告警数',
  `last_alert_time` datetime DEFAULT NULL COMMENT '最后告警时间',
  
  -- 系统字段
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_id` (`device_id`),
  KEY `idx_connection_status` (`connection_status`),
  KEY `idx_last_heartbeat` (`last_heartbeat`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备实时状态表';

-- 4. 系统性能监控表
CREATE TABLE IF NOT EXISTS `system_performance_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `metric_name` varchar(64) NOT NULL COMMENT '指标名称',
  `metric_value` decimal(15,4) NOT NULL COMMENT '指标值',
  `metric_unit` varchar(16) DEFAULT NULL COMMENT '单位',
  
  -- 组件信息
  `component` varchar(32) NOT NULL COMMENT '组件名称(kafka/backend/frontend/database)',
  `instance_id` varchar(64) DEFAULT NULL COMMENT '实例ID',
  
  -- 附加信息
  `tags` json DEFAULT NULL COMMENT '标签信息(JSON)',
  `description` varchar(256) DEFAULT NULL COMMENT '描述',
  
  -- 时间字段
  `record_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  
  PRIMARY KEY (`id`),
  KEY `idx_component_metric_time` (`component`,`metric_name`,`record_time`),
  KEY `idx_record_time` (`record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统性能监控日志';

-- 插入初始设备数据
INSERT INTO `device_realtime_status` (`device_id`, `device_name`, `connection_status`) VALUES
('sensor-simulation', '模拟声发射传感器', 'ONLINE'),
('device001', '生产线传感器001', 'OFFLINE'),
('device002', '生产线传感器002', 'OFFLINE')
ON DUPLICATE KEY UPDATE `device_name` = VALUES(`device_name`);

-- 创建索引优化查询
CREATE INDEX IF NOT EXISTS `idx_filter_record_perf` ON `filter_result_record`(`create_time` DESC, `snr_improvement` DESC);
CREATE INDEX IF NOT EXISTS `idx_anomaly_recent` ON `anomaly_detection_record`(`create_time` DESC, `alert_level`);

SELECT '✅ 数据表创建完成！' AS 提示;
