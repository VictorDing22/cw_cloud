"""
分布式滤波 - 聚合重排服务
功能: 从多个Worker收集结果，按sequence_id重排，推送到WebSocket
"""

import os
import sys
import json
import time
import signal
import argparse
import threading
import heapq
from collections import deque
from datetime import datetime

# 检查依赖
try:
    from kafka import KafkaConsumer, KafkaProducer
    import asyncio
    import websockets
except ImportError as e:
    print(f"[ERROR] 缺少依赖: {e}")
    print("安装: pip3 install kafka-python websockets")
    sys.exit(1)


# 配置
CONFIG = {
    'kafka_brokers': os.getenv('KAFKA_BROKERS', 'localhost:9092'),
    'input_topic': os.getenv('INPUT_TOPIC', 'distributed-filtered'),
    'output_topic': os.getenv('OUTPUT_TOPIC', 'sample-output-distributed'),
    'websocket_port': int(os.getenv('WEBSOCKET_PORT', '8082')),
    'num_partitions': int(os.getenv('NUM_PARTITIONS', '4')),
    'max_wait_ms': int(os.getenv('MAX_WAIT_MS', '100')),  # 最大等待时间
    'buffer_size': int(os.getenv('BUFFER_SIZE', '1000')),  # 重排缓冲区大小
}


class SequenceBuffer:
    """序号重排缓冲区"""
    
    def __init__(self, max_size=1000, max_wait_ms=100):
        self.max_size = max_size
        self.max_wait_ms = max_wait_ms
        self.buffer = {}  # sequence_id -> data
        self.next_expected = 0
        self.lock = threading.Lock()
        self.last_output_time = time.time()
        self.stats = {
            'received': 0,
            'output': 0,
            'skipped': 0,
            'out_of_order': 0,
        }
    
    def add(self, data):
        """添加数据到缓冲区"""
        seq_id = data.get('sequence_id', 0)
        
        with self.lock:
            self.stats['received'] += 1
            
            if seq_id < self.next_expected:
                # 过期数据，丢弃
                self.stats['skipped'] += 1
                return []
            
            if seq_id != self.next_expected:
                self.stats['out_of_order'] += 1
            
            self.buffer[seq_id] = data
            
            # 尝试输出连续的数据
            return self._flush()
    
    def _flush(self):
        """输出连续的数据"""
        output = []
        now = time.time()
        wait_time = (now - self.last_output_time) * 1000
        
        while True:
            if self.next_expected in self.buffer:
                # 有下一个期望的数据
                output.append(self.buffer.pop(self.next_expected))
                self.next_expected += 1
                self.stats['output'] += 1
                self.last_output_time = now
            elif wait_time > self.max_wait_ms and self.buffer:
                # 超时，跳过缺失的数据
                min_seq = min(self.buffer.keys())
                if min_seq > self.next_expected:
                    skipped = min_seq - self.next_expected
                    self.stats['skipped'] += skipped
                    self.next_expected = min_seq
                    # 继续尝试输出
                    continue
                break
            else:
                break
        
        # 防止缓冲区过大
        if len(self.buffer) > self.max_size:
            # 强制输出最旧的数据
            while len(self.buffer) > self.max_size // 2:
                if self.buffer:
                    min_seq = min(self.buffer.keys())
                    output.append(self.buffer.pop(min_seq))
                    self.stats['output'] += 1
                    if min_seq >= self.next_expected:
                        self.next_expected = min_seq + 1
                else:
                    break
        
        return output
    
    def force_flush(self):
        """强制输出所有缓冲数据"""
        with self.lock:
            output = []
            for seq_id in sorted(self.buffer.keys()):
                output.append(self.buffer[seq_id])
                self.stats['output'] += 1
            self.buffer.clear()
            return output


