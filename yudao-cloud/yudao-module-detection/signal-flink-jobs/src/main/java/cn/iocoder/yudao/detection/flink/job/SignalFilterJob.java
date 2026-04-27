package cn.iocoder.yudao.detection.flink.job;

import cn.iocoder.yudao.detection.flink.util.ExceptionMessages;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * signal-filter-flink-job (方案 B — 微服务滤波)
 *
 * 消费 raw_topic，通过异步 HTTP 调用 filter-gateway 微服务（盛老师 Kalman/RLS/LS API），
 * 将滤波后的干净波形写入 filtered_topic。
 * 滤波失败 / 超时的消息路由到 exception_topic。
 */
public class SignalFilterJob {
    private static final Logger LOG = LoggerFactory.getLogger(SignalFilterJob.class);
    private static final String JOB_NAME = "signal-filter-flink-job";
    static final String ERROR_PREFIX = "__FILTER_ERR__:";

    public static void main(String[] args) throws Exception {
        String kafkaBroker      = args.length > 0 ? args[0] : "kafka:9092";
        String inputTopic       = args.length > 1 ? args[1] : "raw_topic";
        String outputTopic      = args.length > 2 ? args[2] : "filtered_topic";
        String filterGatewayUrl = args.length > 3 ? args[3] : "http://filter-gateway:8010";
        String filterType       = args.length > 4 ? args[4] : "kalman";
        String exceptionTopic   = args.length > 5 ? args[5] : ExceptionMessages.DEFAULT_EXCEPTION_TOPIC;

        LOG.info("{} starting: kafka={}, in={}, out={}, gateway={}, filterType={}, exceptionTopic={}",
                 JOB_NAME, kafkaBroker, inputTopic, outputTopic,
                 filterGatewayUrl, filterType, exceptionTopic);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(30_000);

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBroker)
                .setTopics(inputTopic)
                .setGroupId(JOB_NAME)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> inputStream = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaRawSource");

        // Async HTTP → filter-gateway，最大 100 并发请求，30s 超时
        DataStream<String> asyncResult = AsyncDataStream.unorderedWait(
                inputStream,
                new AsyncFilterFunction(filterGatewayUrl, filterType),
                30_000,
                TimeUnit.MILLISECONDS,
                100);

        // 拆分正常 / 异常流
        SingleOutputStreamOperator<String> filtered = asyncResult
                .process(new ErrorSplitter())
                .name("SplitFilterErrors");

        // 正常流 → filtered_topic
        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers(kafkaBroker)
                .setRecordSerializer(new FilteredMessageSerializer(outputTopic))
                .build();
        filtered.sinkTo(kafkaSink).name("KafkaFilteredSink");

        // 异常流 → exception_topic
        ExceptionMessages.wireExceptionSink(filtered, kafkaBroker, exceptionTopic, "FilterExceptionSink");

        env.execute(JOB_NAME);
    }

    // =========================================================================
    //  AsyncFilterFunction — 异步 HTTP 调用 filter-gateway
    // =========================================================================
    public static class AsyncFilterFunction extends RichAsyncFunction<String, String> {
        private static final long serialVersionUID = 1L;

        private final String filterGatewayUrl;
        private final String filterType;

        private transient HttpClient httpClient;
        private transient ObjectMapper objectMapper;
        private transient ExecutorService executor;
        private transient long requestCount;

        public AsyncFilterFunction(String filterGatewayUrl, String filterType) {
            this.filterGatewayUrl = filterGatewayUrl;
            this.filterType = filterType;
        }

        @Override
        public void open(Configuration parameters) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            objectMapper = new ObjectMapper();
            executor = Executors.newFixedThreadPool(
                    Math.max(8, Runtime.getRuntime().availableProcessors() * 2));
            requestCount = 0;
        }

        @Override
        public void close() {
            if (executor != null) {
                executor.shutdown();
                try { executor.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            }
        }

        @Override
        public void asyncInvoke(String message, ResultFuture<String> resultFuture) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    return processMessage(message);
                } catch (Exception e) {
                    return ERROR_PREFIX + ExceptionMessages.buildErrorJson(
                            JOB_NAME, "filter_error",
                            e.getClass().getSimpleName() + ": " + e.getMessage(),
                            message);
                }
            }, executor).thenAccept(result ->
                    resultFuture.complete(Collections.singleton(result))
            );
        }

        private String processMessage(String message) throws Exception {
            int firstComma = message.indexOf(',');
            if (firstComma < 0) {
                throw new IllegalArgumentException("No comma in message (invalid format)");
            }

            String header = message.substring(0, firstComma);
            String[] headerParts = header.split(":");
            if (headerParts.length < 4) {
                throw new IllegalArgumentException("Header requires 4 colon-separated fields, got " + headerParts.length);
            }

            // 提取电压值 → JSON 数组
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode signalArray = requestBody.putArray("signal");

            int pos = firstComma + 1;
            int msgLen = message.length();
            while (pos < msgLen) {
                int nextComma = message.indexOf(',', pos);
                if (nextComma < 0) nextComma = msgLen;
                signalArray.add(Double.parseDouble(message.substring(pos, nextComma)));
                pos = nextComma + 1;
            }
            requestBody.put("filter_type", filterType);

            // HTTP POST → filter-gateway /filter
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(filterGatewayUrl + "/filter"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("filter-gateway returned " + response.statusCode()
                        + ": " + response.body().substring(0, Math.min(200, response.body().length())));
            }

            JsonNode responseNode = objectMapper.readTree(response.body());
            JsonNode filteredSignal = responseNode.get("filtered_signal");

            if (filteredSignal == null || !filteredSignal.isArray()) {
                throw new RuntimeException("Response missing 'filtered_signal' array");
            }

            // 重建 CSV 消息：header + 滤波后电压
            StringBuilder sb = new StringBuilder(message.length());
            sb.append(header);
            for (int i = 0; i < filteredSignal.size(); i++) {
                sb.append(',');
                sb.append(String.format("%.6f", filteredSignal.get(i).asDouble()));
            }

            requestCount++;
            if (requestCount <= 5 || requestCount % 500 == 0) {
                LOG.info("filter-gateway OK: requests={}, samples={}", requestCount, filteredSignal.size());
            }

            return sb.toString();
        }

        @Override
        public void timeout(String input, ResultFuture<String> resultFuture) {
            resultFuture.complete(Collections.singleton(
                    ERROR_PREFIX + ExceptionMessages.buildErrorJson(
                            JOB_NAME, "timeout",
                            "filter-gateway call timed out (30s)",
                            input)));
        }
    }

    // =========================================================================
    //  ErrorSplitter — 将 ERROR_PREFIX 标记的消息路由到 exception side output
    // =========================================================================
    public static class ErrorSplitter extends ProcessFunction<String, String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void processElement(String value, Context ctx, Collector<String> out) {
            if (value.startsWith(ERROR_PREFIX)) {
                ctx.output(ExceptionMessages.EXCEPTION_TAG, value.substring(ERROR_PREFIX.length()));
            } else {
                out.collect(value);
            }
        }
    }

    // =========================================================================
    //  FilteredMessageSerializer — 按 deviceId 分区写入 filtered_topic
    // =========================================================================
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
