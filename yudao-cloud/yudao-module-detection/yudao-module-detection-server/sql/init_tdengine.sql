-- TDengine 初始化脚本
-- 创建数据库
CREATE DATABASE IF NOT EXISTS yudao_detection KEEP 365 DURATION 10;

USE yudao_detection;

-- 创建超级表 (STable) 用于存储检测结果
-- ts: 时间戳
-- raw_val: 原始值
-- filtered_val: 滤波后的值
-- is_anomaly: 是否异常 (1: 异常, 0: 正常)
-- 标签: channel_name (通道名称)
CREATE STABLE IF NOT EXISTS detection_results (
    ts TIMESTAMP,
    raw_val DOUBLE,
    filtered_val DOUBLE,
    is_anomaly TINYINT
) TAGS (
    channel_name BINARY(64)
);

-- 提示：
-- Flink 会自动为每个通道创建子表，例如：
-- INSERT INTO d_channel_001 USING detection_results TAGS ('channel_001') VALUES (...)
