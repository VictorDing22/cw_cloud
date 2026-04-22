"""
TDMS Kafka Producer
读取TDMS文件并通过Kafka发送给Backend服务
"""

import os
import sys
import time
import json
import argparse
from datetime import datetime

try:
    from nptdms import TdmsFile
    print("✓ nptdms 库已安装")
except ImportError:
    print("× nptdms 库未安装")
    print("请运行: pip install npTDMS")
    sys.exit(1)

try:
    from kafka import KafkaProducer
    print("✓ kafka-python 库已安装")
except ImportError:
    print("× kafka-python 库未安装")
    print("请运行: pip install kafka-python")
    sys.exit(1)

import numpy as np

# 配置
CONFIG = {
    'kafka_brokers': ['localhost:9092'],
    'kafka_topic': 'sample-input',  # Backend监听的Kafka主题
    'data_dir': os.path.join(os.path.dirname(__file__), 'floatdata', 'data'),
    'sample_rate': 1000000,  # 采样率 1MHz
    'samples_per_packet': 1000,  # 每个数据包的采样点数
    'send_interval': 0.001,  # 发送间隔（秒）0.001秒 = 1ms
    'loop': True  # 是否循环播放
}

class TdmsKafkaProducer:
    def __init__(self, config):
        self.config = config
        self.producer = None
        self.current_file_index = 0
        self.total_sent = 0
        self.start_time = None
        
    def connect_kafka(self):
        """连接到Kafka"""
        try:
            self.producer = KafkaProducer(
                bootstrap_servers=self.config['kafka_brokers'],
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                compression_type='gzip',
                batch_size=16384,
                linger_ms=10
            )
            print(f"✓ 已连接到Kafka: {self.config['kafka_brokers']}")
            return True
        except Exception as e:
            print(f"× Kafka连接失败: {str(e)}")
            print("请确保Kafka正在运行: KAFKA-START.bat")
            return False
    
    def get_tdms_files(self):
        """获取所有TDMS文件"""
        if not os.path.exists(self.config['data_dir']):
            print(f"× 数据目录不存在: {self.config['data_dir']}")
            return []
        
        files = []
        for file in os.listdir(self.config['data_dir']):
            if file.endswith('.tdms') and not file.endswith('.tdms_index'):
                files.append(os.path.join(self.config['data_dir'], file))
        
        return sorted(files)
    
    def send_samples(self, device_id, samples, metadata=None):
        """发送采样数据到Kafka"""
        try:
            message = {
                'deviceId': device_id,
                'timestamp': int(time.time() * 1000),
                'sampleRate': self.config['sample_rate'],
                'samples': samples,
                'metadata': metadata or {}
            }
            
            future = self.producer.send(
                self.config['kafka_topic'],
                key=device_id,
                value=message
            )
            
            # 异步发送，不等待确认（提高性能）
            # future.get(timeout=10)  # 如需确认，取消注释
            
            self.total_sent += 1
            return True
            
        except Exception as e:
            print(f"× 发送失败: {str(e)}")
            return False
    
    def process_tdms_file(self, file_path):
        """处理单个TDMS文件"""
        try:
            print(f"\n处理文件: {os.path.basename(file_path)}")
            
            # 读取TDMS文件
            tdms_file = TdmsFile.read(file_path)
            groups = tdms_file.groups()
            
            if not groups:
                print("  × 文件中没有数据组")
                return
            
            # 处理所有组和通道
            for group in groups:
                channels = group.channels()
                print(f"  组 '{group.name}': {len(channels)} 个通道")
                
                for channel in channels:
                    data = channel[:]
                    
                    if data is None or len(data) == 0:
                        print(f"    通道 '{channel.name}': 无数据")
                        continue
                    
                    total_samples = len(data)
                    print(f"    通道 '{channel.name}': {total_samples:,} 个采样点")
                    
                    # 构建设备ID
                    device_id = f"tdms-{os.path.splitext(os.path.basename(file_path))[0]}"
                    
                    # 分批发送
                    num_batches = (total_samples + self.config['samples_per_packet'] - 1) // self.config['samples_per_packet']
                    
                    for batch_idx in range(num_batches):
                        start_idx = batch_idx * self.config['samples_per_packet']
                        end_idx = min((batch_idx + 1) * self.config['samples_per_packet'], total_samples)
                        
                        batch_samples = data[start_idx:end_idx]
                        
                        # 转换为列表（如果是NumPy数组）
                        if isinstance(batch_samples, np.ndarray):
                            batch_samples = batch_samples.tolist()
                        
                        # 元数据
                        metadata = {
                            'source_file': os.path.basename(file_path),
                            'group': group.name,
                            'channel': channel.name,
                            'batch_index': batch_idx,
                            'total_batches': num_batches,
                            'start_index': start_idx,
                            'end_index': end_idx
                        }
                        
                        # 发送数据
                        if self.send_samples(device_id, batch_samples, metadata):
                            if (batch_idx + 1) % 100 == 0 or (batch_idx + 1) == num_batches:
                                elapsed = time.time() - self.start_time if self.start_time else 0
                                rate = self.total_sent / elapsed if elapsed > 0 else 0
                                print(f"      进度: {batch_idx + 1}/{num_batches} "
                                      f"({(batch_idx + 1) / num_batches * 100:.1f}%) "
                                      f"| 总发送: {self.total_sent:,} "
                                      f"| 速率: {rate:.1f} 包/秒")
                        
                        # 发送间隔
                        if self.config['send_interval'] > 0:
                            time.sleep(self.config['send_interval'])
            
            # 确保所有消息发送完成
            self.producer.flush()
            print(f"  ✓ 文件处理完成")
            
        except Exception as e:
            print(f"  × 处理失败: {str(e)}")
            import traceback
            traceback.print_exc()
    
    def run(self):
        """主运行循环"""
        if not self.connect_kafka():
            return
        
        files = self.get_tdms_files()
        
        if not files:
            print("× 未找到TDMS文件")
            return
        
        print(f"\n找到 {len(files)} 个TDMS文件:")
        for idx, file in enumerate(files[:10], 1):  # 只显示前10个
            size_mb = os.path.getsize(file) / 1024 / 1024
            print(f"  {idx}. {os.path.basename(file)} ({size_mb:.1f} MB)")
        
        if len(files) > 10:
            print(f"  ... 还有 {len(files) - 10} 个文件")
        
        print("\n" + "=" * 60)
        print("  开始发送数据到Kafka")
        print("=" * 60)
        print(f"Kafka主题: {self.config['kafka_topic']}")
        print(f"每包采样点数: {self.config['samples_per_packet']}")
        print(f"发送间隔: {self.config['send_interval']*1000:.1f}ms")
        print("按 Ctrl+C 停止")
        print("=" * 60)
        print()
        
        self.start_time = time.time()
        
        try:
            while True:
                for file_path in files:
                    self.process_tdms_file(file_path)
                
                if not self.config['loop']:
                    break
                
                print("\n所有文件处理完成，重新开始...")
                time.sleep(1)
                
        except KeyboardInterrupt:
            print("\n\n× 用户中断")
        finally:
            self.cleanup()
    
    def cleanup(self):
        """清理资源"""
        if self.producer:
            print("\n正在关闭Kafka生产者...")
            self.producer.flush()
            self.producer.close()
        
        if self.start_time:
            elapsed = time.time() - self.start_time
            rate = self.total_sent / elapsed if elapsed > 0 else 0
            print(f"\n统计信息:")
            print(f"  总发送: {self.total_sent:,} 个数据包")
            print(f"  运行时间: {elapsed:.1f} 秒")
            print(f"  平均速率: {rate:.1f} 包/秒")

