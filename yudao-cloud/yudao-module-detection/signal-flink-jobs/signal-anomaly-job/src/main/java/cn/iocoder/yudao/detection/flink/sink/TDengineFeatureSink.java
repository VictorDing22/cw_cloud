package cn.iocoder.yudao.detection.flink.sink;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * High-throughput batch sink for TDengine feature_data table.
 *
 * Input: pipe-delimited feature strings produced by SignalAnomalyJob:
 *   deviceId|channelId|seq|timestamp|amplitude|energy|area|skewness|
 *   rise_time|duration|counts|ra|af|is_error|error_type|alert_level
 *
 * Sub-table naming: a_{device}_{channel}  (prefix "a_" for anomaly/feature)
 */
public class TDengineFeatureSink extends RichSinkFunction<String> {
    private static final Logger LOG = LoggerFactory.getLogger(TDengineFeatureSink.class);
    private static final String JOB_NAME = "signal-anomaly-flink-job";
    private static final Pattern SANITIZE_PATTERN = Pattern.compile("[^a-z0-9]");

    private final String jdbcUrl;
    private final int batchSize;
    private final long flushIntervalMs;
    private final String kafkaBroker;
    private final String exceptionTopic;

    private transient Connection connection;
    private transient List<String> buffer;
    private transient long lastFlushTime;
    private transient long flushCount;
    private transient KafkaProducer<String, String> exceptionProducer;
    private transient Map<String, String> sanitizeCache;

    public TDengineFeatureSink(String jdbcUrl, int batchSize, long flushIntervalMs,
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
        flushCount = 0;
        sanitizeCache = new HashMap<>();

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        exceptionProducer = new KafkaProducer<>(props);

        LOG.info("TDengineFeatureSink opened: url={}, batch={}, flushMs={}", jdbcUrl, batchSize, flushIntervalMs);
    }

    @Override
    public void invoke(String record, Context context) throws Exception {
        buffer.add(record);
        long now = System.currentTimeMillis();
        if (buffer.size() >= batchSize || (now - lastFlushTime) >= flushIntervalMs) {
            flush();
        }
    }

    private void flush() {
        if (buffer.isEmpty()) return;

        Map<String, StringBuilder> tableValues = new LinkedHashMap<>();
        Map<String, String[]> tableTags = new HashMap<>();
        String firstDeviceId = null;
        int totalRecords = 0;

        for (String record : buffer) {
            try {
                String[] f = record.split("\\|", -1);
                if (f.length < 16) continue;

                String deviceId = f[0];
                String channelId = f[1];
                String seq = f[2];
                String tsNs = f[3];
                String amplitude = f[4];
                String energy = f[5];
                String area = f[6];
                String skewness = f[7];
                String riseTime = f[8];
                String hitDuration = f[9];
                String counts = f[10];
                String ra = f[11];
                String af = f[12];
                String isError = f[13];
                String errorType = f[14];
                String alertLevel = f[15];

                if (firstDeviceId == null) firstDeviceId = deviceId;

                String sanitized = sanitizeCache.computeIfAbsent(deviceId,
                        k -> SANITIZE_PATTERN.matcher(k.toLowerCase()).replaceAll("_"));
                String subtableKey = sanitized + "_" + channelId;
                tableTags.putIfAbsent(subtableKey, new String[]{deviceId, channelId});

                StringBuilder values = tableValues.computeIfAbsent(subtableKey,
                        k -> new StringBuilder(4096));

                values.append('(')
                        .append(tsNs).append(',')
                        .append(amplitude).append(',')
                        .append(energy).append(',')
                        .append(area).append(',')
                        .append(skewness).append(',')
                        .append(riseTime).append(',')
                        .append(hitDuration).append(',')
                        .append(counts).append(',')
                        .append(ra).append(',')
                        .append(af).append(',')
                        .append(isError).append(',')
                        .append('\'').append(errorType).append("',")
                        .append(alertLevel).append(',')
                        .append("0.0,0.0,")  // loc_x, loc_y (reserved for step 5)
                        .append(seq)
                        .append(')');

                totalRecords++;
            } catch (Exception e) {
                if (firstDeviceId == null) firstDeviceId = "unknown";
                sendToExceptionTopic(firstDeviceId, 0,
                        "flush parse error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        if (totalRecords == 0) {
            buffer.clear();
            lastFlushTime = System.currentTimeMillis();
            return;
        }

        StringBuilder sql = new StringBuilder(totalRecords * 200 + 512);
        sql.append("INSERT INTO ");
        for (Map.Entry<String, StringBuilder> entry : tableValues.entrySet()) {
            String[] tags = tableTags.get(entry.getKey());
            sql.append("a_").append(entry.getKey())
                    .append(" USING feature_data TAGS ('")
                    .append(tags[0]).append("', ")
                    .append(tags[1]).append(") VALUES ")
                    .append(entry.getValue())
                    .append(' ');
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql.toString());
            flushCount++;
            if (flushCount <= 5 || flushCount % 200 == 0) {
                LOG.info("Flushed {} msgs ({} feature records) to feature_data [flush #{}]",
                        buffer.size(), totalRecords, flushCount);
            }
        } catch (Exception e) {
            LOG.error("TDengine feature_data write failed ({} records): {}",
                    totalRecords, e.getMessage());
            sendToExceptionTopic(firstDeviceId != null ? firstDeviceId : "unknown",
                    totalRecords, e.getMessage());
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
        if (buffer != null && !buffer.isEmpty()) flush();
        if (connection != null && !connection.isClosed()) connection.close();
        if (exceptionProducer != null) exceptionProducer.close();
        LOG.info("TDengineFeatureSink closed after {} flushes", flushCount);
    }
}
