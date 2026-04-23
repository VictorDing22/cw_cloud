package cn.iocoder.yudao.detection.flink.sink;

import cn.iocoder.yudao.detection.flink.schema.RawSignalRecord;
import cn.iocoder.yudao.detection.flink.util.ExceptionMessages;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Batch sink for TDengine filtered_data table.
 * Write failures are sent to exception_topic (dead-letter queue) instead of crashing.
 */
public class TDengineFilteredSink extends RichSinkFunction<RawSignalRecord> {
    private static final Logger LOG = LoggerFactory.getLogger(TDengineFilteredSink.class);
    private static final String JOB_NAME = "signal-save-filtered-flink-job";

    private final String jdbcUrl;
    private final int batchSize;
    private final long flushIntervalMs;
    private final String kafkaBroker;
    private final String exceptionTopic;

    private transient Connection connection;
    private transient List<RawSignalRecord> buffer;
    private transient long lastFlushTime;
    private transient KafkaProducer<String, String> exceptionProducer;

    public TDengineFilteredSink(String jdbcUrl, int batchSize, long flushIntervalMs,
                                String kafkaBroker, String exceptionTopic) {
        this.jdbcUrl = jdbcUrl;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.kafkaBroker = kafkaBroker;
        this.exceptionTopic = exceptionTopic;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        Class.forName("com.taosdata.jdbc.rs.RestfulDriver");
        connection = DriverManager.getConnection(jdbcUrl, "root", "taosdata");
        buffer = new ArrayList<>(batchSize);
        lastFlushTime = System.currentTimeMillis();

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        exceptionProducer = new KafkaProducer<>(props);

        LOG.info("TDengineFilteredSink opened, url={}, batchSize={}, exceptionTopic={}",
                 jdbcUrl, batchSize, exceptionTopic);
    }

    @Override
    public void invoke(RawSignalRecord record, Context context) throws Exception {
        buffer.add(record);
        long now = System.currentTimeMillis();
        if (buffer.size() >= batchSize || (now - lastFlushTime) >= flushIntervalMs) {
            flush();
        }
    }

    private void flush() {
        if (buffer.isEmpty()) return;

        Map<String, List<RawSignalRecord>> grouped = new HashMap<>();
        for (RawSignalRecord r : buffer) {
            String key = sanitize(r.getDeviceId()) + "_" + r.getChannelId();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ");
        String firstDeviceId = buffer.get(0).getDeviceId();
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
            LOG.info("Flushed {} filtered records to TDengine", buffer.size());
        } catch (Exception e) {
            LOG.error("TDengine filtered_data write failed ({} records), sending to exception_topic: {}",
                      buffer.size(), e.getMessage());
            sendToExceptionTopic(firstDeviceId, buffer.size(), e.getMessage());
        }

        buffer.clear();
        lastFlushTime = System.currentTimeMillis();
    }

    private void sendToExceptionTopic(String deviceId, int recordCount, String errorMsg) {
        try {
            String json = ExceptionMessages.buildWriteErrorJson(
                    JOB_NAME, recordCount, deviceId, errorMsg);
            exceptionProducer.send(new ProducerRecord<>(exceptionTopic, deviceId, json));
            exceptionProducer.flush();
        } catch (Exception ex) {
            LOG.error("Failed to send to exception_topic: {}", ex.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        if (buffer != null && !buffer.isEmpty()) {
            flush();
        }
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        if (exceptionProducer != null) {
            exceptionProducer.close();
        }
        LOG.info("TDengineFilteredSink closed");
    }

    private static String sanitize(String input) {
        return input.toLowerCase().replaceAll("[^a-z0-9]", "_");
    }
}
