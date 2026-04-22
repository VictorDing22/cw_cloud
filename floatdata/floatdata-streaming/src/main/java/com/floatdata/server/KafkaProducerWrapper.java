package com.floatdata.server;

import com.floatdata.utils.ConfigLoader;
import com.floatdata.utils.SignalData;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Kafka 生产者包装类 - 将信号数据发送到 Kafka
 */
public class KafkaProducerWrapper {
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerWrapper.class);
    private final KafkaProducer<String, String> producer;
    private final String topic;

    public KafkaProducerWrapper() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                ConfigLoader.getString("kafka.bootstrap.servers", "localhost:9092"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.BATCH_SIZE_CONFIG,
                ConfigLoader.getInt("kafka.producer.batch.size", 16384));
        props.put(ProducerConfig.LINGER_MS_CONFIG,
                ConfigLoader.getLong("kafka.producer.linger.ms", 10));
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        this.producer = new KafkaProducer<>(props);
        this.topic = ConfigLoader.getString("kafka.topic.signal", "acoustic-emission-signal");
        
        logger.info("Kafka 生产者已初始化, 主题: {}", topic);
    }

    public void send(SignalData signalData) {
        try {
            String key = "sensor-" + signalData.getSensorId();
            String value = signalData.toJson();
            
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("发送消息失败", exception);
                }
            });
        } catch (Exception e) {
            logger.error("Kafka 发送异常", e);
        }
    }

    public void close() {
        if (producer != null) {
            producer.close();
            logger.info("Kafka 生产者已关闭");
        }
    }
}
