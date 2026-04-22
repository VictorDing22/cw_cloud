"""
TDMS Signal Producer - 支持 Signal-1 和 Signal-2 数据源
通过 HTTP API 控制数据源切换，数据发送到 Kafka
目标速率：2M 样本/秒
"""

import os
import sys
import time
import json
import threading
from pathlib import Path
from http.server import HTTPServer, BaseHTTPRequestHandler
from concurrent.futures import ThreadPoolExecutor

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

# 配置
CONFIG = {
    'kafka_brokers': ['127.0.0.1:9092'],
    'kafka_topic': 'sample-input',
    'http_port': 3003,
    'sample_rate': 2000000,
    'samples_per_packet': 20000,
    'send_interval_ms': 10,  # 10ms间隔，20000样本/10ms = 2M/s
}

# 项目根目录
PROJECT_ROOT = Path(__file__).parent.parent
SIGNAL_1_PATH = PROJECT_ROOT / 'floatdata' / 'signal-1' / 'ae_sim_2s.tdms'
SIGNAL_2_DIR = PROJECT_ROOT / 'floatdata' / 'signal-2'


class SignalProducer:
    def __init__(self):
        self.producer = None
        self.running = False
        self.current_source = None
        self.data = None
        self.data_index = 0
        self.lock = threading.Lock()
        self.stats = {
            'total_samples': 0,
            'total_packets': 0,
            'start_time': None,
            'current_rate': 0
        }
    
    def connect_kafka(self):
        try:
            self.producer = KafkaProducer(
                bootstrap_servers=CONFIG['kafka_brokers'],
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                batch_size=16384,
                linger_ms=0,
                acks=1,
                batch_size=1048576,
                linger_ms=5,
                buffer_memory=268435456,
                acks=1,  # 等待 leader 确认
                max_request_size=10485760,  # 10MB
                compression_type='gzip',  # 压缩大消息
            )
            print(f"[OK] Kafka连接成功: {CONFIG['kafka_brokers']}")
            return True
        except Exception as e:
            print(f"[ERROR] Kafka连接失败: {e}")
            return False
    
    def load_signal1(self):
        """加载 Signal-1 数据"""
        print(f"[INFO] 加载 Signal-1: {SIGNAL_1_PATH}")
        try:
            tdms = TdmsFile.read(str(SIGNAL_1_PATH))
            for group in tdms.groups():
                for channel in group.channels():
                    name = channel.name.lower()
                    if 'plus' in name or 'noise' in name:
                        data = np.array(channel[:], dtype=np.float32)
                        print(f"[OK] 加载通道 '{channel.name}': {len(data)} 样本")
                        return {
                            'name': 'Signal-1',
                            'noisy': data,
                            'sample_rate': 100000,
                            'total_samples': len(data)
                        }
            # 如果没找到加噪信号，用第一个通道
            for group in tdms.groups():
                for channel in group.channels():
                    data = np.array(channel[:], dtype=np.float32)
                    print(f"[OK] 加载通道 '{channel.name}': {len(data)} 样本")
                    return {
                        'name': 'Signal-1',
                        'noisy': data,
                        'sample_rate': 100000,
                        'total_samples': len(data)
                    }
        except Exception as e:
            print(f"[ERROR] 加载 Signal-1 失败: {e}")
        return None
    
    def load_signal2(self):
        """加载 Signal-2 数据"""
        print(f"[INFO] 加载 Signal-2: {SIGNAL_2_DIR}")
        try:
            mix_file = SIGNAL_2_DIR / 'ae_mix_2s.tdms'
            tdms = TdmsFile.read(str(mix_file))
            for group in tdms.groups():
                for channel in group.channels():
                    data = np.array(channel[:], dtype=np.float32)
                    print(f"[OK] 加载混合信号: {len(data)} 样本")
                    return {
                        'name': 'Signal-2',
                        'noisy': data,
                        'sample_rate': 100000,
                        'total_samples': len(data)
                    }
        except Exception as e:
            print(f"[ERROR] 加载 Signal-2 失败: {e}")
        return None
    
    def switch_source(self, source):
        """切换数据源"""
        with self.lock:
            if source == 'signal-1':
                self.data = self.load_signal1()
            elif source == 'signal-2':
                self.data = self.load_signal2()
            else:
                print(f"[ERROR] 未知数据源: {source}")
                return False
            
            if self.data:
                self.current_source = source
                self.data_index = 0
                self.stats = {
                    'total_samples': 0,
                    'total_packets': 0,
                    'start_time': time.time(),
                    'current_rate': 0
                }
                print(f"[OK] 切换到数据源: {source}")
                return True
            return False
    
    def start(self, source='signal-1'):
        """启动数据发送"""
        if not self.connect_kafka():
            return False
        
        if not self.switch_source(source):
            return False
        
        self.running = True
        self.stats['start_time'] = time.time()
        
        # 启动发送线程
        thread = threading.Thread(target=self._send_loop, daemon=True)
        thread.start()
        print(f"[OK] 数据发送已启动，目标速率: 2M/s")
        return True
    
    def stop(self):
        """停止数据发送"""
        self.running = False
        print("[OK] 数据发送已停止")
    
    def _send_loop(self):
        """发送循环"""
        while self.running:
            if not self.data:
                time.sleep(0.1)
                continue
            
            with self.lock:
                noisy = self.data['noisy']
                total = len(noisy)
                chunk_size = CONFIG['samples_per_packet']
                
                # 获取当前块
                start_idx = self.data_index
                end_idx = min(start_idx + chunk_size, total)
                samples = noisy[start_idx:end_idx].tolist()
                
                # 更新索引，循环播放
                self.data_index = end_idx if end_idx < total else 0
                
                # 更新统计
                self.stats['total_samples'] += len(samples)
                self.stats['total_packets'] += 1
                elapsed = time.time() - self.stats['start_time']
                if elapsed > 0:
                    self.stats['current_rate'] = self.stats['total_samples'] / elapsed
            
            # 发送到 Kafka
            message = {
                'deviceId': f'tdms-{self.current_source}',
                'timestamp': int(time.time() * 1000),
                'sampleRate': CONFIG['sample_rate'],
                'samples': samples,
                'metadata': {
                    'source': self.current_source,
                    'index': start_idx,
                    'total': total
                }
            }
            
            try:
                future = self.producer.send(CONFIG['kafka_topic'], 
                                   key=self.current_source, 
                                   value=message)
                # 确保消息发送成功
                future.get(timeout=10)
            except Exception as e:
                print(f"[ERROR] 发送失败: {e}")
            
            # 控制发送速率
            time.sleep(CONFIG['send_interval_ms'] / 1000)
    
    def get_status(self):
        """获取状态"""
        return {
            'running': self.running,
            'source': self.current_source,
            'stats': self.stats
        }


