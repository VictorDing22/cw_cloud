"""
分布式滤波 - 滤波工作节点
功能: 从指定partition消费数据，调用盛老师的滤波API，输出到聚合topic
"""

import os
import sys
import json
import time
import signal
import argparse
import threading
import asyncio
from datetime import datetime

# 检查依赖
try:
    import numpy as np
    from kafka import KafkaConsumer, KafkaProducer
    from kafka.structs import TopicPartition
    import aiohttp
except ImportError as e:
    print(f"[ERROR] 缺少依赖: {e}")
    print("安装: pip3 install numpy kafka-python aiohttp")
    sys.exit(1)


# ============== 盛老师滤波服务配置 ==============

FILTER_SERVICES = {
    "kalman": os.getenv("KALMAN_SERVICE_URL", "http://49.235.44.231:8000/kalman/audio/run"),
    "rls": os.getenv("RLS_SERVICE_URL", "http://49.235.44.231:8001/rls/audio/run"),
    "ls": os.getenv("LS_SERVICE_URL", "http://49.235.44.231:8002/ls/audio/run"),
}


# ============== 远程滤波客户端 ==============

class RemoteFilterClient:
    """调用盛老师的滤波微服务"""
    
    def __init__(self, algorithm: str = "kalman"):
        self.algorithm = algorithm
        self.url = FILTER_SERVICES.get(algorithm, FILTER_SERVICES["kalman"])
        self.session = None
        self.stats = {
            'requests': 0,
            'errors': 0,
            'total_time_ms': 0,
        }
    
    async def init(self):
        timeout = aiohttp.ClientTimeout(total=30)
        self.session = aiohttp.ClientSession(timeout=timeout)
        print(f"[OK] 远程滤波客户端: {self.algorithm} -> {self.url}")
    
    async def close(self):
        if self.session:
            await self.session.close()
    
    async def filter_signal(self, signal_data: list) -> dict:
        """调用远程滤波服务"""
        if not self.session:
            await self.init()
        
        start_time = time.perf_counter()
        
        try:
            # 构建请求体
            if self.algorithm == "kalman":
                payload = {
                    "signal": signal_data,
                    "model": "level",
                    "process_noise_var": 1e-3,
                    "measurement_noise_var": 1e-2,
                }
            elif self.algorithm == "rls":
                payload = {
                    "signal": signal_data,
                    "model": "level",
                    "forgetting_factor": 0.99,
                    "delta": 1000.0,
                }
            elif self.algorithm == "ls":
                payload = {
                    "signal": signal_data,
                    "model": "level",
                    "ridge_alpha": 0.0,
                }
            else:
                payload = {"signal": signal_data}
            
            async with self.session.post(self.url, json=payload) as resp:
                if resp.status != 200:
                    raise Exception(f"HTTP {resp.status}")
                
                result = await resp.json()
                elapsed_ms = (time.perf_counter() - start_time) * 1000
                
                self.stats['requests'] += 1
                self.stats['total_time_ms'] += elapsed_ms
                
                return {
                    'filtered_signal': result.get('filtered_signal', []),
                    'processing_time_ms': elapsed_ms,
                }
                
        except Exception as e:
            self.stats['errors'] += 1
            print(f"[ERROR] 滤波API调用失败: {e}")
            # 降级：返回原始信号
            return {
                'filtered_signal': signal_data,
                'processing_time_ms': 0,
                'error': str(e),
            }


