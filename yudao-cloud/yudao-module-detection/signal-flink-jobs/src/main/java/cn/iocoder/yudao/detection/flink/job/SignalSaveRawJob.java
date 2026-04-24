package cn.iocoder.yudao.detection.flink.job;

import cn.iocoder.yudao.detection.flink.schema.RawSignalRecord;
import cn.iocoder.yudao.detection.flink.sink.TDengineRawSink;
import cn.iocoder.yudao.detection.flink.util.ExceptionMessages;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * signal-saveraw-flink-job
 *
 * Consumes raw_topic, parses the edge device protocol, expands each voltage fragment
 * into individual sample records, and batch-inserts into TDengine raw_data table.
 * Malformed / unparseable messages are routed to exception_topic for traceability.
 *
 * Protocol: deviceid:通道号:片段号:时间戳,v1,v2,v3,...
 */
public class SignalSaveRawJob {
    private static final Logger LOG = LoggerFactory.getLogger(SignalSaveRawJob.class);
    private static final String JOB_NAME = "signal-saveraw-flink-job";
    private static final int DEFAULT_SAMPLING_RATE = 2_000_000;

    public static void main(String[] args) throws Exception {
        String kafkaBroker   = args.length > 0 ? args[0] : "kafka:9092";
        String tdengineUrl   = args.length > 1 ? args[1] : "jdbc:TAOS-RS://tdengine:6041/yudao_detection";
        int    batchSize     = args.length > 2 ? Integer.parseInt(args[2]) : 5000;
        String topic         = args.length > 3 ? args[3] : "raw_topic";
        String exceptionTopic = args.length > 4 ? args[4] : ExceptionMessages.DEFAULT_EXCEPTION_TOPIC;

        LOG.info("{} starting: kafka={}, tdengine={}, batch={}, topic={}, exceptionTopic={}",
                 JOB_NAME, kafkaBroker, tdengineUrl, batchSize, topic, exceptionTopic);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(30_000);   // checkpoint every 30s → commits Kafka offsets

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBroker)
                .setTopics(topic)
                .setGroupId(JOB_NAME)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // Parse raw messages; malformed messages go to exception side output
        SingleOutputStreamOperator<RawSignalRecord> records = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaRawSource")
                .process(new RawMessageParser())
                .name("ParseRawMessage");

        // Main path → TDengine raw_data
        records.keyBy(RawSignalRecord::getDeviceId)
               .addSink(new TDengineRawSink(tdengineUrl, batchSize, 500L, kafkaBroker, exceptionTopic))
               .name("TDengineRawSink");

        // Exception path → Kafka exception_topic
        ExceptionMessages.wireExceptionSink(records, kafkaBroker, exceptionTopic, "ExceptionSink");

        env.execute(JOB_NAME);
    }

    /**
     * Parses "deviceid:ch:seq:ts,v1,v2,..." and expands into individual RawSignalRecords.
     * Parse failures are emitted to the EXCEPTION_TAG side output instead of being silently dropped.
     */
    public static class RawMessageParser
            extends ProcessFunction<String, RawSignalRecord> {
        private static final long serialVersionUID = 1L;

        @Override
        public void processElement(String message, Context ctx, Collector<RawSignalRecord> out) {
            String deviceId = "";
            String channelStr = "";
            String seqStr = "";
            try {
                int firstComma = message.indexOf(',');
                if (firstComma < 0) {
                    ctx.output(ExceptionMessages.EXCEPTION_TAG,
                            ExceptionMessages.buildErrorJson(JOB_NAME, "parse_error",
                                    "No comma found in message (invalid format)", message));
                    return;
                }

                String header = message.substring(0, firstComma);
                String[] parts = header.split(":");
                if (parts.length >= 1) deviceId = parts[0];
                if (parts.length >= 2) channelStr = parts[1];
                if (parts.length >= 3) seqStr = parts[2];

                if (parts.length != 4) {
                    ctx.output(ExceptionMessages.EXCEPTION_TAG,
                            ExceptionMessages.buildErrorJson(JOB_NAME, "parse_error",
                                    "Header requires 4 colon-separated fields, got " + parts.length,
                                    message, deviceId, channelStr, seqStr));
                    return;
                }

                int channelId = Integer.parseInt(channelStr);
                int seq       = Integer.parseInt(seqStr);
                long baseTs   = Long.parseLong(parts[3]);

                String voltagesCsv = message.substring(firstComma + 1);
                String[] values = voltagesCsv.split(",");

                if (values.length == 0) {
                    ctx.output(ExceptionMessages.EXCEPTION_TAG,
                            ExceptionMessages.buildErrorJson(JOB_NAME, "parse_error",
                                    "Empty voltage array", message, deviceId, channelStr, seqStr));
                    return;
                }

                long sampleIntervalNs = 1_000_000_000L / DEFAULT_SAMPLING_RATE;

                for (int i = 0; i < values.length; i++) {
                    float voltage = Float.parseFloat(values[i].trim());
                    long tsNs = baseTs + (long) i * sampleIntervalNs;
                    out.collect(new RawSignalRecord(deviceId, channelId, seq, tsNs, voltage, DEFAULT_SAMPLING_RATE));
                }
            } catch (Exception e) {
                ctx.output(ExceptionMessages.EXCEPTION_TAG,
                        ExceptionMessages.buildErrorJson(JOB_NAME, "parse_error",
                                e.getClass().getSimpleName() + ": " + e.getMessage(),
                                message, deviceId, channelStr, seqStr));
            }
        }
    }
}
