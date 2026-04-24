package cn.iocoder.yudao.detection.flink.job;

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
 * Consumes raw_topic, validates message format, and batch-inserts all voltage
 * samples directly into TDengine raw_data table.
 *
 * Key optimization: raw Kafka messages are passed through the Flink pipeline as
 * single Strings, NOT expanded into individual RawSignalRecord objects (1000x
 * amplification eliminated). Parsing happens in the sink during flush().
 *
 * Protocol: deviceid:通道号:片段号:时间戳,v1,v2,v3,...
 */
public class SignalSaveRawJob {
    private static final Logger LOG = LoggerFactory.getLogger(SignalSaveRawJob.class);
    private static final String JOB_NAME = "signal-saveraw-flink-job";

    public static void main(String[] args) throws Exception {
        String kafkaBroker    = args.length > 0 ? args[0] : "kafka:9092";
        String tdengineUrl    = args.length > 1 ? args[1] : "jdbc:TAOS-RS://tdengine:6041/yudao_detection";
        int    batchSize      = args.length > 2 ? Integer.parseInt(args[2]) : 16;
        String topic          = args.length > 3 ? args[3] : "raw_topic";
        String exceptionTopic = args.length > 4 ? args[4] : ExceptionMessages.DEFAULT_EXCEPTION_TOPIC;

        LOG.info("{} starting: kafka={}, tdengine={}, batchMessages={}, topic={}, exceptionTopic={}",
                 JOB_NAME, kafkaBroker, tdengineUrl, batchSize, topic, exceptionTopic);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(30_000);

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBroker)
                .setTopics(topic)
                .setGroupId(JOB_NAME)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // Validate only — 1:1 pass-through; malformed → exception side output
        SingleOutputStreamOperator<String> validated = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaRawSource")
                .process(new MessageValidator())
                .name("ValidateMessage");

        // keyBy device id extracted from the message header
        validated.keyBy(msg -> {
                    int colon = msg.indexOf(':');
                    return colon > 0 ? msg.substring(0, colon) : msg;
                })
                .addSink(new TDengineRawSink(tdengineUrl, batchSize, 500L, kafkaBroker, exceptionTopic))
                .name("TDengineRawSink");

        ExceptionMessages.wireExceptionSink(validated, kafkaBroker, exceptionTopic, "ExceptionSink");

        env.execute(JOB_NAME);
    }

    /**
     * Lightweight format check: ensures header has exactly 4 colon-separated fields.
     * Valid messages pass through as-is; invalid ones go to exception side output.
     */
    public static class MessageValidator extends ProcessFunction<String, String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void processElement(String message, Context ctx, Collector<String> out) {
            int firstComma = message.indexOf(',');
            if (firstComma < 0) {
                ctx.output(ExceptionMessages.EXCEPTION_TAG,
                        ExceptionMessages.buildErrorJson(JOB_NAME, "parse_error",
                                "No comma found in message (invalid format)", message));
                return;
            }

            String header = message.substring(0, firstComma);
            String[] parts = header.split(":");
            if (parts.length != 4) {
                ctx.output(ExceptionMessages.EXCEPTION_TAG,
                        ExceptionMessages.buildErrorJson(JOB_NAME, "parse_error",
                                "Header requires 4 colon-separated fields, got " + parts.length,
                                message));
                return;
            }

            out.collect(message);
        }
    }
}