def calculate_snr(original, filtered):
    """计算信噪比"""
    if len(original) == 0 or len(filtered) == 0:
        return 0.0, 0.0
    
    original = np.array(original)
    filtered = np.array(filtered)
    min_len = min(len(original), len(filtered))
    original = original[:min_len]
    filtered = filtered[:min_len]
    
    noise = original - filtered
    signal_power = np.mean(filtered ** 2)
    noise_power = np.mean(noise ** 2)
    
    # 估计原始SNR
    window = min(50, len(original) // 10)
    if window >= 3:
        smoothed = np.convolve(original, np.ones(window)/window, mode='same')
        orig_noise = original - smoothed
        orig_signal_power = np.mean(smoothed ** 2)
        orig_noise_power = np.mean(orig_noise ** 2)
        snr_before = 10 * np.log10(orig_signal_power / max(orig_noise_power, 1e-10))
    else:
        snr_before = 10.0
    
    snr_after = 10 * np.log10(signal_power / max(noise_power, 1e-10))
    
    return float(np.clip(snr_before, -20, 40)), float(np.clip(snr_after, -20, 60))


# ============== 配置 ==============

CONFIG = {
    'kafka_brokers': os.getenv('KAFKA_BROKERS', 'localhost:9092'),
    'input_topic': os.getenv('INPUT_TOPIC', 'distributed-raw'),
    'output_topic': os.getenv('OUTPUT_TOPIC', 'distributed-filtered'),
    'partition_id': int(os.getenv('PARTITION_ID', '0')),
    'worker_id': os.getenv('WORKER_ID', 'filter-worker-0'),
    'algorithm': os.getenv('FILTER_ALGORITHM', 'kalman'),
}


class FilterWorker:
    """滤波工作节点 - 调用远程滤波API"""
    
    def __init__(self, config):
        self.config = config
        self.consumer = None
        self.producer = None
        self.running = False
        self.filter_client = RemoteFilterClient(config['algorithm'])
        self.stats = {
            'received': 0,
            'processed': 0,
            'samples': 0,
            'errors': 0,
            'start_time': None,
        }
    
    def connect(self):
        """连接Kafka"""
        brokers = self.config['kafka_brokers'].split(',')
        partition = self.config['partition_id']
        
        try:
            self.consumer = KafkaConsumer(
                bootstrap_servers=brokers,
                group_id=f"filter-worker-{partition}-{int(time.time())}",
                auto_offset_reset='latest',
                enable_auto_commit=True,
                value_deserializer=lambda m: json.loads(m.decode('utf-8')),
                max_poll_records=50,
            )
            
            tp = TopicPartition(self.config['input_topic'], partition)
            self.consumer.assign([tp])
            print(f"[OK] Consumer: {self.config['input_topic']} partition {partition}")
            
            self.producer = KafkaProducer(
                bootstrap_servers=brokers,
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                compression_type='gzip',
                batch_size=16384,
                linger_ms=5,
                acks=1,
            )
            print(f"[OK] Producer: {self.config['output_topic']}")
            
            return True
        except Exception as e:
            print(f"[ERROR] Kafka连接失败: {e}")
            return False
    
    async def process(self, data):
        """处理数据包 - 调用远程滤波API"""
        try:
            device_id = data.get('deviceId', 'unknown')
            samples = data.get('samples', [])
            sequence_id = data.get('sequence_id', 0)
            partition_id = data.get('partition_id', 0)
            sample_rate = data.get('sampleRate', 50000)
            
            if not samples:
                return None
            
            # 调用盛老师的滤波API
            result = await self.filter_client.filter_signal(samples)
            filtered = result['filtered_signal']
            
            # 计算SNR
            snr_before, snr_after = calculate_snr(samples, filtered)
            
            # 构建输出 - 确保包含 originalSamples
            output = {
                'type': 'signal-data',
                'sequence_id': sequence_id,
                'partition_id': partition_id,
                'worker_id': self.config['worker_id'],
                'deviceId': device_id,
                'timestamp': data.get('timestamp', int(time.time() * 1000)),
                'processedAt': int(time.time() * 1000),
                'sampleRate': sample_rate,
                # 关键：同时包含原始信号和滤波后信号
                'originalSamples': samples[:500],
                'filteredSamples': filtered[:500] if filtered else samples[:500],
                'original_samples': samples[:500],  # 兼容下划线命名
                'filtered_samples': filtered[:500] if filtered else samples[:500],
                'residuals': (np.array(samples[:100]) - np.array(filtered[:100])).tolist() if len(filtered) >= 100 else [],
                'sampleCount': len(samples),
                'snrBefore': round(snr_before, 2),
                'snrAfter': round(snr_after, 2),
                'snrImprovement': round(snr_after - snr_before, 2),
                'processingTimeMs': round(result['processing_time_ms'], 2),
                'filterType': self.config['algorithm'].upper(),
                'statistics': {
                    'min': float(np.min(samples)),
                    'max': float(np.max(samples)),
                    'avg': float(np.mean(samples)),
                    'rms': float(np.sqrt(np.mean(np.array(samples) ** 2))),
                },
                'distributed': True,
                'remoteApi': True,
            }
            
            return output
            
        except Exception as e:
            print(f"[ERROR] 处理失败: {e}")
            return None
    
    def send(self, output):
        """发送处理结果"""
        try:
            self.producer.send(
                self.config['output_topic'],
                value=output
            )
            return True
        except Exception as e:
            print(f"[ERROR] 发送失败: {e}")
            return False
    
    def print_stats(self):
        """打印统计"""
        if self.stats['start_time'] is None:
            return
        elapsed = time.time() - self.stats['start_time']
        if elapsed < 0.1:
            return
        rate = self.stats['samples'] / elapsed
        api_stats = self.filter_client.stats
        avg_api_time = api_stats['total_time_ms'] / max(api_stats['requests'], 1)
        print(f"\r[{self.config['worker_id']}] "
              f"收:{self.stats['received']:,} "
              f"处理:{self.stats['processed']:,} "
              f"样本:{self.stats['samples']:,} "
              f"| {rate/1000:.1f}K/s "
              f"| API:{avg_api_time:.0f}ms "
              f"| 错误:{api_stats['errors']}    ", end='', flush=True)
    
    async def run_async(self):
        """异步运行Worker"""
        if not self.connect():
            return
        
        await self.filter_client.init()
        
        self.running = True
        self.stats['start_time'] = time.time()
        
        print("\n" + "=" * 60)
        print(f"  分布式滤波 - Worker [{self.config['worker_id']}]")
        print("=" * 60)
        print(f"  Partition: {self.config['partition_id']}")
        print(f"  算法: {self.config['algorithm'].upper()} (远程API)")
        print(f"  API: {self.filter_client.url}")
        print(f"  输入: {self.config['input_topic']}")
        print(f"  输出: {self.config['output_topic']}")
        print("=" * 60)
        print("\n按 Ctrl+C 停止\n")
        
        last_print = time.time()
        loop = asyncio.get_event_loop()
        
        try:
            while self.running:
                # 在线程池中执行阻塞的poll
                messages = await loop.run_in_executor(
                    None,
                    lambda: self.consumer.poll(timeout_ms=1000, max_records=10)
                )
                
                if not messages:
                    if time.time() - last_print > 2:
                        self.print_stats()
                        last_print = time.time()
                    continue
                
                for tp, records in messages.items():
                    for msg in records:
                        self.stats['received'] += 1
                        
                        output = await self.process(msg.value)
                        
                        if output and self.send(output):
                            self.stats['processed'] += 1
                            self.stats['samples'] += output['sampleCount']
                        else:
                            self.stats['errors'] += 1
                
                if time.time() - last_print > 0.5:
                    self.print_stats()
                    last_print = time.time()
                    
        except asyncio.CancelledError:
            pass
        finally:
            await self.shutdown()
    
    def run(self):
        """运行Worker"""
        try:
            asyncio.run(self.run_async())
        except KeyboardInterrupt:
            print("\n\n[停止]")
    
    async def shutdown(self):
        """关闭"""
        self.running = False
        
        await self.filter_client.close()
        
        print("\n" + "=" * 60)
        print(f"  Worker [{self.config['worker_id']}] 统计")
        print("=" * 60)
        if self.stats['start_time']:
            elapsed = time.time() - self.stats['start_time']
            print(f"  运行时间: {elapsed:.1f}s")
            print(f"  接收: {self.stats['received']:,}")
            print(f"  处理: {self.stats['processed']:,}")
            print(f"  样本: {self.stats['samples']:,}")
            print(f"  错误: {self.stats['errors']}")
            print(f"  API调用: {self.filter_client.stats['requests']:,}")
            print(f"  API错误: {self.filter_client.stats['errors']}")
            if elapsed > 0:
                print(f"  速率: {self.stats['samples']/elapsed/1000:.1f}K/s")
        print("=" * 60)
        
        if self.producer:
            self.producer.flush()
            self.producer.close()
        if self.consumer:
            self.consumer.close()


def main():
    parser = argparse.ArgumentParser(description='分布式滤波 - Worker节点 (远程API)')
    parser.add_argument('--brokers', default=CONFIG['kafka_brokers'])
    parser.add_argument('--input', default=CONFIG['input_topic'])
    parser.add_argument('--output', default=CONFIG['output_topic'])
    parser.add_argument('--partition', type=int, default=CONFIG['partition_id'])
    parser.add_argument('--worker-id', default=CONFIG['worker_id'])
    parser.add_argument('--algorithm', default=CONFIG['algorithm'],
                        choices=['kalman', 'rls', 'ls'])
    args = parser.parse_args()
    
    config = {
        'kafka_brokers': args.brokers,
        'input_topic': args.input,
        'output_topic': args.output,
        'partition_id': args.partition,
        'worker_id': args.worker_id or f'filter-worker-{args.partition}',
        'algorithm': args.algorithm,
    }
    
    def sig_handler(sig, frame):
        sys.exit(0)
    signal.signal(signal.SIGINT, sig_handler)
    signal.signal(signal.SIGTERM, sig_handler)
    
    worker = FilterWorker(config)
    worker.run()


if __name__ == '__main__':
    main()
