"""
TDMS Kafka Producer - 高速版
支持200万样本/秒的处理速率
优化版本：适用于高速数据采集和实时处理
"""

import os
import sys
import time
import json
import argparse
from datetime import datetime

try:
    from nptdms import TdmsFile
    print("[OK] nptdms library installed")
except ImportError:
    print("[ERROR] nptdms library not installed")
    print("Please run: pip install npTDMS")
    sys.exit(1)

try:
    from kafka import KafkaProducer
    print("[OK] kafka-python library installed")
except ImportError:
    print("[ERROR] kafka-python library not installed")
    print("Please run: pip install kafka-python")
    sys.exit(1)

import numpy as np

# 高速配置 - 200万样本/秒 - 极速优化版
CONFIG_HIGH_SPEED = {
    'kafka_brokers': ['localhost:9092'],
    'kafka_topic': 'sample-input',
    'data_dir': os.path.join(os.path.dirname(__file__), 'floatdata', 'data'),
    'sample_rate': 2000000,  # 采样率 2MHz
    'samples_per_packet': 5000,  # 每包5000样本（提升吞吐量）
    'send_interval': 0,  # 0ms发送间隔（无延迟）
    'loop': True,
    'batch_size': 1048576,  # 1MB批次大小（极大提升）
    'compression': 'gzip',  # 数据压缩
    'linger_ms': 0  # 0ms批次延迟（立即发送）
}

# 标准配置 - 100万样本/秒
CONFIG_STANDARD = {
    'kafka_brokers': ['localhost:9092'],
    'kafka_topic': 'sample-input',
    'data_dir': os.path.join(os.path.dirname(__file__), 'floatdata', 'data'),
    'sample_rate': 1000000,  # 采样率 1MHz
    'samples_per_packet': 1000,  # 每包1000样本
    'send_interval': 0.001,  # 1ms
    'loop': True,
    'batch_size': 16384,
    'compression': 'gzip',
    'linger_ms': 10
}

