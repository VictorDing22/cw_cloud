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
 * High-throughput batch sink for TDengine filtered_data table.
 *
 * Aligned with TDengineRawSink optimizations:
 *   - Receives raw Kafka message Strings (not pre-expanded RawSignalRecord)
 *   - Parses voltage values during flush() with zero-copy string append
 *   - Pre-compiled regex, sanitize cache, pre-sized StringBuilder
 *   - batchSize counts Kafka messages, not individual samples
 *   - Write failures → exception_topic (dead-letter queue)
 *
 * Differences from TDengineRawSink:
 *   - Target super table: filtered_data (not raw_data)
 *   - Sub-table prefix: f_ (not t_)
 *   - Columns: ts, voltage, seq (no sampling column)
 */
public class TDengineFilteredSink extends RichSinkFunction<String> {
    private static final Logger LOG = LoggerFactory.getLogger(TDengineFilteredSink.class);
    private static final String JOB_NAME = "signal-save-filtered-flink-job";
    private static final int SAMPLING_RATE = 2_000_000;
    private static final long SAMPLE_INTERVAL_NS = 1_000_000_000L / SAMPLING_RATE;
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
        flushCount = 0;
        sanitizeCache = new HashMap<>();

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        exceptionProducer = new KafkaProducer<>(props);

        LOG.info("TDengineFilteredSink opened: url={}, batchMessages={}, flushMs={}, exceptionTopic={}",
                 jdbcUrl, batchSize, flushIntervalMs, exceptionTopic);
    }

    @Override
    public void invoke(String message, Context context) throws Exception {
        buffer.add(message);
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

        for (String message : buffer) {
            try {
                int firstComma = message.indexOf(',');
                if (firstComma < 0) continue;

                int c1 = message.indexOf(':');
                int c2 = message.indexOf(':', c1 + 1);
                int c3 = message.indexOf(':', c2 + 1);

                String deviceId = message.substring(0, c1);
                String channelStr = message.substring(c1 + 1, c2);
                int seq = Integer.parseInt(message.substring(c2 + 1, c3));
                long baseTs = Long.parseLong(message.substring(c3 + 1, firstComma));

                if (firstDeviceId == null) firstDeviceId = deviceId;

                String sanitizedDevice = sanitizeCache.computeIfAbsent(deviceId,
                        k -> SANITIZE_PATTERN.matcher(k.toLowerCase()).replaceAll("_"));
                String subtableKey = sanitizedDevice + "_" + channelStr;
                tableTags.putIfAbsent(subtableKey, new String[]{deviceId, channelStr});

                StringBuilder values = tableValues.computeIfAbsent(subtableKey,
                        k -> new StringBuilder(64 * 1024));

                int sampleIdx = 0;
                int pos = firstComma + 1;
                int msgLen = message.length();

                while (pos < msgLen) {
                    int nextComma = message.indexOf(',', pos);
                    if (nextComma < 0) nextComma = msgLen;

                    long tsNs = baseTs + (long) sampleIdx * SAMPLE_INTERVAL_NS;

                    // filtered_data columns: ts, voltage, seq (no sampling column)
                    values.append('(')
                          .append(tsNs).append(',')
                          .append((CharSequence) message, pos, nextComma).append(',')
                          .append(seq)
                          .append(')');

                    totalRecords++;
                    sampleIdx++;
                    pos = nextComma + 1;
                }
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

        StringBuilder sql = new StringBuilder(totalRecords * 40 + 512);
        sql.append("INSERT INTO ");
        for (Map.Entry<String, StringBuilder> entry : tableValues.entrySet()) {
            String[] tags = tableTags.get(entry.getKey());
            sql.append("f_").append(entry.getKey())
               .append(" USING filtered_data TAGS ('")
               .append(tags[0]).append("', ")
               .append(tags[1]).append(") VALUES ")
               .append(entry.getValue())
               .append(' ');
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql.toString());
            flushCount++;
            if (flushCount <= 5 || flushCount % 200 == 0) {
                LOG.info("Flushed {} msgs ({} records) to TDengine filtered_data [flush #{}]",
                         buffer.size(), totalRecords, flushCount);
            }
        } catch (Exception e) {
            LOG.error("TDengine filtered_data write failed ({} records): {}",
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
        if (buffer != null && !buffer.isEmpty()) {
            flush();
        }
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        if (exceptionProducer != null) {
            exceptionProducer.close();
        }
        LOG.info("TDengineFilteredSink closed after {} flushes", flushCount);
    }
}
