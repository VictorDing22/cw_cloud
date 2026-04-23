package cn.iocoder.yudao.detection.flink.job;

import cn.iocoder.yudao.detection.flink.schema.RawSignalRecord;
import cn.iocoder.yudao.detection.flink.sink.TDengineRawSink;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * signal-saveraw-flink-job
 *
 * Consumes raw_topic, parses the edge device protocol, expands each voltage fragment
 * into individual sample records, and batch-inserts into TDengine raw_data table.
 *
 * Protocol: deviceid:通道号:片段号:时间戳,v1,v2,v3,...
 * Sampling rate: 2,000,000 Hz (0.5μs per sample)
 *
 * Usage:
 *   flink run -c cn.iocoder.yudao.detection.flink.job.SignalSaveRawJob signal-flink-jobs.jar \
 *     [kafkaBroker] [tdengineUrl] [batchSize]
 */
public class SignalSaveRawJob {
    private static final Logger LOG = LoggerFactory.getLogger(SignalSaveRawJob.class);
    private static final int DEFAULT_SAMPLING_RATE = 2_000_000;

    public static void main(String[] args) throws Exception {
        String kafkaBroker   = args.length > 0 ? args[0] : "kafka:9092";
        String tdengineUrl   = args.length > 1 ? args[1] : "jdbc:TAOS-RS://tdengine:6041/yudao_detection";
        int    batchSize     = args.length > 2 ? Integer.parseInt(args[2]) : 5000;
        String topic         = args.length > 3 ? args[3] : "raw_topic";

        LOG.info("SignalSaveRawJob starting: kafka={}, tdengine={}, batch={}, topic={}",
                 kafkaBroker, tdengineUrl, batchSize, topic);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBroker)
                .setTopics(topic)
                .setGroupId("signal-saveraw-flink-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> rawStream = env.fromSource(
                kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaRawSource");

        DataStream<RawSignalRecord> records = rawStream
                .flatMap(new RawMessageParser())
                .name("ParseRawMessage");

        records.keyBy(RawSignalRecord::getDeviceId)
               .addSink(new TDengineRawSink(tdengineUrl, batchSize, 500L))
               .name("TDengineRawSink");

        env.execute("signal-saveraw-flink-job");
    }

    /**
     * Parses "deviceid:ch:seq:ts,v1,v2,..." and expands into individual RawSignalRecords.
     * Each voltage sample gets its own timestamp computed from base ts + sample index / samplingRate.
     */
    public static class RawMessageParser implements FlatMapFunction<String, RawSignalRecord> {
        private static final long serialVersionUID = 1L;

        @Override
        public void flatMap(String message, Collector<RawSignalRecord> out) {
            try {
                int firstComma = message.indexOf(',');
                if (firstComma < 0) return;

                String header = message.substring(0, firstComma);
                String[] parts = header.split(":");
                if (parts.length != 4) return;

                String deviceId = parts[0];
                int channelId   = Integer.parseInt(parts[1]);
                int seq         = Integer.parseInt(parts[2]);
                long baseTs     = Long.parseLong(parts[3]);

                String voltagesCsv = message.substring(firstComma + 1);
                String[] values = voltagesCsv.split(",");

                // baseTs is already in nanoseconds from the simulator.
                // At 2MHz, sample interval = 500ns.
                long sampleIntervalNs = 1_000_000_000L / DEFAULT_SAMPLING_RATE; // 500ns

                for (int i = 0; i < values.length; i++) {
                    float voltage = Float.parseFloat(values[i].trim());
                    long tsNs = baseTs + (long) i * sampleIntervalNs;
                    out.collect(new RawSignalRecord(deviceId, channelId, seq, tsNs, voltage, DEFAULT_SAMPLING_RATE));
                }
            } catch (Exception e) {
                // skip malformed messages
            }
        }
    }
}
