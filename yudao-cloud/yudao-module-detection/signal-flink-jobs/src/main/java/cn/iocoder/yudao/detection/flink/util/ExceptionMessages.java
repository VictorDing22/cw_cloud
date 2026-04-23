package cn.iocoder.yudao.detection.flink.util;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.util.OutputTag;

/**
 * Shared utilities for exception_topic across all Flink jobs.
 *
 * Unified error JSON format:
 * {
 *   "job":          "signal-saveraw-flink-job",
 *   "error_type":   "parse_error",
 *   "error_msg":    "NumberFormatException: ...",
 *   "original_msg": "DEVICE:1:1:168...,0.001,0.002",
 *   "device_id":    "DEVICE",
 *   "channel_id":   1,
 *   "seq":          1,
 *   "timestamp":    1776910945998
 * }
 */
public class ExceptionMessages {

    public static final String DEFAULT_EXCEPTION_TOPIC = "exception_topic";

    public static final OutputTag<String> EXCEPTION_TAG =
            new OutputTag<String>("exception") {};

    /**
     * Try to extract device_id, channel_id, seq from the raw protocol header.
     * Returns String[3] = {deviceId, channelId, seq}. Missing fields are empty strings.
     */
    private static String[] extractHeader(String originalMsg) {
        String deviceId = "";
        String channelId = "";
        String seq = "";
        if (originalMsg != null && !originalMsg.isEmpty()) {
            try {
                String header = originalMsg;
                int comma = originalMsg.indexOf(',');
                if (comma > 0) header = originalMsg.substring(0, comma);
                String[] parts = header.split(":");
                if (parts.length >= 1) deviceId = parts[0];
                if (parts.length >= 2) channelId = parts[1];
                if (parts.length >= 3) seq = parts[2];
            } catch (Exception ignored) {}
        }
        return new String[]{deviceId, channelId, seq};
    }

    /**
     * Build the unified error JSON. Automatically extracts device_id/channel_id/seq
     * from original_msg when possible.
     */
    public static String buildErrorJson(String jobName, String errorType,
                                        String errorMsg, String originalMsg) {
        String[] hdr = extractHeader(originalMsg);
        return buildErrorJson(jobName, errorType, errorMsg, originalMsg,
                              hdr[0], hdr[1], hdr[2]);
    }

    /**
     * Build error JSON with explicit device/channel/seq (used when caller already parsed them).
     */
    public static String buildErrorJson(String jobName, String errorType,
                                        String errorMsg, String originalMsg,
                                        String deviceId, String channelId, String seq) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        jsonStr(sb, "job", jobName);           sb.append(',');
        jsonStr(sb, "error_type", errorType);  sb.append(',');
        jsonStr(sb, "error_msg", errorMsg);    sb.append(',');
        jsonStr(sb, "original_msg", truncate(originalMsg, 2000)); sb.append(',');
        jsonStr(sb, "device_id", deviceId);    sb.append(',');
        jsonVal(sb, "channel_id", toIntOrNull(channelId)); sb.append(',');
        jsonVal(sb, "seq", toIntOrNull(seq));  sb.append(',');
        sb.append("\"timestamp\":").append(System.currentTimeMillis());
        sb.append('}');
        return sb.toString();
    }

    /**
     * Build error JSON for TDengine write failures.
     */
    public static String buildWriteErrorJson(String jobName, int recordCount,
                                             String deviceId, String errorMsg) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        jsonStr(sb, "job", jobName);           sb.append(',');
        jsonStr(sb, "error_type", "tdengine_write_error"); sb.append(',');
        jsonStr(sb, "error_msg", errorMsg);    sb.append(',');
        jsonStr(sb, "original_msg", "batch_size=" + recordCount); sb.append(',');
        jsonStr(sb, "device_id", deviceId);    sb.append(',');
        sb.append("\"channel_id\":null,");
        sb.append("\"seq\":null,");
        sb.append("\"timestamp\":").append(System.currentTimeMillis());
        sb.append('}');
        return sb.toString();
    }

    public static KafkaSink<String> createExceptionSink(String kafkaBroker, String topic) {
        return KafkaSink.<String>builder()
                .setBootstrapServers(kafkaBroker)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(topic)
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build())
                .build();
    }

    public static void wireExceptionSink(SingleOutputStreamOperator<?> mainStream,
                                         String kafkaBroker, String topic, String sinkName) {
        DataStream<String> exceptions = mainStream.getSideOutput(EXCEPTION_TAG);
        exceptions.sinkTo(createExceptionSink(kafkaBroker, topic)).name(sinkName);
    }

    // --- JSON helpers ---

    private static void jsonStr(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":\"").append(escape(value)).append('"');
    }

    private static void jsonVal(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":").append(value);
    }

    private static String toIntOrNull(String s) {
        if (s == null || s.isEmpty()) return "null";
        try { Integer.parseInt(s); return s; } catch (Exception e) { return "null"; }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...[truncated]";
    }
}
