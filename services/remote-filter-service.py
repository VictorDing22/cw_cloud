"""
远程滤波服务 - 调用盛老师的滤波微服务 API
替代本地 LMS 滤波，使用真正的 Kalman/RLS/LS 算法

使用方法:
  python3 services/remote-filter-service.py --algorithm kalman
  python3 services/remote-filter-service.py --algorithm rls
  python3 services/remote-filter-service.py --algorithm ls
"""

import os
import sys
import json
import time
import signal
import argparse
import asyncio
import aiohttp
from typing import List, Dict, Any, Optional
from dataclasses import dataclass
from enum import Enum

# 检查依赖
try:
    import numpy as np
    from kafka import KafkaConsumer, KafkaProducer
    print("[OK] numpy, kafka-python")
except ImportError as e:
    print(f"[ERROR] 缺少依赖: {e}")
    print("安装: pip3 install numpy kafka-python aiohttp")
    sys.exit(1)


# ============== 配置 ==============

# 盛老师的滤波微服务地址
FILTER_SERVICES = {
    "kalman": "http://49.235.44.231:8000/kalman/audio/run",
    "rls": "http://49.235.44.231:8001/rls/audio/run",
    "ls": "http://49.235.44.231:8002/ls/audio/run",
}

CONFIG = {
    'kafka_brokers': os.getenv('KAFKA_BROKERS', 'localhost:9092'),
    'input_topic': os.getenv('INPUT_TOPIC', 'sample-input'),
    'output_topic': os.getenv('OUTPUT_TOPIC', 'sample-output'),
    'algorithm': os.getenv('FILTER_ALGORITHM', 'kalman'),
    'batch_size': int(os.getenv('BATCH_SIZE', '10')),
    'timeout': int(os.getenv('TIMEOUT', '30')),
}


# ============== 远程滤波客户端 ==============

class RemoteFilterClient:
    """异步远程滤波客户端"""
    
    def __init__(self, algorithm: str = "kalman"):
        self.algorithm = algorithm
        self.url = FILTER_SERVICES.get(algorithm)
        if not self.url:
            raise ValueError(f"未知算法: {algorithm}, 支持: {list(FILTER_SERVICES.keys())}")
        
        self.session: Optional[aiohttp.ClientSession] = None
        self.stats = {
            'requests': 0,
            'samples': 0,
            'errors': 0,
            'total_time_ms': 0,
        }
    
    async def init(self):
        timeout = aiohttp.ClientTimeout(total=CONFIG['timeout'])
        self.session = aiohttp.ClientSession(timeout=timeout)
        print(f"[OK] 远程滤波客户端初始化: {self.algorithm}")
        print(f"[OK] 服务地址: {self.url}")
    
    async def close(self):
        if self.session:
            await self.session.close()
    
    async def filter_signal(self, signal: List[float]) -> Dict[str, Any]:
        """调用远程滤波服务"""
        if not self.session:
            await self.init()
        
        start_time = time.perf_counter()
        
        try:
            # 构建请求体
            if self.algorithm == "kalman":
                payload = {
                    "signal": signal,
                    "model": "level",
                    "process_noise_var": 1e-3,
                    "measurement_noise_var": 1e-2,
                }
            elif self.algorithm == "rls":
                payload = {
                    "signal": signal,
                    "model": "level",
                    "forgetting_factor": 0.99,
                    "delta": 1000.0,
                }
            elif self.algorithm == "ls":
                payload = {
                    "signal": signal,
                    "model": "level",
                    "ridge_alpha": 0.0,
                }
            else:
                payload = {"signal": signal}
            
            async with self.session.post(self.url, json=payload) as resp:
                if resp.status != 200:
                    error_text = await resp.text()
                    raise Exception(f"HTTP {resp.status}: {error_text}")
                
                result = await resp.json()
                elapsed_ms = (time.perf_counter() - start_time) * 1000
                
                # 更新统计
                self.stats['requests'] += 1
                self.stats['samples'] += len(signal)
                self.stats['total_time_ms'] += elapsed_ms
                
                return {
                    'filtered_signal': result.get('filtered_signal', []),
                    'processing_time_ms': elapsed_ms,
                }
                
        except Exception as e:
            self.stats['errors'] += 1
            print(f"[ERROR] 滤波请求失败: {e}")
            # 返回原始信号作为降级
            return {
                'filtered_signal': signal,
                'processing_time_ms': 0,
                'error': str(e),
            }


