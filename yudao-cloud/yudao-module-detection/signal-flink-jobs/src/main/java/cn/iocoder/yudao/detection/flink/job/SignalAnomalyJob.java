package cn.iocoder.yudao.detection.flink.job;

import cn.iocoder.yudao.detection.flink.sink.TDengineFeatureSink;
import cn.iocoder.yudao.detection.flink.util.AeFeatureCalculator;
import cn.iocoder.yudao.detection.flink.util.AeFeatureCalculator.AeFeatures;
import cn.iocoder.yudao.detection.flink.util.ExceptionMessages;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * signal-anomaly-flink-job (Step 4)
 *
 * Consumes filtered_topic, computes 9 AE features entirely in Java (zero HTTP calls),
 * applies threshold-based anomaly detection, outputs:
 *   1. ALL feature records → TDengine feature_data (via TDengineFeatureSink)
 *   2. Anomaly records only → Kafka anomaly_topic (JSON, for step 5 consumption)
 *   3. Errors → exception_topic
 */
public class SignalAnomalyJob {
    private static final Logger LOG = LoggerFactory.getLogger(SignalAnomalyJob.class);
    private static final String JOB_NAME = "signal-anomaly-flink-job";

    static final OutputTag<String> ANOMALY_TAG = new OutputTag<String>("anomaly") {};

    public static void main(String[] args) throws Exception {
        String kafkaBroker      = args.length > 0 ? args[0] : "kafka:9092";
        String tdengineUrl      = args.length > 1 ? args[1] : "jdbc:TAOS-RS://tdengine:6041/yudao_detection";
        int    batchSize        = args.length > 2 ? Integer.parseInt(args[2]) : 16;
        String inputTopic       = args.length > 3 ? args[3] : "filtered_topic";
        String anomalyTopic     = args.length > 4 ? args[4] : "anomaly_topic";
        String exceptionTopic   = args.length > 5 ? args[5] : ExceptionMessages.DEFAULT_EXCEPTION_TOPIC;
        double ampThreshold     = args.length > 6 ? Double.parseDouble(args[6]) : 0.01;
        double energyThreshold  = args.length > 7 ? Double.parseDouble(args[7]) : 0.1;
        int    countsThreshold  = args.length > 8 ? Integer.parseInt(args[8]) : 3;

        LOG.info("{} starting: kafka={}, in={}, anomalyOut={}, ampTh={}, energyTh={}, countsTh={}",
                JOB_NAME, kafkaBroker, inputTopic, anomalyTopic,
                ampThreshold, energyThreshold, countsThreshold);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(30_000);

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBroker)
                .setTopics(inputTopic)
                .setGroupId(JOB_NAME)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        SingleOutputStreamOperator<String> featureStream = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaFilteredSource")
                .process(new FeatureExtractor(ampThreshold, energyThreshold, countsThreshold))
                .name("AeFeatureExtract");

        // Main output → TDengine feature_data
        featureStream
                .keyBy(record -> {
                    int pipe = record.indexOf('|');
                    return pipe > 0 ? record.substring(0, pipe) : record;
                })
                .addSink(new TDengineFeatureSink(tdengineUrl, batchSize, 500L, kafkaBroker, exceptionTopic))
                .name("TDengineFeatureSink");

        // Side output: anomaly records → anomaly_topic (JSON)
        featureStream.getSideOutput(ANOMALY_TAG)
                .sinkTo(KafkaSink.<String>builder()
                        .setBootstrapServers(kafkaBroker)
                        .setRecordSerializer(new AnomalySerializer(anomalyTopic))
                        .build())
                .name("KafkaAnomalySink");

        // Side output: exceptions → exception_topic
        ExceptionMessages.wireExceptionSink(featureStream, kafkaBroker, exceptionTopic, "ExceptionSink");