# 全局 producer 实例
producer = SignalProducer()


class APIHandler(BaseHTTPRequestHandler):
    """HTTP API 处理器"""
    
    def do_GET(self):
        if self.path == '/status':
            self.send_json(producer.get_status())
        elif self.path == '/health':
            self.send_json({'status': 'ok'})
        else:
            self.send_error(404)
    
    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length).decode('utf-8')
        
        try:
            data = json.loads(body) if body else {}
        except:
            data = {}
        
        if self.path == '/start':
            source = data.get('source', 'signal-1')
            if producer.running:
                producer.switch_source(source)
                self.send_json({'success': True, 'message': f'切换到 {source}'})
            else:
                producer.start(source)
                self.send_json({'success': True, 'message': f'启动 {source}'})
        elif self.path == '/stop':
            producer.stop()
            self.send_json({'success': True, 'message': '已停止'})
        elif self.path == '/switch':
            source = data.get('source', 'signal-1')
            if producer.switch_source(source):
                self.send_json({'success': True, 'message': f'切换到 {source}'})
            else:
                self.send_json({'success': False, 'message': '切换失败'})
        else:
            self.send_error(404)
    
    def send_json(self, data):
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(json.dumps(data).encode('utf-8'))
    
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
    
    def log_message(self, format, *args):
        pass  # 禁用日志


def main():
    print("=" * 60)
    print("  TDMS Signal Producer")
    print("  支持 Signal-1 和 Signal-2 数据源")
    print("  目标速率: 2M 样本/秒")
    print("=" * 60)
    print(f"  HTTP API: http://localhost:{CONFIG['http_port']}")
    print("  POST /start  - 启动 (body: {source: 'signal-1'})")
    print("  POST /stop   - 停止")
    print("  POST /switch - 切换数据源")
    print("  GET  /status - 获取状态")
    print("=" * 60)
    
    # 启动 HTTP 服务器
    server = HTTPServer(('0.0.0.0', CONFIG['http_port']), APIHandler)
    print(f"\n[OK] HTTP API 服务器启动在端口 {CONFIG['http_port']}")
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[停止]")
        producer.stop()


if __name__ == '__main__':
    main()
