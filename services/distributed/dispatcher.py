"""
分布式滤波 - 数据分发器
功能: 从 sample-input 读取数据，按序号分发到不同 partition
"""

import os
import sys
import json
import time
import signal
import argparse
import threading
from datetime import datetime

# 检查依赖
try:
    from kafka import KafkaConsumer, KafkaProducer
    from kafka.admin import KafkaAdminClient, NewTopic
    from kafka.errors import TopicAlreadyExistsError
except ImportError:
    print("[ERROR] 缺少 kafka-python")
    print("安装: pip3 install kafka-python")
    sys.exit(1)

# 配置
CONFIG = {
    'kafka_brokers': os.getenv('KAFKA_BROKERS', 'localhost:9092'),
    'input_topic': os.getenv('INPUT_TOPIC', 'sample-input'),
    'output_topic': os.getenv('OUTPUT_TOPIC', 'distributed-raw'),
    'num_partitions': int(os.getenv('NUM_PARTITIONS', '4')),
    'consumer_group': 'dispatcher-service',
}


class Dispatcher:
    """数据分发器 - 给数据包分配序号并分发到不同partition"""
    
    def __init__(self, config):
        self.config = config
        self.consumer = None
        self.producer = None
        self.running = False
        self.sequence_id = 0
        self.sequence_lock = threading.Lock()
        self.stats = {
            'received': 0,
            'dispatched': 0,
            'errors': 0,
            'start_time': None,
        }
    
    def ensure_topic(self):
        """确保输出topic存在且有足够的partition"""
        try:
            admin = KafkaAdminClient(
                bootstrap_servers=self.config['kafka_brokers'].split(',')
            )
            topic = NewTopic(
                name=self.config['output_topic'],
                num_partitions=self.config['num_partitions'],
                replication_factor=1
            )
            admin.create_topics([topic])
            print(f"[OK] 创建topic: {self.config['output_topic']} ({self.config['num_partitions']} partitions)")
        except TopicAlreadyExistsError:
            print(f"[OK] Topic已存在: {self.config['output_topic']}")
        except Exception as e:
            print(f"[WARN] Topic操作: {e}")
    
    def connect(self):
        """连接Kafka"""
        brokers = self.config['kafka_brokers'].split(',')
        
        try:
            self.consumer = KafkaConsumer(
                self.config['input_topic'],
                bootstrap_servers=brokers,
                group_id=self.config['consumer_group'] + '-' + str(int(time.time())),
                auto_offset_reset='latest',
                enable_auto_commit=True,
                value_deserializer=lambda m: json.loads(m.decode('utf-8')),
                max_poll_records=100,
            )
            print(f"[OK] Consumer连接: {self.config['input_topic']}")
            
            self.producer = KafkaProducer(
                bootstrap_servers=brokers,
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                compression_type='gzip',
                batch_size=16384,
                linger_ms=5,
                acks=1,
            )
            print(f"[OK] Producer连接: {self.config['output_topic']}")
            
            return True
        except Exception as e:
            print(f"[ERROR] Kafka连接失败: {e}")
            return False
    
    def get_next_sequence(self):
        """获取下一个序号"""
        with self.sequence_lock:
            seq = self.sequence_id
            self.sequence_id += 1
            return seq
    
    def dispatch(self, data):
        """分发数据包"""
        seq_id = self.get_next_sequence()
        partition = seq_id % self.config['num_partitions']
        
        # 添加分布式处理元数据
        enriched_data = {
            **data,
            'sequence_id': seq_id,
            'partition_id': partition,
            'dispatch_time': int(time.time() * 1000),
            'num_partitions': self.config['num_partitions'],
        }
        
        try:
            self.producer.send(
                self.config['output_topic'],
                value=enriched_data,
                partition=partition
            )
            return True
        except Exception as e:
            print(f"[ERROR] 分发失败: {e}")
            return False
    
    def print_stats(self):
        """打印统计"""
        if self.stats['start_time'] is None:
            return
        elapsed = time.time() - self.stats['start_time']
        if elapsed < 0.1:
            return
        rate = self.stats['dispatched'] / elapsed
        print(f"\r[Dispatcher] 收:{self.stats['received']:,} "
              f"发:{self.stats['dispatched']:,} "
              f"| {rate:.0f}/s "
              f"| 序号:{self.sequence_id}    ", end='', flush=True)
    
    def run(self):
        """运行分发器"""
        self.ensure_topic()
        
        if not self.connect():
            return
        
        self.running = True
        self.stats['start_time'] = time.time()
        
        print("\n" + "=" * 60)
        print("  分布式滤波 - 数据分发器")
        print("=" * 60)
        print(f"  输入: {self.config['input_topic']}")
        print(f"  输出: {self.config['output_topic']} ({self.config['num_partitions']} partitions)")
        print("=" * 60)
        print("\n按 Ctrl+C 停止\n")
        
        last_print = time.time()
        
        try:
            while self.running:
                messages = self.consumer.poll(timeout_ms=1000, max_records=100)
                
                if not messages:
                    if time.time() - last_print > 2:
                        self.print_stats()
                        last_print = time.time()
                    continue
                
                for tp, records in messages.items():
                    for msg in records:
                        self.stats['received'] += 1
                        
                        if self.dispatch(msg.value):
                            self.stats['dispatched'] += 1
                        else:
                            self.stats['errors'] += 1
                
                if time.time() - last_print > 0.5:
                    self.print_stats()
                    last_print = time.time()
                    
        except KeyboardInterrupt:
            print("\n\n[停止]")
        finally:
            self.shutdown()
    
    def shutdown(self):
        """关闭"""
        self.running = False
        print("\n" + "=" * 60)
        print("  Dispatcher 统计")
        print("=" * 60)
        if self.stats['start_time']:
            elapsed = time.time() - self.stats['start_time']
            print(f"  运行时间: {elapsed:.1f}s")
            print(f"  接收: {self.stats['received']:,}")
            print(f"  分发: {self.stats['dispatched']:,}")
            print(f"  错误: {self.stats['errors']}")
            if elapsed > 0:
                print(f"  速率: {self.stats['dispatched']/elapsed:.0f}/s")
        print("=" * 60)
        
        if self.producer:
            self.producer.flush()
            self.producer.close()
        if self.consumer:
            self.consumer.close()


def main():
    parser = argparse.ArgumentParser(description='分布式滤波 - 数据分发器')
    parser.add_argument('--brokers', default=CONFIG['kafka_brokers'])
    parser.add_argument('--input', default=CONFIG['input_topic'])
    parser.add_argument('--output', default=CONFIG['output_topic'])
    parser.add_argument('--partitions', type=int, default=CONFIG['num_partitions'])
    args = parser.parse_args()
    
    config = {
        'kafka_brokers': args.brokers,
        'input_topic': args.input,
        'output_topic': args.output,
        'num_partitions': args.partitions,
        'consumer_group': CONFIG['consumer_group'],
    }
    
    def sig_handler(sig, frame):
        sys.exit(0)
    signal.signal(signal.SIGINT, sig_handler)
    signal.signal(signal.SIGTERM, sig_handler)
    
    dispatcher = Dispatcher(config)
    dispatcher.run()


if __name__ == '__main__':
    main()
