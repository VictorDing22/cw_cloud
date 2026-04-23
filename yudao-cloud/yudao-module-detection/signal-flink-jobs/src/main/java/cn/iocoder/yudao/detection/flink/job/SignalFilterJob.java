package cn.iocoder.yudao.detection.flink.job;

import cn.iocoder.yudao.detection.flink.filter.ButterworthBandpass;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
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
 *
 * The bandpass removes:
 *   - Low-frequency noise (mechanical vibration, power-line interference) below lowCutoff
 *   - High-frequency noise (electronic noise, aliasing) above highCutoff
 *
 * Input/output message format (unchanged):
 *   deviceid:ch:seq:ts_ns,v1,v2,v3,...
 *
 * Usage:
 *   flink run -c cn.iocoder.yudao.detection.flink.job.SignalFilterJob signal-flink-jobs.jar \
 *     [kafkaBroker] [inputTopic] [outputTopic] [lowCutoffHz] [highCutoffHz] [samplingRate]
 */
public class SignalFilterJob {
    private static final Logger LOG = LoggerFactory.getLogger(SignalFilterJob.class);

    public static void main(String[] args) throws Exception {
        String kafkaBroker   = args.length > 0 ? args[0] : "kafka:9092";
        String inputTopic    = args.length > 1 ? args[1] : "raw_topic";
        String outputTopic   = args.length > 2 ? args[2] : "filtered_topic";
        double lowCutoff     = args.length > 3 ? Double.parseDouble(args[3]) : 100_000.0;
        double highCutoff    = args.length > 4 ? Double.parseDouble(args[4]) : 900_000.0;
        double samplingRate  = args.length > 5 ? Double.parseDouble(args[5]) : 2_000_000.0;

        LOG.info("SignalFilterJob starting: kafka={}, in={}, out={}, bandpass={}-{}Hz, fs={}Hz",
                 kafkaBroker, inputTopic, outputTopic, lowCutoff, highCutoff, samplingRate);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // --- Source: Kafka raw_topic ---
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBroker)
                .setTopics(inputTopic)
                .setGroupId("signal-filter-flink-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> rawStream = env.fromSource(
                kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaRawSource");

        // --- Process: keyBy(deviceId:channelId) → bandpass filter ---
        DataStream<String> filteredStream = rawStream
                .keyBy(msg -> {
                    int firstComma = msg.indexOf(',');
                    if (firstComma < 0) return "unknown";
                    String header = msg.substring(0, firstComma);
                    String[] parts = header.split(":");
                    return parts.length >= 2 ? parts[0] + ":" + parts[1] : "unknown";
                })
                .process(new BandpassFilterFunction(samplingRate, lowCutoff, highCutoff))
                .name("ButterworthBandpass");

        // --- Sink: Kafka filtered_topic (with deviceId as key for partition routing) ---
        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers(kafkaBroker)
                .setRecordSerializer(new FilteredMessageSerializer(outputTopic))
                .build();

        filteredStream.sinkTo(kafkaSink).name("KafkaFilteredSink");

        env.execute("signal-filter-flink-job");
    }

    /**
     * Stateful bandpass filter: maintains IIR filter state per (device, channel) key.
     * Parses each message, applies Butterworth bandpass to every voltage sample,
     * and emits the filtered message in the same protocol format.
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
            int firstComma = message.indexOf(',');
            if (firstComma < 0) return;

            String header = message.substring(0, firstComma);
            String voltagesCsv = message.substring(firstComma + 1);
            String[] values = voltagesCsv.split(",");

            // Restore or create filter
            ButterworthBandpass filter = new ButterworthBandpass(samplingRate, lowCutoff, highCutoff);
            double[] savedState = filterStateHandle.value();
            if (savedState != null) {
                filter.restoreState(savedState);
            }

            // Apply bandpass to each voltage sample
            StringBuilder sb = new StringBuilder(header);
            for (int i = 0; i < values.length; i++) {
                double raw = Double.parseDouble(values[i].trim());
                double filtered = filter.apply(raw);
                sb.append(',');
                sb.append(String.format("%.6f", filtered));
            }

            // Save filter state for continuity across messages
            filterStateHandle.update(filter.getState());

            out.collect(sb.toString());
        }
    }

    /**
     * Serializes filtered messages to Kafka, using deviceId as the record key
     * to maintain consistent partition routing with raw_topic.
     */
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
