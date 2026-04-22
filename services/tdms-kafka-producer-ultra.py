"""
TDMS Kafka Producer - 超高速版 (2M+ 样本/秒)
优化策略：
1. 增大每包样本数到20000
2. 使用多线程并行发送
3. 预加载数据到内存
4. 移除所有阻塞操作
"""

import os
import sys
import time
import json
import argparse
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor
import threading

try:
    from nptdms import TdmsFile
    print("[OK] nptdms")
except ImportError:
    print("[ERROR] pip install npTDMS")
    sys.exit(1)

try:
    from kafka import KafkaProducer
    print("[OK] kafka-python")
except ImportError:
    print("[ERROR] pip install kafka-python")
    sys.exit(1)

import numpy as np

# 超高速配置 - 针对8核30GB服务器优化，目标2M+样本/秒
CONFIG_ULTRA = {
    'kafka_brokers': ['127.0.0.1:9092'],
    'kafka_topic': 'sample-input',
    'data_dir': os.path.join(os.path.dirname(__file__), 'floatdata', 'data'),
    'sample_rate': 2000000,
    'samples_per_packet': 10000,  # 10000样本/包
    'send_interval': 0,
    'loop': True,
    'batch_size': 1048576,  # 1MB批次
    'compression': 'lz4',  # lz4压缩（需要pip3 install lz4）
    'linger_ms': 5,  # 5ms批次延迟
    'num_threads': 4,  # 4个发送线程
    'preload': True
}