        env.execute(JOB_NAME);
    }

    /**
     * Parses filtered CSV, computes AE features, emits pipe-delimited feature string.
     * Anomaly records are also emitted as JSON to ANOMALY_TAG side output.
     */
    public static class FeatureExtractor extends ProcessFunction<String, String> {
        private static final long serialVersionUID = 1L;

        private final double ampThreshold;
        private final double energyThreshold;
        private final int countsThreshold;
        private transient long processedCount;

        public FeatureExtractor(double ampThreshold, double energyThreshold, int countsThreshold) {
            this.ampThreshold = ampThreshold;
            this.energyThreshold = energyThreshold;
            this.countsThreshold = countsThreshold;
        }

        @Override
        public void open(Configuration parameters) {
            processedCount = 0;
        }

        @Override
        public void processElement(String message, Context ctx, Collector<String> out) {
            try {
                int firstComma = message.indexOf(',');
                if (firstComma < 0) {
                    ctx.output(ExceptionMessages.EXCEPTION_TAG,
                            ExceptionMessages.buildErrorJson(JOB_NAME, "parse_error",
                                    "No comma in message", message));
                    return;
                }

                String header = message.substring(0, firstComma);
                String[] parts = header.split(":");
                if (parts.length < 4) {
                    ctx.output(ExceptionMessages.EXCEPTION_TAG,
                            ExceptionMessages.buildErrorJson(JOB_NAME, "parse_error",
                                    "Header requires 4 fields, got " + parts.length, message));
                    return;
                }

                String deviceId = parts[0];
                String channelId = parts[1];
                int seq = Integer.parseInt(parts[2]);
                long timestamp = Long.parseLong(parts[3]);

                int msgLen = message.length();
                int estimatedSamples = (msgLen - firstComma) / 8;
                double[] buf = new double[Math.max(estimatedSamples, 64)];
                int count = 0;
                int pos = firstComma + 1;
                while (pos < msgLen) {
                    int nc = message.indexOf(',', pos);
                    if (nc < 0) nc = msgLen;
                    if (count >= buf.length) {
                        double[] tmp = new double[buf.length * 2];
                        System.arraycopy(buf, 0, tmp, 0, count);
                        buf = tmp;
                    }
                    buf[count++] = Double.parseDouble(message.substring(pos, nc));
                    pos = nc + 1;
                }
                double[] voltages = (count == buf.length) ? buf : java.util.Arrays.copyOf(buf, count);

                AeFeatures feat = AeFeatureCalculator.compute(voltages, AeFeatureCalculator.DEFAULT_SAMPLING_RATE);

                int isError = 0;
                String errorType = "";
                int alertLevel = 0;

                if (feat.amplitude >= ampThreshold) {
                    isError = 1;
                    errorType = "amplitude";
                    alertLevel = feat.amplitude >= ampThreshold * 2 ? 2 : 1;
                } else if (feat.energy >= energyThreshold) {
                    isError = 1;
                    errorType = "energy";
                    alertLevel = feat.energy >= energyThreshold * 2 ? 2 : 1;
                } else if (feat.counts >= countsThreshold) {
                    isError = 1;
                    errorType = "counts";
                    alertLevel = 1;
                }

                StringBuilder sb = new StringBuilder(256);
                sb.append(deviceId).append('|')
                        .append(channelId).append('|')
                        .append(seq).append('|')
                        .append(timestamp).append('|')
                        .append(feat.amplitude).append('|')
                        .append(feat.energy).append('|')
                        .append(feat.area).append('|')
                        .append(feat.skewness).append('|')
                        .append(feat.riseTime).append('|')
                        .append(feat.duration).append('|')
                        .append(feat.counts).append('|')
                        .append(feat.ra).append('|')
                        .append(feat.af).append('|')
                        .append(isError).append('|')
                        .append(errorType).append('|')
                        .append(alertLevel);

                out.collect(sb.toString());

                if (isError == 1) {
                    ctx.output(ANOMALY_TAG, buildAnomalyJson(
                            deviceId, channelId, seq, timestamp, feat, errorType, alertLevel));
                }

                processedCount++;
                if (processedCount <= 5 || processedCount % 2000 == 0) {
                    LOG.info("Feature #{}: device={} ch={} amp={} energy={} counts={} isError={}",
                            processedCount, deviceId, channelId,
                            String.format("%.4f", feat.amplitude),
                            String.format("%.2f", feat.energy),
                            feat.counts, isError);
                }

            } catch (Exception e) {
                ctx.output(ExceptionMessages.EXCEPTION_TAG,
                        ExceptionMessages.buildErrorJson(JOB_NAME, "feature_error",
                                e.getClass().getSimpleName() + ": " + e.getMessage(), message));
            }
        }

        private String buildAnomalyJson(String deviceId, String channelId, int seq,
                                         long timestamp, AeFeatures feat,
                                         String errorType, int alertLevel) {
            StringBuilder sb = new StringBuilder(512);
            sb.append('{');
            sb.append("\"deviceId\":\"").append(deviceId).append("\",");
            sb.append("\"channelId\":").append(channelId).append(',');
            sb.append("\"seq\":").append(seq).append(',');
            sb.append("\"timestamp\":").append(timestamp).append(',');
            sb.append("\"amplitude\":").append(feat.amplitude).append(',');
            sb.append("\"energy\":").append(feat.energy).append(',');
            sb.append("\"area\":").append(feat.area).append(',');
            sb.append("\"skewness\":").append(feat.skewness).append(',');
            sb.append("\"riseTime\":").append(feat.riseTime).append(',');
            sb.append("\"duration\":").append(feat.duration).append(',');
            sb.append("\"counts\":").append(feat.counts).append(',');
            sb.append("\"ra\":").append(feat.ra).append(',');
            sb.append("\"af\":").append(feat.af).append(',');
            sb.append("\"errorType\":\"").append(errorType).append("\",");
            sb.append("\"alertLevel\":").append(alertLevel);
            sb.append('}');
            return sb.toString();
        }
    }

    public static class AnomalySerializer implements KafkaRecordSerializationSchema<String> {
        private static final long serialVersionUID = 1L;
        private final String topic;

        public AnomalySerializer(String topic) { this.topic = topic; }

        @Override
        public ProducerRecord<byte[], byte[]> serialize(String msg, KafkaSinkContext context, Long timestamp) {
            String deviceId = "unknown";
            try {
                int idx = msg.indexOf("\"deviceId\":\"");
                if (idx >= 0) {
                    int start = idx + 12;
                    int end = msg.indexOf('"', start);
                    if (end > start) deviceId = msg.substring(start, end);
                }
            } catch (Exception ignored) {}
            return new ProducerRecord<>(topic,
                    deviceId.getBytes(StandardCharsets.UTF_8),
                    msg.getBytes(StandardCharsets.UTF_8));
        }
    }
}