def main():
    parser = argparse.ArgumentParser(description='TDMS文件到Kafka的数据生产者')
    parser.add_argument('--broker', default='localhost:9092', help='Kafka broker地址')
    parser.add_argument('--topic', default='sample-input', help='Kafka主题')
    parser.add_argument('--data-dir', help='TDMS文件目录')
    parser.add_argument('--samples', type=int, default=1000, help='每包采样点数')
    parser.add_argument('--interval', type=float, default=0.001, help='发送间隔（秒）')
    parser.add_argument('--no-loop', action='store_true', help='不循环播放')
    
    args = parser.parse_args()
    
    # 更新配置
    if args.broker:
        CONFIG['kafka_brokers'] = [args.broker]
    if args.topic:
        CONFIG['kafka_topic'] = args.topic
    if args.data_dir:
        CONFIG['data_dir'] = args.data_dir
    if args.samples:
        CONFIG['samples_per_packet'] = args.samples
    if args.interval:
        CONFIG['send_interval'] = args.interval
    if args.no_loop:
        CONFIG['loop'] = False
    
    print("=" * 60)
    print("  TDMS Kafka Producer")
    print("=" * 60)
    print()
    
    producer = TdmsKafkaProducer(CONFIG)
    producer.run()

if __name__ == '__main__':
    main()
