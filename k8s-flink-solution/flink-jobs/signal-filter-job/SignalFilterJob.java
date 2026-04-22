package com.cwcloud.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.async.AsyncFunction;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.apache.flink.configuration.Configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Flink 信号滤波作业
 * 从 Kafka 读取原始信号，调用滤波微服务，写回 Kafka
 */
public class SignalFilterJob {
    
    private static final String KAFKA_BROKERS = System.getenv().getOrDefault("KAFKA_BROKERS", "localhost:9092");
    private static final String INPUT_TOPIC = System.getenv().getOrDefault("INPUT_TOPIC", "sample-input");
    private static final String OUTPUT_TOPIC = System.getenv().getOrDefault("OUTPUT_TOPIC", "sample-output");
    private static final String FILTER_SERVICE_URL = System.getenv().getOrDefault("FILTER_SERVICE_URL", "http://49.235.44.231:8000");
    private static final String FILTER_TYPE = System.getenv().getOrDefault("FILTER_TYPE", "kalman");
    
    public static void main(String[] args) throws Exception {
        // 创建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 设置并行度
        env.setParallelism(4);
        
        // 启用 Checkpoint
        env.enableCheckpointing(10000);
        
        // Kafka Source
        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(KAFKA_BROKERS)
            .setTopics(INPUT_TOPIC)
            .setGroupId("flink-signal-filter")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
        
        // Kafka Sink
        KafkaSink<String> sink = KafkaSink.<String>builder()
            .setBootstrapServers(KAFKA_BROKERS)
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic(OUTPUT_TOPIC)
                .setValueSerializationSchema(new SimpleStringSchema())
                .build())
            .build();
        
        // 数据流处理
        DataStream<String> inputStream = env.fromSource(
            source, 
            WatermarkStrategy.noWatermarks(), 
            "Kafka Source"
        );
        
        // 异步调用滤波服务
        DataStream<String> filteredStream = AsyncDataStream.unorderedWait(
            inputStream,
            new AsyncFilterFunction(),
            30000,  // 超时时间 30s
            java.util.concurrent.TimeUnit.MILLISECONDS,
            100     // 最大并发请求数
        );
        
        // 输出到 Kafka
        filteredStream.sinkTo(sink);
        
        // 执行作业
        env.execute("Signal Filter Job - " + FILTER_TYPE);
    }
    
    /**
     * 异步滤波函数 - 调用远程滤波微服务
     */
    public static class AsyncFilterFunction extends RichAsyncFunction<String, String> {
        
        private transient HttpClient httpClient;
        private transient ObjectMapper objectMapper;
        
        @Override
        public void open(Configuration parameters) {
            httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
            objectMapper = new ObjectMapper();
        }
        
        @Override
        public void asyncInvoke(String input, ResultFuture<String> resultFuture) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    return processSignal(input);
                } catch (Exception e) {
                    // 出错时返回原始数据
                    return input;
                }
            }).thenAccept(result -> {
                resultFuture.complete(Collections.singleton(result));
            });
        }
        
        private String processSignal(String input) throws Exception {
            JsonNode inputNode = objectMapper.readTree(input);
            
            // 提取信号数据
            JsonNode samplesNode = inputNode.get("samples");
            if (samplesNode == null || !samplesNode.isArray()) {
                return input;
            }
            
            // 构建滤波请求
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.set("signal", samplesNode);
            requestBody.put("model", "level");
            requestBody.put("process_noise_var", 1e-3);
            requestBody.put("measurement_noise_var", 1e-2);
            
            // 调用滤波服务
            String filterUrl = FILTER_SERVICE_URL + "/" + FILTER_TYPE + "/audio/run";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(filterUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .timeout(Duration.ofSeconds(10))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("Filter service error: " + response.statusCode());
            }
            
            JsonNode responseNode = objectMapper.readTree(response.body());
            JsonNode filteredSignal = responseNode.get("filtered_signal");
            
            // 构建输出
            ObjectNode output = objectMapper.createObjectNode();
            output.put("type", "signal-data");
            output.put("deviceId", inputNode.has("deviceId") ? inputNode.get("deviceId").asText() : "unknown");
            output.put("timestamp", inputNode.has("timestamp") ? inputNode.get("timestamp").asLong() : System.currentTimeMillis());
            output.put("processedAt", System.currentTimeMillis());
            output.put("sampleRate", inputNode.has("sampleRate") ? inputNode.get("sampleRate").asInt() : 50000);
            
            // 原始信号（取前500个）
            ArrayNode originalSamples = output.putArray("originalSamples");
            for (int i = 0; i < Math.min(500, samplesNode.size()); i++) {
                originalSamples.add(samplesNode.get(i).asDouble());
            }
            
            // 滤波后信号（取前500个）
            ArrayNode filteredSamples = output.putArray("filteredSamples");
            for (int i = 0; i < Math.min(500, filteredSignal.size()); i++) {
                filteredSamples.add(filteredSignal.get(i).asDouble());
            }
            
            output.put("sampleCount", samplesNode.size());
            output.put("filterType", FILTER_TYPE.toUpperCase());
            output.put("mode", "flink-processed");
            
            return objectMapper.writeValueAsString(output);
        }
        
        @Override
        public void timeout(String input, ResultFuture<String> resultFuture) {
            // 超时时返回原始数据
            resultFuture.complete(Collections.singleton(input));
        }
    }
}
