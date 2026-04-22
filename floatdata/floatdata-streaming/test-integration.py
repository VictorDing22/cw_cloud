#!/usr/bin/env python3
"""
集成测试脚本 - 验证整个系统的功能
"""

import json
import time
import socket
import struct
import numpy as np
from kafka import KafkaConsumer, KafkaProducer
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class AcousticEmissionTester:
    def __init__(self, netty_host='localhost', netty_port=9090,
                 kafka_bootstrap='localhost:9092'):
        self.netty_host = netty_host
        self.netty_port = netty_port
        self.kafka_bootstrap = kafka_bootstrap
        self.socket = None
        
    def connect_to_netty(self):
        """连接到 Netty 服务器"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((self.netty_host, self.netty_port))
            logger.info(f"已连接到 Netty 服务器: {self.netty_host}:{self.netty_port}")
            return True
        except Exception as e:
            logger.error(f"连接失败: {e}")
            return False
    
    def generate_signal(self, length=4096, sensor_id=1):
        """生成模拟信号"""
        base_freq = 50000 + sensor_id * 10000
        t = np.arange(length) / 1000000.0
        
        # 多频率信号
        signal = np.sin(2 * np.pi * base_freq * t)
        signal += 0.3 * np.sin(2 * np.pi * base_freq * 2 * t)
        signal += 0.1 * np.sin(2 * np.pi * base_freq * 3 * t)
        signal += np.random.normal(0, 0.1, length)
        
        return signal.astype(np.float32)
    
    def send_signal(self, sensor_id=1, location='test'):
        """发送信号数据到 Netty 服务器"""
        try:
            timestamp = int(time.time() * 1000)
            sample_rate = 1000000
            samples = self.generate_signal(sensor_id=sensor_id)
            
            # 编码数据
            location_bytes = location.encode('utf-8')
            data = struct.pack('>q', timestamp)  # 时间戳
            data += struct.pack('>i', sensor_id)  # 传感器ID
            data += struct.pack('>i', sample_rate)  # 采样率
            data += struct.pack('>h', len(location_bytes))  # 位置长度
            data += location_bytes  # 位置
            data += struct.pack('>i', len(samples))  # 采样数长度
            
            for sample in samples:
                data += struct.pack('>f', sample)
            
            self.socket.sendall(data)
            logger.info(f"已发送信号数据: sensorId={sensor_id}, samples={len(samples)}")
            return True
        except Exception as e:
            logger.error(f"发送失败: {e}")
            return False
    
    def verify_kafka_messages(self, topic='acoustic-emission-signal', timeout=10):
        """验证 Kafka 消息"""
        try:
            consumer = KafkaConsumer(
                topic,
                bootstrap_servers=self.kafka_bootstrap,
                auto_offset_reset='latest',
                value_deserializer=lambda m: json.loads(m.decode('utf-8')),
                consumer_timeout_ms=timeout * 1000
            )
            
            messages = []
            for message in consumer:
                messages.append(message.value)
                logger.info(f"收到 Kafka 消息: {message.value}")
            
            consumer.close()
            return len(messages) > 0
        except Exception as e:
            logger.error(f"Kafka 验证失败: {e}")
            return False
    
    def test_end_to_end(self):
        """端到端测试"""
        logger.info("=" * 50)
        logger.info("开始端到端测试")
        logger.info("=" * 50)
        
        # 1. 连接到 Netty
        if not self.connect_to_netty():
            logger.error("无法连接到 Netty 服务器")
            return False
        
        # 2. 发送多个信号
        logger.info("发送测试信号...")
        for i in range(5):
            if not self.send_signal(sensor_id=i+1, location=f'sensor-{i+1}'):
                logger.error(f"发送信号 {i+1} 失败")
                return False
            time.sleep(0.5)
        
        # 3. 验证 Kafka 消息
        logger.info("验证 Kafka 消息...")
        time.sleep(2)  # 等待消息处理
        if not self.verify_kafka_messages():
            logger.error("未收到 Kafka 消息")
            return False
        
        logger.info("=" * 50)
        logger.info("端到端测试成功!")
        logger.info("=" * 50)
        return True
    
    def close(self):
        """关闭连接"""
        if self.socket:
            self.socket.close()
            logger.info("已关闭 Netty 连接")

def main():
    tester = AcousticEmissionTester()
    try:
        success = tester.test_end_to_end()
        exit(0 if success else 1)
    finally:
        tester.close()

if __name__ == '__main__':
    main()
