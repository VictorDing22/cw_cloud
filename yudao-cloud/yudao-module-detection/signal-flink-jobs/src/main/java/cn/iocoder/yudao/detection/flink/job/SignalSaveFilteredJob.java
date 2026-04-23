package cn.iocoder.yudao.detection.flink.job;

import cn.iocoder.yudao.detection.flink.schema.RawSignalRecord;
import cn.iocoder.yudao.detection.flink.sink.TDengineFilteredSink;
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
 * signal-save-filtered-flink-job
 *
 * Consumes filtered_topic, parses the same protocol format, expands voltage fragments
 * into individual records, and batch-inserts into TDengine filtered_data table.
 *
 * This is the filtered-data counterpart of SignalSaveRawJob. The message format is
 * identical: deviceid:ch:seq:ts_ns,v1,v2,...
 *
 * Usage:
 *   flink run -c cn.iocoder.yudao.detection.flink.job.SignalSaveFilteredJob signal-flink-jobs.jar \
 *     [kafkaBroker] [tdengineUrl] [batchSize] [topic]
 */
public class SignalSaveFilteredJob {
    private static final Logger LOG = LoggerFactory.getLogger(SignalSaveFilteredJob.class);
    private static final int DEFAULT_SAMPLING_RATE = 2_000_000;

    public static void main(String[] args) throws Exception {
        String kafkaBroker   = args.length > 0 ? args[0] : "kafka:9092";
        String tdengineUrl   = args.length > 1 ? args[1] : "jdbc:TAOS-RS://tdengine:6041/yudao_detection";
        int    batchSize     = args.length > 2 ? Integer.parseInt(args[2]) : 5000;
        String topic         = args.length > 3 ? args[3] : "filtered_topic";

        LOG.info("SignalSaveFilteredJob starting: kafka={}, tdengine={}, batch={}, topic={}",
                 kafkaBroker, tdengineUrl, batchSize, topic);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBroker)
                .setTopics(topic)
                .setGroupId("signal-save-filtered-flink-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> filteredStream = env.fromSource(
                kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaFilteredSource");

        DataStream<RawSignalRecord> records = filteredStream
                .flatMap(new FilteredMessageParser())
                .name("ParseFilteredMessage");

        records.keyBy(RawSignalRecord::getDeviceId)
               .addSink(new TDengineFilteredSink(tdengineUrl, batchSize, 500L))
               .name("TDengineFilteredSink");

        env.execute("signal-save-filtered-flink-job");
    }

    /**
     * Same parser as SignalSaveRawJob.RawMessageParser — the wire format is identical.
     * Reuses RawSignalRecord POJO (voltage field holds filtered voltage).
     */
    public static class FilteredMessageParser implements FlatMapFunction<String, RawSignalRecord> {
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

                long sampleIntervalNs = 1_000_000_000L / DEFAULT_SAMPLING_RATE;

                for (int i = 0; i < values.length; i++) {
                    float voltage = Float.parseFloat(values[i].trim());
                    long tsNs = baseTs + (long) i * sampleIntervalNs;
                    out.collect(new RawSignalRecord(deviceId, channelId, seq, tsNs, voltage, DEFAULT_SAMPLING_RATE));
                }
            } catch (Exception e) {
                // skip malformed
            }
        }
    }
}
