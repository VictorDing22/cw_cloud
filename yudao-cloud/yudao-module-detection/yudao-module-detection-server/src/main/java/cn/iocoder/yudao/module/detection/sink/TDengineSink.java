package cn.iocoder.yudao.module.detection.sink;

import cn.iocoder.yudao.module.detection.logic.dto.FilterResult;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

/**
 * TDengine Sink：将检测结果写入时序库
 */
public class TDengineSink {

    public static SinkFunction<FilterResult> getSink() {
        // TDengine 写入 SQL (使用超级表)
        // 假设超级表 detection_results 已创建：
        // CREATE STABLE detection_results (ts TIMESTAMP, raw_val DOUBLE, filtered_val DOUBLE, is_anomaly TINYINT) TAGS (channel_name BINARY(64));
        String sql = "INSERT INTO ? USING detection_results TAGS (?) VALUES (?, ?, ?, ?)";

        return JdbcSink.sink(
                sql,
                (ps, result) -> {
                    // 子表名通常由通道名称决定，例如：d_channel_001
                    String subTableName = "d_" + result.getChannel().replaceAll("[^a-zA-Z0-9_]", "_");
                    ps.setString(1, subTableName);
                    ps.setString(2, result.getChannel());
                    ps.setLong(3, result.getTimestamp());
                    ps.setDouble(4, result.getOriginalValue());
                    ps.setDouble(5, result.getFilteredValue());
                    ps.setInt(6, result.isAnomaly() ? 1 : 0);
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(1000)
                        .withBatchIntervalMs(200)
                        .withMaxRetries(5)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl("jdbc:TAOS://tdengine-service:6030/yudao_detection")
                        .withDriverName("com.taosdata.jdbc.TSDBDriver")
                        .withUsername("root")
                        .withPassword("taosdata")
                        .build()
        );
    }
}