class TdmsKafkaProducerHighSpeed:
    def __init__(self, config):
        self.config = config
        self.producer = None
        self.current_file_index = 0
        self.total_sent = 0
        self.total_samples = 0
        self.start_time = None
        self.last_report_time = None
        
    def connect_kafka(self):
        """连接Kafka服务器（优化配置）"""
        try:
            self.producer = KafkaProducer(
                bootstrap_servers=self.config['kafka_brokers'],
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                api_version=(0, 10, 1),  # 兼容旧版本Kafka
                compression_type=self.config.get('compression', 'gzip'),
                batch_size=self.config.get('batch_size', 1048576),  # 1MB批次，极大提升
                linger_ms=self.config.get('linger_ms', 0),  # 0延迟，立即发送
                buffer_memory=134217728,  # 128MB缓冲区，加倍
                max_request_size=10485760,  # 10MB最大请求
                max_block_ms=30000,  # 30秒超时
                request_timeout_ms=30000,  # 30秒请求超时
                acks=0  # 不等待确认，提升速度
            )
            print(f"[OK] Connected to Kafka: {self.config['kafka_brokers']}")
            print(f"  配置: 批次={self.config['batch_size']}, 压缩={self.config['compression']}, 延迟={self.config['linger_ms']}ms")
            return True
        except Exception as e:
            print(f"[ERROR] Kafka connection failed: {str(e)}")
            print("请确保Kafka正在运行: KAFKA-START.bat")
            return False
    
    def send_samples(self, device_id, samples, metadata=None):
        """发送样本数据到Kafka"""
        try:
            message = {
                'deviceId': device_id,
                'timestamp': int(time.time() * 1000),
                'sampleRate': self.config['sample_rate'],
                'samples': samples,
                'metadata': metadata or {}
            }
            
            # 异步发送，不等待响应
            self.producer.send(
                self.config['kafka_topic'],
                key=device_id,
                value=message
            )
            
            self.total_sent += 1
            self.total_samples += len(samples)
            return True
            
        except Exception as e:
            print(f"[ERROR] Send failed: {str(e)}")
            return False
    
    def print_statistics(self, force=False):
        """打印统计信息"""
        now = time.time()
        if self.last_report_time is None:
            self.last_report_time = now
            return
        
        elapsed = now - self.last_report_time
        if not force and elapsed < 5.0:  # 每5秒报告一次
            return
        
        total_elapsed = now - self.start_time
        samples_per_sec = self.total_samples / total_elapsed if total_elapsed > 0 else 0
        packets_per_sec = self.total_sent / total_elapsed if total_elapsed > 0 else 0
        
        print(f"\n[统计] 运行时间: {total_elapsed:.1f}秒")
        print(f"  总发送包数: {self.total_sent:,}")
        print(f"  总采样点数: {self.total_samples:,}")
        print(f"  吞吐量: {samples_per_sec:,.0f} 样本/秒 ({samples_per_sec/1000000:.2f} M样本/秒)")
        print(f"  数据包速率: {packets_per_sec:.0f} 包/秒")
        
        self.last_report_time = now
    
    def process_tdms_file(self, file_path):
        """处理单个TDMS文件"""
        try:
            print(f"\n处理文件: {os.path.basename(file_path)}")
            tdms_file = TdmsFile.read(file_path)
            
            for group in tdms_file.groups():
                print(f"  组 '{group.name}': {len(group.channels())} 个通道")
                
                for channel in group.channels():
                    data = channel[:]
                    total_samples = len(data)
                    print(f"    通道 '{channel.name}': {total_samples:,} 个采样点")
                    
                    # 分批发送
                    samples_per_packet = self.config['samples_per_packet']
                    send_interval = self.config['send_interval']
                    
                    for i in range(0, total_samples, samples_per_packet):
                        batch = data[i:i+samples_per_packet]
                        
                        device_id = f"tdms-{os.path.basename(file_path)}-{group.name}-{channel.name}"
                        
                        metadata = {
                            'file': os.path.basename(file_path),
                            'group': group.name,
                            'channel': channel.name,
                            'sample_index': i,
                            'total_samples': total_samples,
                            'batch_size': len(batch)
                        }
                        
                        self.send_samples(device_id, batch.tolist(), metadata)
                        
                        # 高速模式：完全移除延迟和阻塞，让Kafka批次机制自然控制速率
                        # 注释掉原有的sleep和flush来获得最大吞吐量
                        # if send_interval > 0:
                        #     time.sleep(send_interval)
                        
                        # 完全移除flush，让Kafka自动批次发送（极速模式）
                        # 只在文件处理完成后flush一次
                        
                        # 定期打印统计
                        self.print_statistics()
                    
                    print(f"      [OK] Completed: {channel.name}")
            
            # 文件处理完成后flush一次，确保数据发送
            if self.producer:
                self.producer.flush()
            
            return True
            
        except Exception as e:
            print(f"  [ERROR] Processing failed: {str(e)}")
            return False
    
    def run(self):
        """主运行循环"""
        if not self.connect_kafka():
            return
        
        # 获取TDMS文件列表
        data_dir = self.config['data_dir']
        if not os.path.exists(data_dir):
            print(f"[ERROR] Data directory not found: {data_dir}")
            return
        
        tdms_files = [f for f in os.listdir(data_dir) if f.endswith('.tdms')]
        if not tdms_files:
            print(f"[ERROR] No TDMS files found: {data_dir}")
            return
        
        tdms_files.sort()
        print(f"\n找到 {len(tdms_files)} 个TDMS文件")
        
        self.start_time = time.time()
        self.last_report_time = self.start_time
        
        try:
            while True:
                for file_name in tdms_files:
                    file_path = os.path.join(data_dir, file_name)
                    self.process_tdms_file(file_path)
                
                if not self.config['loop']:
                    break
                    
                print("\n循环播放...")
                
        except KeyboardInterrupt:
            print("\n\n用户中断")
        finally:
            self.print_statistics(force=True)
            if self.producer:
                self.producer.flush()  # 确保所有消息发送完成
                self.producer.close()
            print("\n[INFO] TDMS Kafka Producer 已停止")

def main():
    parser = argparse.ArgumentParser(description='TDMS Kafka Producer - 高速版')
    parser.add_argument('--mode', choices=['high', 'standard'], default='high',
                        help='运行模式: high=200万样本/秒, standard=100万样本/秒')
    parser.add_argument('--loop', action='store_true', help='循环播放')
    args = parser.parse_args()
    
    # 选择配置
    if args.mode == 'high':
        config = CONFIG_HIGH_SPEED.copy()
        print("\n[模式] 高速模式 - 200万样本/秒")
    else:
        config = CONFIG_STANDARD.copy()
        print("\n[模式] 标准模式 - 100万样本/秒")
    
    if args.loop:
        config['loop'] = True
    
    print("=" * 80)
    print("  TDMS Kafka Producer - 高速版")
    print("=" * 80)
    print(f"\n[配置]")
    print(f"  数据源: {config['data_dir']}")
    print(f"  Kafka主题: {config['kafka_topic']}")
    print(f"  Kafka Brokers: {config['kafka_brokers']}")
    print(f"  采样率: {config['sample_rate']:,} Hz ({config['sample_rate']/1000000:.1f} MHz)")
    print(f"  每包样本数: {config['samples_per_packet']:,}")
    print(f"  发送间隔: {config['send_interval']*1000:.2f} ms")
    if config['send_interval'] > 0:
        print(f"  理论吞吐量: {config['samples_per_packet']/config['send_interval']:,.0f} 样本/秒")
    else:
        print(f"  理论吞吐量: 无限制（取决于Kafka批次机制）")
    print(f"  循环播放: {'是' if config['loop'] else '否'}")
    print()
    
    producer = TdmsKafkaProducerHighSpeed(config)
    producer.run()

if __name__ == '__main__':
    main()
