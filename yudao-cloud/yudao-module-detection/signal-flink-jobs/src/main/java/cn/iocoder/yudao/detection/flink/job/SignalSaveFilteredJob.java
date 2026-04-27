package cn.iocoder.yudao.detection.flink.job;

import cn.iocoder.yudao.detection.flink.sink.TDengineFilteredSink;
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
 * signal-save-filtered-flink-job
 *
 * Consumes filtered_topic and batch-inserts into TDengine filtered_data table.
 *
 * Aligned with SignalSaveRawJob optimizations:
 *   - Raw Kafka messages pass through as Strings (no 1→1000 RawSignalRecord expansion)
 *   - Parsing deferred to TDengineFilteredSink.flush()
 *   - batchSize counts Kafka messages, not individual samples
 */
public class SignalSaveFilteredJob {
    private static final Logger LOG = LoggerFactory.getLogger(SignalSaveFilteredJob.class);
    private static final String JOB_NAME = "signal-save-filtered-flink-job";

    public static void main(String[] args) throws Exception {
        String kafkaBroker    = args.length > 0 ? args[0] : "kafka:9092";
        String tdengineUrl    = args.length > 1 ? args[1] : "jdbc:TAOS-RS://tdengine:6041/yudao_detection";
        int    batchSize      = args.length > 2 ? Integer.parseInt(args[2]) : 16;
        String topic          = args.length > 3 ? args[3] : "filtered_topic";
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

        SingleOutputStreamOperator<String> validated = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaFilteredSource")
                .process(new MessageValidator())
                .name("ValidateFilteredMessage");

        validated.keyBy(msg -> {
                    int colon = msg.indexOf(':');
                    return colon > 0 ? msg.substring(0, colon) : msg;
                })
                .addSink(new TDengineFilteredSink(tdengineUrl, batchSize, 500L, kafkaBroker, exceptionTopic))
                .name("TDengineFilteredSink");

        ExceptionMessages.wireExceptionSink(validated, kafkaBroker, exceptionTopic, "ExceptionSink");

        env.execute(JOB_NAME);
    }

    public static class MessageValidator extends ProcessFunction<String, String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void processElement(String message, Context ctx, Collector<String> out) {
            int firstComma = message.indexOf(',');
            if (firstComma < 0) {
                ctx.output(ExceptionMessages.EXCEPTION_TAG,
                        ExceptionMessages.buildErrorJson(JOB_NAME, "parse_error",
                                "No comma found in message", message));
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