class Aggregator:
    """聚合重排服务"""
    
    def __init__(self, config):
        self.config = config
        self.consumer = None
        self.producer = None
        self.running = False
        self.sequence_buffer = SequenceBuffer(
            max_size=config['buffer_size'],
            max_wait_ms=config['max_wait_ms']
        )
        self.ws_clients = set()
        self.ws_lock = threading.Lock()
        self.stats = {
            'kafka_sent': 0,
            'ws_sent': 0,
            'start_time': None,
        }
    
    def connect_kafka(self):
        """连接Kafka"""
        brokers = self.config['kafka_brokers'].split(',')
        
        try:
            self.consumer = KafkaConsumer(
                self.config['input_topic'],
                bootstrap_servers=brokers,
                group_id=f"aggregator-{int(time.time())}",
                auto_offset_reset='latest',
                enable_auto_commit=True,
                value_deserializer=lambda m: json.loads(m.decode('utf-8')),
                max_poll_records=100,
            )
            print(f"[OK] Consumer: {self.config['input_topic']}")
            
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
    
    def send_to_kafka(self, data):
        """发送到Kafka"""
        try:
            # 添加聚合元数据
            data['aggregated'] = True
            data['aggregatedAt'] = int(time.time() * 1000)
            
            self.producer.send(
                self.config['output_topic'],
                value=data
            )
            self.stats['kafka_sent'] += 1
            return True
        except Exception as e:
            print(f"[ERROR] Kafka发送失败: {e}")
            return False
    
    async def send_to_websocket(self, data):
        """发送到所有WebSocket客户端"""
        if not self.ws_clients:
            return
        
        message = json.dumps(data)
        dead_clients = set()
        
        with self.ws_lock:
            for client in self.ws_clients:
                try:
                    await client.send(message)
                    self.stats['ws_sent'] += 1
                except:
                    dead_clients.add(client)
            
            # 移除断开的客户端
            self.ws_clients -= dead_clients
    
    async def ws_handler(self, websocket):
        """WebSocket连接处理"""
        # 获取路径（兼容新旧版本websockets）
        path = getattr(websocket, 'path', '/distributed')
        print(f"[WS] 新连接: {websocket.remote_address}, 路径: {path}")
        
        # 只接受 /distributed 路径
        if path not in ['/distributed', '/']:
            print(f"[WS] 拒绝无效路径: {path}")
            await websocket.close(1008, "Invalid path")
            return
        
        with self.ws_lock:
            self.ws_clients.add(websocket)
        
        try:
            # 发送欢迎消息
            await websocket.send(json.dumps({
                'type': 'welcome',
                'message': '已连接到分布式滤波聚合服务',
                'partitions': self.config['num_partitions'],
            }))
            
            # 保持连接，处理ping/pong
            async for message in websocket:
                # 处理客户端消息（如果需要）
                try:
                    data = json.loads(message)
                    if data.get('type') == 'ping':
                        await websocket.send(json.dumps({'type': 'pong'}))
                except:
                    pass
                
        except websockets.exceptions.ConnectionClosed as e:
            print(f"[WS] 连接关闭: {e.code} {e.reason}")
        except Exception as e:
            print(f"[WS] 错误: {e}")
        finally:
            with self.ws_lock:
                self.ws_clients.discard(websocket)
            print(f"[WS] 断开: {websocket.remote_address}")
    
    def print_stats(self):
        """打印统计"""
        if self.stats['start_time'] is None:
            return
        elapsed = time.time() - self.stats['start_time']
        if elapsed < 0.1:
            return
        
        buf_stats = self.sequence_buffer.stats
        rate = buf_stats['output'] / elapsed
        
        print(f"\r[Aggregator] "
              f"收:{buf_stats['received']:,} "
              f"出:{buf_stats['output']:,} "
              f"跳:{buf_stats['skipped']} "
              f"乱序:{buf_stats['out_of_order']} "
              f"缓冲:{len(self.sequence_buffer.buffer)} "
              f"| {rate:.0f}/s "
              f"| WS客户端:{len(self.ws_clients)}    ", end='', flush=True)
    
    def _poll_kafka(self):
        """在线程中执行Kafka poll（阻塞操作）"""
        try:
            return self.consumer.poll(timeout_ms=50, max_records=50)
        except Exception as e:
            print(f"[ERROR] Kafka poll: {e}")
            return {}
    
    async def kafka_consumer_loop(self):
        """Kafka消费循环（非阻塞）"""
        last_print = time.time()
        loop = asyncio.get_event_loop()
        
        while self.running:
            # 在线程池中执行阻塞的poll操作
            messages = await loop.run_in_executor(None, self._poll_kafka)
            
            if not messages:
                # 检查超时数据
                output = self.sequence_buffer._flush()
                for data in output:
                    self.send_to_kafka(data)
                    await self.send_to_websocket(data)
                
                if time.time() - last_print > 2:
                    self.print_stats()
                    last_print = time.time()
                await asyncio.sleep(0.01)
                continue
            
            for tp, records in messages.items():
                for msg in records:
                    # 添加到重排缓冲区
                    output = self.sequence_buffer.add(msg.value)
                    
                    # 输出排序后的数据
                    for data in output:
                        self.send_to_kafka(data)
                        await self.send_to_websocket(data)
            
            if time.time() - last_print > 0.5:
                self.print_stats()
                last_print = time.time()
            
            # 让出控制权给其他协程
            await asyncio.sleep(0)
    
    async def run_async(self):
        """异步运行"""
        if not self.connect_kafka():
            return
        
        self.running = True
        self.stats['start_time'] = time.time()
        
        print("\n" + "=" * 60)
        print("  分布式滤波 - 聚合重排服务")
        print("=" * 60)
        print(f"  输入: {self.config['input_topic']}")
        print(f"  输出: {self.config['output_topic']}")
        print(f"  WebSocket: ws://0.0.0.0:{self.config['websocket_port']}/distributed")
        print(f"  分区数: {self.config['num_partitions']}")
        print(f"  最大等待: {self.config['max_wait_ms']}ms")
        print("=" * 60)
        print("\n按 Ctrl+C 停止\n")
        
        # 启动WebSocket服务器
        ws_server = await websockets.serve(
            self.ws_handler,
            "0.0.0.0",
            self.config['websocket_port'],
            ping_interval=30,
            ping_timeout=10,
        )
        print(f"[OK] WebSocket服务器启动: 端口 {self.config['websocket_port']}")
        
        try:
            await self.kafka_consumer_loop()
        except asyncio.CancelledError:
            pass
        finally:
            ws_server.close()
            await ws_server.wait_closed()
            self.shutdown()
    
    def run(self):
        """运行聚合服务"""
        try:
            asyncio.run(self.run_async())
        except KeyboardInterrupt:
            print("\n\n[停止]")
    
    def shutdown(self):
        """关闭"""
        self.running = False
        
        # 输出剩余缓冲数据
        remaining = self.sequence_buffer.force_flush()
        for data in remaining:
            self.send_to_kafka(data)
        
        print("\n" + "=" * 60)
        print("  Aggregator 统计")
        print("=" * 60)
        if self.stats['start_time']:
            elapsed = time.time() - self.stats['start_time']
            buf_stats = self.sequence_buffer.stats
            print(f"  运行时间: {elapsed:.1f}s")
            print(f"  接收: {buf_stats['received']:,}")
            print(f"  输出: {buf_stats['output']:,}")
            print(f"  跳过: {buf_stats['skipped']}")
            print(f"  乱序: {buf_stats['out_of_order']}")
            print(f"  Kafka发送: {self.stats['kafka_sent']:,}")
            print(f"  WS发送: {self.stats['ws_sent']:,}")
            if elapsed > 0:
                print(f"  速率: {buf_stats['output']/elapsed:.0f}/s")
        print("=" * 60)
        
        if self.producer:
            self.producer.flush()
            self.producer.close()
        if self.consumer:
            self.consumer.close()


