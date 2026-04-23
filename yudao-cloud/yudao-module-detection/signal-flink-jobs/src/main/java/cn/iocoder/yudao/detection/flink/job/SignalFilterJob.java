package cn.iocoder.yudao.detection.flink.job;

import cn.iocoder.yudao.detection.flink.filter.ButterworthBandpass;
import cn.iocoder.yudao.detection.flink.util.ExceptionMessages;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * signal-filter-flink-job
 *
 * Consumes raw_topic, applies a 4th-order Butterworth bandpass filter to each voltage
 * fragment (per device+channel), and publishes filtered data to filtered_topic.
 * Parse / filter errors are routed to exception_topic via side output.
 */
public class SignalFilterJob {
    private static final Logger LOG = LoggerFactory.getLogger(SignalFilterJob.class);
    private static final String JOB_NAME = "signal-filter-flink-job";

    public static void main(String[] args) throws Exception {
        String kafkaBroker   = args.length > 0 ? args[0] : "kafka:9092";
        String inputTopic    = args.length > 1 ? args[1] : "raw_topic";
        String outputTopic   = args.length > 2 ? args[2] : "filtered_topic";
        double lowCutoff     = args.length > 3 ? Double.parseDouble(args[3]) : 100_000.0;
        double highCutoff    = args.length > 4 ? Double.parseDouble(args[4]) : 900_000.0;
        double samplingRate  = args.length > 5 ? Double.parseDouble(args[5]) : 2_000_000.0;
        String exceptionTopic = args.length > 6 ? args[6] : ExceptionMessages.DEFAULT_EXCEPTION_TOPIC;

        LOG.info("{} starting: kafka={}, in={}, out={}, bandpass={}-{}Hz, fs={}Hz, exceptionTopic={}",
                 JOB_NAME, kafkaBroker, inputTopic, outputTopic,
                 lowCutoff, highCutoff, samplingRate, exceptionTopic);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBroker)
                .setTopics(inputTopic)
                .setGroupId(JOB_NAME)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // Bandpass filter with error side output
        SingleOutputStreamOperator<String> filteredStream = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaRawSource")
                .keyBy(msg -> {
                    int firstComma = msg.indexOf(',');
                    if (firstComma < 0) return "unknown";
                    String header = msg.substring(0, firstComma);
                    String[] parts = header.split(":");
                    return parts.length >= 2 ? parts[0] + ":" + parts[1] : "unknown";
                })
                .process(new BandpassFilterFunction(samplingRate, lowCutoff, highCutoff))
                .name("ButterworthBandpass");

        // Main path → Kafka filtered_topic
        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers(kafkaBroker)
                .setRecordSerializer(new FilteredMessageSerializer(outputTopic))
                .build();
        filteredStream.sinkTo(kafkaSink).name("KafkaFilteredSink");

        // Exception path → Kafka exception_topic
        ExceptionMessages.wireExceptionSink(filteredStream, kafkaBroker, exceptionTopic, "ExceptionSink");

        env.execute(JOB_NAME);
    }

    /**
     * Stateful bandpass filter with error handling.
     * Parse/filter errors go to EXCEPTION_TAG side output.
     */
    public static class BandpassFilterFunction
            extends KeyedProcessFunction<String, String, String> {
        private static final long serialVersionUID = 1L;

        private final double samplingRate;
        private final double lowCutoff;
        private final double highCutoff;

        private transient ValueState<double[]> filterStateHandle;

        public BandpassFilterFunction(double samplingRate, double lowCutoff, double highCutoff) {
            this.samplingRate = samplingRate;
            this.lowCutoff = lowCutoff;
            this.highCutoff = highCutoff;
        }

        @Override
        public void open(Configuration parameters) {
            filterStateHandle = getRuntimeContext().getState(
                    new ValueStateDescriptor<>("bpFilterState", double[].class));
        }

        @Override
        public void processElement(String message, Context ctx, Collector<String> out)
                throws Exception {
            String deviceId = "";
            String channelStr = "";
            String seqStr = "";
            try {
                int firstComma = message.indexOf(',');
                if (firstComma < 0) {
                    ctx.output(ExceptionMessages.EXCEPTION_TAG,
                            ExceptionMessages.buildErrorJson(JOB_NAME, "parse_error",
                                    "No comma found in message", message));
                    return;
                }

                String header = message.substring(0, firstComma);
                String[] headerParts = header.split(":");
                if (headerParts.length >= 1) deviceId = headerParts[0];
                if (headerParts.length >= 2) channelStr = headerParts[1];
                if (headerParts.length >= 3) seqStr = headerParts[2];

                String voltagesCsv = message.substring(firstComma + 1);
                String[] values = voltagesCsv.split(",");

                if (values.length == 0) {
                    ctx.output(ExceptionMessages.EXCEPTION_TAG,
                            ExceptionMessages.buildErrorJson(JOB_NAME, "parse_error",
                                    "Empty voltage array", message,
                                    deviceId, channelStr, seqStr));
                    return;
                }

                ButterworthBandpass filter = new ButterworthBandpass(samplingRate, lowCutoff, highCutoff);
                double[] savedState = filterStateHandle.value();
                if (savedState != null) {
                    filter.restoreState(savedState);
                }

                StringBuilder sb = new StringBuilder(header);
                for (int i = 0; i < values.length; i++) {
                    double raw = Double.parseDouble(values[i].trim());
                    double filtered = filter.apply(raw);
                    sb.append(',');
                    sb.append(String.format("%.6f", filtered));
                }

                filterStateHandle.update(filter.getState());
                out.collect(sb.toString());

            } catch (Exception e) {
                ctx.output(ExceptionMessages.EXCEPTION_TAG,
                        ExceptionMessages.buildErrorJson(JOB_NAME, "filter_error",
                                e.getClass().getSimpleName() + ": " + e.getMessage(),
                                message, deviceId, channelStr, seqStr));
            }
        }
    }

    public static class FilteredMessageSerializer
            implements KafkaRecordSerializationSchema<String> {
        private static final long serialVersionUID = 1L;
        private final String topic;

        public FilteredMessageSerializer(String topic) {
            this.topic = topic;
        }

        @Override
        public ProducerRecord<byte[], byte[]> serialize(
                String msg, KafkaSinkContext context, Long timestamp) {
            int firstColon = msg.indexOf(':');
            String deviceId = firstColon > 0 ? msg.substring(0, firstColon) : "unknown";
            return new ProducerRecord<>(topic,
                    deviceId.getBytes(StandardCharsets.UTF_8),
                    msg.getBytes(StandardCharsets.UTF_8));
        }
    }
}