# ============== Kafka 流处理服务 ==============

class RemoteFilterService:
    """远程滤波 Kafka 流处理服务"""
    
    def __init__(self, config: Dict):
        self.config = config
        self.consumer = None
        self.producer = None
        self.running = False
        self.filter_client = RemoteFilterClient(config['algorithm'])
        self.stats = {
            'received': 0,
            'sent': 0,
            'samples': 0,
            'start_time': None,
        }
    
    def connect_kafka(self):
        """连接 Kafka"""
        brokers = self.config['kafka_brokers'].split(',')
        
        self.consumer = KafkaConsumer(
            self.config['input_topic'],
            bootstrap_servers=brokers,
            group_id=f"remote-filter-{self.config['algorithm']}-{int(time.time())}",
            auto_offset_reset='latest',
            enable_auto_commit=True,
            value_deserializer=lambda m: json.loads(m.decode('utf-8')),
            max_poll_records=self.config['batch_size'],
        )
        print(f"[OK] Kafka Consumer: {self.config['input_topic']}")
        
        self.producer = KafkaProducer(
            bootstrap_servers=brokers,
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            compression_type='gzip',
            batch_size=16384,
            linger_ms=5,
            acks=1,
        )
        print(f"[OK] Kafka Producer: {self.config['output_topic']}")
    
    async def process_message(self, data: Dict) -> Optional[Dict]:
        """处理单条消息"""
        samples = data.get('samples', [])
        if not samples:
            return None
        
        # 调用远程滤波服务
        result = await self.filter_client.filter_signal(samples)
        filtered = result['filtered_signal']
        
        # 计算 SNR
        original_arr = np.array(samples)
        filtered_arr = np.array(filtered)
        
        if len(filtered_arr) > 0:
            noise = original_arr[:len(filtered_arr)] - filtered_arr
            signal_power = np.mean(filtered_arr ** 2)
            noise_power = np.mean(noise ** 2)
            snr_after = 10 * np.log10(signal_power / max(noise_power, 1e-10))
            snr_after = float(np.clip(snr_after, -20, 60))
        else:
            snr_after = 0
        
        # 估计原始 SNR
        if len(original_arr) > 50:
            window = min(50, len(original_arr) // 10)
            smoothed = np.convolve(original_arr, np.ones(window)/window, mode='same')
            orig_noise = original_arr - smoothed
            orig_signal_power = np.mean(smoothed ** 2)
            orig_noise_power = np.mean(orig_noise ** 2)
            snr_before = 10 * np.log10(orig_signal_power / max(orig_noise_power, 1e-10))
            snr_before = float(np.clip(snr_before, -20, 40))
        else:
            snr_before = 10.0
        
        # 构建输出
        output = {
            'type': 'signal-data',
            'deviceId': data.get('deviceId', 'unknown'),
            'timestamp': data.get('timestamp', int(time.time() * 1000)),
            'processedAt': int(time.time() * 1000),
            'sampleRate': data.get('sampleRate', 50000),
            'location': data.get('metadata', {}).get('file', 'Remote-Filter'),
            'originalSamples': samples[:500],
            'filteredSamples': filtered[:500],
            'residuals': (original_arr[:100] - filtered_arr[:100]).tolist() if len(filtered_arr) >= 100 else [],
            'sampleCount': len(samples),
            'snrBefore': round(snr_before, 2),
            'snrAfter': round(snr_after, 2),
            'snrImprovement': round(snr_after - snr_before, 2),
            'processingTimeMs': round(result['processing_time_ms'], 2),
            'filterType': self.config['algorithm'].upper(),
            'statistics': {
                'min': float(np.min(original_arr)),
                'max': float(np.max(original_arr)),
                'avg': float(np.mean(original_arr)),
                'rms': float(np.sqrt(np.mean(original_arr ** 2))),
            },
            'mode': 'remote-api',
        }
        
        return output
    
    def send_output(self, output: Dict) -> bool:
        """发送到 Kafka"""
        try:
            future = self.producer.send(
                self.config['output_topic'],
                key=output['deviceId'].encode('utf-8'),
                value=output
            )
            future.get(timeout=10)
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
        client_stats = self.filter_client.stats
        avg_time = client_stats['total_time_ms'] / max(client_stats['requests'], 1)
        
        print(f"\r[{self.config['algorithm'].upper()}] "
              f"收:{self.stats['received']:,} "
              f"发:{self.stats['sent']:,} "
              f"样本:{self.stats['samples']:,} "
              f"| {rate/1000:.1f}K/s "
              f"| API延迟:{avg_time:.0f}ms "
              f"| 错误:{client_stats['errors']}    ", end='', flush=True)
    
    async def run(self):
        """运行服务"""
        self.connect_kafka()
        await self.filter_client.init()
        
        self.running = True
        self.stats['start_time'] = time.time()
        
        print("\n" + "=" * 60)
        print(f"  远程滤波服务 - {self.config['algorithm'].upper()}")
        print("=" * 60)
        print(f"  输入: {self.config['input_topic']}")
        print(f"  输出: {self.config['output_topic']}")
        print(f"  算法: {self.config['algorithm'].upper()}")
        print(f"  服务: {FILTER_SERVICES[self.config['algorithm']]}")
        print("=" * 60)
        print("\n按 Ctrl+C 停止\n")
        
        loop = asyncio.get_event_loop()
        last_print = time.time()
        
        try:
            while self.running:
                # 在线程池中执行阻塞的 poll
                messages = await loop.run_in_executor(
                    None,
                    lambda: self.consumer.poll(timeout_ms=1000, max_records=self.config['batch_size'])
                )
                
                if not messages:
                    if time.time() - last_print > 2:
                        self.print_stats()
                        last_print = time.time()
                    continue
                
                for tp, records in messages.items():
                    for msg in records:
                        self.stats['received'] += 1
                        
                        output = await self.process_message(msg.value)
                        
                        if output and self.send_output(output):
                            self.stats['sent'] += 1
                            self.stats['samples'] += output['sampleCount']
                
                if time.time() - last_print > 0.5:
                    self.print_stats()
                    last_print = time.time()
                    
        except asyncio.CancelledError:
            pass
        finally:
            await self.shutdown()
    
    async def shutdown(self):
        """关闭服务"""
        self.running = False
        
        print("\n\n" + "=" * 60)
        print("  服务统计")
        print("=" * 60)
        if self.stats['start_time']:
            elapsed = time.time() - self.stats['start_time']
            print(f"  运行时间: {elapsed:.1f}s")
            print(f"  接收消息: {self.stats['received']:,}")
            print(f"  发送消息: {self.stats['sent']:,}")
            print(f"  处理样本: {self.stats['samples']:,}")
            if elapsed > 0:
                print(f"  平均速率: {self.stats['samples']/elapsed/1000:.1f} K/s")
            print(f"  API 调用: {self.filter_client.stats['requests']:,}")
            print(f"  API 错误: {self.filter_client.stats['errors']}")
        print("=" * 60)
        
        await self.filter_client.close()
        
        if self.producer:
            self.producer.flush()
            self.producer.close()
        if self.consumer:
            self.consumer.close()
        
        print("[OK] 服务已关闭")


# ============== 主入口 ==============

def main():
    parser = argparse.ArgumentParser(description='远程滤波服务 - 调用盛老师的滤波微服务')
    parser.add_argument('--brokers', default=CONFIG['kafka_brokers'], help='Kafka brokers')
    parser.add_argument('--input-topic', default=CONFIG['input_topic'], help='输入主题')
    parser.add_argument('--output-topic', default=CONFIG['output_topic'], help='输出主题')
    parser.add_argument('--algorithm', '-a', default=CONFIG['algorithm'],
                        choices=['kalman', 'rls', 'ls'], help='滤波算法')
    parser.add_argument('--batch-size', type=int, default=CONFIG['batch_size'], help='批量大小')
    args = parser.parse_args()
    
    config = {
        'kafka_brokers': args.brokers,
        'input_topic': args.input_topic,
        'output_topic': args.output_topic,
        'algorithm': args.algorithm,
        'batch_size': args.batch_size,
    }
    
    print("=" * 60)
    print("  远程滤波服务")
    print("  调用盛老师的 Kalman/RLS/LS 微服务")
    print("=" * 60)
    
    service = RemoteFilterService(config)
    
    def sig_handler(sig, frame):
        print("\n[收到停止信号]")
        service.running = False
    
    signal.signal(signal.SIGINT, sig_handler)
    signal.signal(signal.SIGTERM, sig_handler)
    
    try:
        asyncio.run(service.run())
    except KeyboardInterrupt:
        pass


if __name__ == '__main__':
    main()