def main():
    parser = argparse.ArgumentParser(description='分布式滤波 - 聚合重排服务')
    parser.add_argument('--brokers', default=CONFIG['kafka_brokers'])
    parser.add_argument('--input', default=CONFIG['input_topic'])
    parser.add_argument('--output', default=CONFIG['output_topic'])
    parser.add_argument('--ws-port', type=int, default=CONFIG['websocket_port'])
    parser.add_argument('--partitions', type=int, default=CONFIG['num_partitions'])
    parser.add_argument('--max-wait', type=int, default=CONFIG['max_wait_ms'])
    parser.add_argument('--buffer-size', type=int, default=CONFIG['buffer_size'])
    args = parser.parse_args()
    
    config = {
        'kafka_brokers': args.brokers,
        'input_topic': args.input,
        'output_topic': args.output,
        'websocket_port': args.ws_port,
        'num_partitions': args.partitions,
        'max_wait_ms': args.max_wait,
        'buffer_size': args.buffer_size,
    }
    
    def sig_handler(sig, frame):
        sys.exit(0)
    signal.signal(signal.SIGINT, sig_handler)
    signal.signal(signal.SIGTERM, sig_handler)
    
    aggregator = Aggregator(config)
    aggregator.run()


if __name__ == '__main__':
    main()
