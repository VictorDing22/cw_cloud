package com.floatdata.spark;

import com.floatdata.processor.AnomalyDetector;
import com.floatdata.utils.AnomalyResult;
import com.floatdata.utils.ConfigLoader;
import com.floatdata.utils.SignalData;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.spark.SparkConf;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka010.ConsumerStrategies;
import org.apache.spark.streaming.kafka010.KafkaUtils;
import org.apache.spark.streaming.kafka010.LocationStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Properties;

/**
 * Spark 流处理器 - 实时处理 Kafka 中的信号数据
 * 
 * 启动方式:
 * StreamProcessor processor = new StreamProcessor();
 * processor.start();
 */
public class StreamProcessor {
    private static final Logger logger = LoggerFactory.getLogger(StreamProcessor.class);
    private JavaStreamingContext jssc;
    private final String appName;
    private final String master;
    private final long batchInterval;
    private final String kafkaBootstrapServers;
    private final String kafkaInputTopic;
    private final String kafkaOutputTopic;

    public StreamProcessor() {
        this.appName = ConfigLoader.getString("spark.app.name", "AcousticEmissionProcessor");
        this.master = ConfigLoader.getString("spark.master", "local[4]");
        this.batchInterval = ConfigLoader.getLong("spark.streaming.batch.interval", 2000);
        this.kafkaBootstrapServers = ConfigLoader.getString("kafka.bootstrap.servers", "localhost:9092");
        this.kafkaInputTopic = ConfigLoader.getString("kafka.topic.signal", "acoustic-emission-signal");
        this.kafkaOutputTopic = ConfigLoader.getString("kafka.topic.result", "anomaly-detection-result");
    }

    public void start() {
        try {
            // 1. 创建 Spark 配置
            SparkConf conf = new SparkConf()
                    .setAppName(appName)
                    .setMaster(master)
                    .set("spark.streaming.kafka.maxRatePerPartition", 
                         ConfigLoader.getString("spark.streaming.kafka.max.rate.per.partition", "1000"))
                    .set("spark.executor.cores", 
                         ConfigLoader.getString("spark.executor.cores", "4"))
                    .set("spark.executor.memory", 
                         ConfigLoader.getString("spark.executor.memory", "2g"));

            // 2. 创建流处理上下文
            jssc = new JavaStreamingContext(conf, Durations.milliseconds(batchInterval));
            
            logger.info("Spark 流处理器启动: appName={}, master={}, batchInterval={}ms",
                    appName, master, batchInterval);

            // 3. 配置 Kafka 消费者
            Map<String, Object> kafkaParams = new HashMap<>();
            kafkaParams.put("bootstrap.servers", kafkaBootstrapServers);
            kafkaParams.put("key.deserializer", StringDeserializer.class);
            kafkaParams.put("value.deserializer", StringDeserializer.class);
            kafkaParams.put("group.id", "acoustic-emission-group");
            kafkaParams.put("auto.offset.reset", "latest");
            kafkaParams.put("enable.auto.commit", true);

            Collection<String> topics = Collections.singletonList(kafkaInputTopic);

            // 4. 创建 Kafka 数据流
            JavaInputDStream<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>> 
                kafkaStream = KafkaUtils.createDirectStream(
                    jssc,
                    LocationStrategies.PreferConsistent(),
                    ConsumerStrategies.Subscribe(topics, kafkaParams)
            );

            // 5. 处理数据流
            processStream(kafkaStream);

            // 6. 启动流处理
            jssc.start();
            logger.info("Spark 流处理已启动");
            
            jssc.awaitTermination();
        } catch (Exception e) {
            logger.error("Spark 流处理启动失败", e);
            shutdown();
        }
    }

    /**
     * Core logic for stream processing
     */
    private void processStream(JavaInputDStream<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>> kafkaStream) {
        // Step 1: Extract values and convert to SignalData
        JavaDStream<SignalData> signalStream = kafkaStream
                .map(record -> SignalData.fromJson(record.value()))
                .filter(signal -> signal != null && signal.getSamples() != null);

        // Step 2: Segment processing (segment within each batch)
        JavaDStream<List<SignalData>> segmentedStream = signalStream
                .window(Durations.milliseconds(batchInterval * 2))
                .glom()
                .map(array -> {
                    List<SignalData> list = new ArrayList<>();
                    for (SignalData data : array) {
                        list.add(data);
                    }
                    return list;
                });

        // Step 3: Anomaly detection
        JavaDStream<AnomalyResult> resultStream = segmentedStream
                .flatMap(signalList -> {
                    AnomalyDetector detector = new AnomalyDetector();
                    List<AnomalyResult> results = new ArrayList<>();
                    
                    for (SignalData signal : signalList) {
                        AnomalyResult result = detector.detect(signal);
                        results.add(result);
                    }
                    
                    return results.iterator();
                });

        // Step 4: Output results
        resultStream.foreachRDD(rdd -> {
            // Statistics
            long count = rdd.count();
            if (count > 0) {
                long anomalyCount = rdd.filter(AnomalyResult::isAnomaly).count();
                logger.info("Processing batch: total={}, anomalies={}, anomaly_rate={:.2f}%",
                        count, anomalyCount, (double) anomalyCount / count * 100);
            }
            
            // Capture variables for serialization
            final String bootstrapServers = kafkaBootstrapServers;
            final String outputTopic = kafkaOutputTopic;
            
            // Send to Kafka using foreachPartition for efficiency
            rdd.foreachPartition(partition -> {
                // Create Kafka producer per partition (not per record)
                Properties props = new Properties();
                props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                props.put(ProducerConfig.ACKS_CONFIG, "1");
                props.put(ProducerConfig.RETRIES_CONFIG, 3);
                
                KafkaProducer<String, String> producer = new KafkaProducer<>(props);
                
                try {
                    int sent = 0;
                    while (partition.hasNext()) {
                        AnomalyResult result = partition.next();
                        
                        // Send to Kafka
                        String key = "sensor-" + result.getSensorId();
                        String value = result.toJson();
                        ProducerRecord<String, String> record = new ProducerRecord<>(outputTopic, key, value);
                        producer.send(record);
                        sent++;
                        
                        // Local logging for anomalies
                        if (result.isAnomaly()) {
                            System.out.println("Anomaly detected: sensorId=" + result.getSensorId() + 
                                ", score=" + result.getAnomalyScore() + ", type=" + result.getAnomalyType());
                        }
                    }
                    producer.flush();
                    System.out.println("Sent " + sent + " results to Kafka topic: " + outputTopic);
                } catch (Exception e) {
                    System.err.println("Error sending to Kafka: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    producer.close();
                }
            });
        });

        // Step 5: Periodic statistics
        resultStream.window(Durations.seconds(10))
                .foreachRDD(rdd -> {
                    Map<String, Long> anomalyTypeCount = rdd
                            .filter(AnomalyResult::isAnomaly)
                            .mapToPair(r -> new scala.Tuple2<>(r.getAnomalyType(), 1L))
                            .reduceByKey((a, b) -> a + b)
                            .collectAsMap();
                    
                    if (!anomalyTypeCount.isEmpty()) {
                        logger.info("Anomaly type statistics: {}", anomalyTypeCount);
                    }
                });
    }

    public void shutdown() {
        if (jssc != null) {
            jssc.stop(true, true);
            logger.info("Spark streaming processor shutdown");
        }
    }

    public static void main(String[] args) {
        StreamProcessor processor = new StreamProcessor();
        processor.start();
    }
}
