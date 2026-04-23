package cn.iocoder.yudao.detection.flink.sink;

import cn.iocoder.yudao.detection.flink.schema.RawSignalRecord;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Batch sink for TDengine filtered_data table.
 * Same batching strategy as TDengineRawSink, but targets the filtered_data stable
 * which has columns: ts TIMESTAMP, voltage FLOAT, seq INT
 * and tags: device_id NCHAR(32), channel_id TINYINT.
 */
public class TDengineFilteredSink extends RichSinkFunction<RawSignalRecord> {
    private static final Logger LOG = LoggerFactory.getLogger(TDengineFilteredSink.class);

    private final String jdbcUrl;
    private final int batchSize;
    private final long flushIntervalMs;

    private transient Connection connection;
    private transient List<RawSignalRecord> buffer;
    private transient long lastFlushTime;

    public TDengineFilteredSink(String jdbcUrl, int batchSize, long flushIntervalMs) {
        this.jdbcUrl = jdbcUrl;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        Class.forName("com.taosdata.jdbc.rs.RestfulDriver");
        connection = DriverManager.getConnection(jdbcUrl, "root", "taosdata");
        buffer = new ArrayList<>(batchSize);
        lastFlushTime = System.currentTimeMillis();
        LOG.info("TDengineFilteredSink opened, url={}, batchSize={}", jdbcUrl, batchSize);
    }

    @Override
    public void invoke(RawSignalRecord record, Context context) throws Exception {
        buffer.add(record);
        long now = System.currentTimeMillis();
        if (buffer.size() >= batchSize || (now - lastFlushTime) >= flushIntervalMs) {
            flush();
        }
    }

    private void flush() throws Exception {
        if (buffer.isEmpty()) return;

        Map<String, List<RawSignalRecord>> grouped = new HashMap<>();
        for (RawSignalRecord r : buffer) {
            String key = sanitize(r.getDeviceId()) + "_" + r.getChannelId();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        // filtered_data columns: ts, voltage, seq (no 'sampling' column)
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        for (Map.Entry<String, List<RawSignalRecord>> entry : grouped.entrySet()) {
            List<RawSignalRecord> records = entry.getValue();
            RawSignalRecord first = records.get(0);
            sql.append("f_").append(entry.getKey())
               .append(" USING filtered_data TAGS ('")
               .append(first.getDeviceId()).append("', ")
               .append(first.getChannelId()).append(") VALUES ");
            for (RawSignalRecord r : records) {
                sql.append('(')
                   .append(r.getTimestampNs()).append(", ")
                   .append(String.format("%.8f", r.getVoltage())).append(", ")
                   .append(r.getSeq())
                   .append(") ");
            }
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql.toString());
        } catch (Exception e) {
            LOG.error("TDengine filtered batch insert failed ({} records): {}", buffer.size(), e.getMessage());
            throw e;
        }

        int count = buffer.size();
        buffer.clear();
        lastFlushTime = System.currentTimeMillis();
        LOG.info("Flushed {} filtered records to TDengine", count);
    }

    @Override
    public void close() throws Exception {
        if (buffer != null && !buffer.isEmpty()) {
            try { flush(); } catch (Exception e) { LOG.error("Final flush failed", e); }
        }
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        LOG.info("TDengineFilteredSink closed");
    }

    private static String sanitize(String input) {
        return input.toLowerCase().replaceAll("[^a-z0-9]", "_");
    }
}
