-- Detection Platform TDengine Schema (based on technical spec)
-- NOTE: Each statement MUST be on a single line for `taos -f` compatibility

CREATE DATABASE IF NOT EXISTS yudao_detection KEEP 3650 DURATION 30 BUFFER 256 WAL_LEVEL 1 PRECISION 'ns';

USE yudao_detection;

CREATE STABLE IF NOT EXISTS raw_data (ts TIMESTAMP, voltage FLOAT, sampling INT, seq INT) TAGS (device_id NCHAR(32), channel_id TINYINT);

CREATE STABLE IF NOT EXISTS filtered_data (ts TIMESTAMP, voltage FLOAT, seq INT) TAGS (device_id NCHAR(32), channel_id TINYINT);

CREATE STABLE IF NOT EXISTS feature_data (ts TIMESTAMP, amplitude FLOAT, energy FLOAT, area FLOAT, skewness FLOAT, rise_time FLOAT, hit_duration FLOAT, counts INT, ra FLOAT, af FLOAT, is_error TINYINT, error_type NCHAR(32), alert_level TINYINT, loc_x FLOAT, loc_y FLOAT, seq INT) TAGS (device_id NCHAR(32), channel_id TINYINT);

CREATE STABLE IF NOT EXISTS detection_results (ts TIMESTAMP, raw_val DOUBLE, filtered_val DOUBLE, is_anomaly TINYINT) TAGS (channel_name BINARY(64));