class UltraFastProducer:
    def __init__(self, config):
        self.config = config
        self.producer = None
        self.total_sent = 0
        self.total_samples = 0
        self.start_time = None
        self.lock = threading.Lock()
        self.preloaded_data = []
        
    def connect_kafka(self):
        try:
            compression = self.config.get('compression', 'lz4')
            if compression == 'none':
                compression = None
            
            self.producer = KafkaProducer(
                bootstrap_servers=self.config['kafka_brokers'],
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                compression_type=compression,
                batch_size=self.config.get('batch_size', 1048576),
                linger_ms=self.config.get('linger_ms', 5),
                buffer_memory=268435456,  # 256MB缓冲
                max_request_size=10485760,  # 10MB最大请求
                acks=0,  # 不等待确认
                max_in_flight_requests_per_connection=10,
                retries=0,
                request_timeout_ms=30000
            )
            print(f"[OK] Kafka: {self.config['kafka_brokers']}")
            print(f"  压缩: {compression or '无'}, 批次: {self.config['batch_size']/1024/1024:.1f}MB")
            return True
        except Exception as e:
            print(f"[ERROR] Kafka: {e}")
            return False
    
    def preload_all_data(self):
        """预加载所有TDMS数据到内存"""
        print("\n[预加载] 加载TDMS数据到内存...")
        data_dir = self.config['data_dir']
        tdms_files = sorted([f for f in os.listdir(data_dir) if f.endswith('.tdms')])
        
        for file_name in tdms_files:
            file_path = os.path.join(data_dir, file_name)
            try:
                tdms_file = TdmsFile.read(file_path)
                for group in tdms_file.groups():
                    for channel in group.channels():
                        data = channel[:].astype(np.float32)  # 使用float32节省内存
                        self.preloaded_data.append({
                            'file': file_name,
                            'group': group.name,
                            'channel': channel.name,
                            'data': data
                        })
                        print(f"  加载: {file_name}/{channel.name} ({len(data):,} 样本)")
            except Exception as e:
                print(f"  [ERROR] {file_name}: {e}")
        
        total = sum(len(d['data']) for d in self.preloaded_data)
        print(f"[预加载完成] {len(self.preloaded_data)} 个通道, {total:,} 总样本")
    
    def send_batch_async(self, device_id, samples, metadata):
        """异步发送单个批次（不等待确认）"""
        try:
            message = {
                'deviceId': device_id,
                'timestamp': int(time.time() * 1000),
                'sampleRate': self.config['sample_rate'],
                'samples': samples,
                'metadata': metadata
            }
            # 异步发送，不阻塞
            self.producer.send(self.config['kafka_topic'], key=device_id, value=message)
            return True
        except Exception as e:
            return False
    
    def send_batch(self, device_id, samples, metadata):
        """发送单个批次（兼容旧接口）"""
        return self.send_batch_async(device_id, samples, metadata)
    
    def process_channel_fast(self, channel_data):
        """快速处理单个通道"""
        data = channel_data['data']
        device_id = f"tdms-{channel_data['file']}-{channel_data['group']}-{channel_data['channel']}"
        samples_per_packet = self.config['samples_per_packet']
        total = len(data)
        
        # 批量发送，减少锁竞争
        local_sent = 0
        local_samples = 0
        
        for i in range(0, total, samples_per_packet):
            batch = data[i:i+samples_per_packet].tolist()
            metadata = {
                'file': channel_data['file'],
                'channel': channel_data['channel'],
                'index': i,
                'total': total
            }
            if self.send_batch_async(device_id, batch, metadata):
                local_sent += 1
                local_samples += len(batch)
        
        # 批量更新统计
        with self.lock:
            self.total_sent += local_sent
            self.total_samples += local_samples
    
    def print_stats(self, force=False):
        """打印统计 - 每秒更新"""
        if self.start_time is None:
            return
        now = time.time()
        elapsed = now - self.start_time
        if elapsed < 0.5:
            return
        
        # 每秒打印一次
        if not force and hasattr(self, '_last_print') and (now - self._last_print) < 1.0:
            return
        self._last_print = now
        
        rate = self.total_samples / elapsed
        pps = self.total_sent / elapsed
        print(f"\r[{elapsed:.0f}s] {self.total_samples:,} 样本 | {rate/1000000:.2f} M/s | {pps:.0f} pkt/s    ", end='', flush=True)
    
    def run(self):
        if not self.connect_kafka():
            return
        
        # 预加载数据
        if self.config.get('preload', True):
            self.preload_all_data()
        
        if not self.preloaded_data:
            print("[ERROR] 没有数据")
            return
        
        self.start_time = time.time()
        print("\n[开始发送] Ctrl+C 停止\n")
        
        try:
            with ThreadPoolExecutor(max_workers=self.config['num_threads']) as executor:
                while True:
                    # 并行处理所有通道
                    futures = [executor.submit(self.process_channel_fast, ch) for ch in self.preloaded_data]
                    
                    # 等待完成并打印统计
                    for f in futures:
                        f.result()
                        self.print_stats()
                    
                    if not self.config['loop']:
                        break
                    
                    # 刷新缓冲
                    self.producer.flush()
                    
        except KeyboardInterrupt:
            print("\n\n[停止]")
        finally:
            elapsed = time.time() - self.start_time
            rate = self.total_samples / elapsed if elapsed > 0 else 0
            print(f"\n\n[最终统计]")
            print(f"  运行时间: {elapsed:.1f}s")
            print(f"  总样本: {self.total_samples:,}")
            print(f"  平均速率: {rate/1000000:.2f} M样本/秒")
            if self.producer:
                self.producer.flush()
                self.producer.close()

def main():
    parser = argparse.ArgumentParser(description='TDMS Kafka Producer - 超高速版')
    parser.add_argument('--threads', type=int, default=6, help='发送线程数(默认6)')
    parser.add_argument('--packet-size', type=int, default=50000, help='每包样本数(默认50000)')
    parser.add_argument('--no-compress', action='store_true', help='禁用压缩')
    args = parser.parse_args()
    
    config = CONFIG_ULTRA.copy()
    config['num_threads'] = args.threads
    config['samples_per_packet'] = args.packet_size
    if args.no_compress:
        config['compression'] = 'none'
    
    print("=" * 60)
    print("  TDMS Kafka Producer - 超高速版")
    print("  目标: 2M+ 样本/秒 (针对8核30GB服务器优化)")
    print("=" * 60)
    print(f"  线程数: {config['num_threads']}")
    print(f"  每包样本: {config['samples_per_packet']:,}")
    print(f"  压缩: {config['compression']}")
    print(f"  批次大小: {config['batch_size']/1024/1024:.0f}MB")
    print(f"  缓冲区: 512MB")
    print()
    
    producer = UltraFastProducer(config)
    producer.run()

if __name__ == '__main__':
    main()
