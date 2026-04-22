"""
WebSocket 桥接服务 - 从 Kafka 读取滤波结果，推送到前端
专用于分布式滤波监控
"""

import os
import json
import time
import asyncio
import threading
from typing import Set

from kafka import KafkaConsumer
import websockets
from websockets.server import WebSocketServerProtocol


# 配置
KAFKA_BROKERS = os.getenv("KAFKA_BROKERS", "localhost:9092")
INPUT_TOPIC = os.getenv("INPUT_TOPIC", "distributed-filtered")
WS_PORT = int(os.getenv("WS_PORT", "8083"))


class WebSocketBridge:
    """WebSocket 桥接服务"""
    
    def __init__(self):
        self.clients: Set[WebSocketServerProtocol] = set()
        self.consumer = None
        self.running = False
        self.stats = {
            "messages_received": 0,
            "messages_sent": 0,
            "clients_connected": 0,
        }
    
    def connect_kafka(self):
        """连接 Kafka"""
        brokers = KAFKA_BROKERS.split(",")
        
        self.consumer = KafkaConsumer(
            INPUT_TOPIC,
            bootstrap_servers=brokers,
            group_id=f"websocket-bridge-{int(time.time())}",
            auto_offset_reset="latest",
            value_deserializer=lambda m: json.loads(m.decode("utf-8")),
            max_poll_records=50,
        )
        
        print(f"[OK] Kafka 连接成功: {INPUT_TOPIC}")
    
    async def register(self, websocket: WebSocketServerProtocol):
        """注册 WebSocket 客户端"""
        self.clients.add(websocket)
        self.stats["clients_connected"] = len(self.clients)
        print(f"[WS] 新连接: {websocket.remote_address}, 总客户端: {len(self.clients)}")
        
        # 发送欢迎消息
        await websocket.send(json.dumps({
            "type": "welcome",
            "message": "已连接到滤波数据流",
            "timestamp": int(time.time() * 1000),
        }))
    
    async def unregister(self, websocket: WebSocketServerProtocol):
        """注销 WebSocket 客户端"""
        self.clients.discard(websocket)
        self.stats["clients_connected"] = len(self.clients)
        print(f"[WS] 断开: {websocket.remote_address}, 剩余客户端: {len(self.clients)}")
    
    async def broadcast(self, message: str):
        """广播消息给所有客户端"""
        if not self.clients:
            return
        
        dead_clients = set()
        
        for client in self.clients:
            try:
                await client.send(message)
                self.stats["messages_sent"] += 1
            except websockets.exceptions.ConnectionClosed:
                dead_clients.add(client)
            except Exception as e:
                print(f"[WS] 发送失败: {e}")
                dead_clients.add(client)
        
        # 移除断开的客户端
        for client in dead_clients:
            await self.unregister(client)
    
    async def ws_handler(self, websocket: WebSocketServerProtocol):
        """WebSocket 连接处理"""
        await self.register(websocket)
        
        try:
            async for message in websocket:
                # 处理客户端消息（如 ping）
                try:
                    data = json.loads(message)
                    if data.get("type") == "ping":
                        await websocket.send(json.dumps({"type": "pong"}))
                except:
                    pass
        except websockets.exceptions.ConnectionClosed:
            pass
        finally:
            await self.unregister(websocket)
    
    def _poll_kafka(self):
        """在线程中执行 Kafka poll"""
        try:
            return self.consumer.poll(timeout_ms=100, max_records=20)
        except Exception as e:
            print(f"[ERROR] Kafka poll: {e}")
            return {}
    
    async def kafka_consumer_loop(self):
        """Kafka 消费循环"""
        self.connect_kafka()
        self.running = True
        
        loop = asyncio.get_event_loop()
        last_print = time.time()
        
        print("[OK] 开始消费 Kafka 消息...")
        
        while self.running:
            # 在线程池中执行阻塞的 poll
            messages = await loop.run_in_executor(None, self._poll_kafka)
            
            if not messages:
                await asyncio.sleep(0.01)
                continue
            
            for tp, records in messages.items():
                for msg in records:
                    self.stats["messages_received"] += 1
                    
                    # 广播给所有 WebSocket 客户端
                    await self.broadcast(json.dumps(msg.value))
            
            # 打印统计
            if time.time() - last_print > 2:
                print(f"\r[Bridge] 收:{self.stats['messages_received']:,} "
                      f"发:{self.stats['messages_sent']:,} "
                      f"客户端:{self.stats['clients_connected']}    ", end="", flush=True)
                last_print = time.time()
    
    async def run(self):
        """运行服务"""
        print(f"[OK] WebSocket Bridge 启动: ws://0.0.0.0:{WS_PORT}/realtime")
        
        # 启动 WebSocket 服务器
        async with websockets.serve(
            self.ws_handler,
            "0.0.0.0",
            WS_PORT,
            ping_interval=30,
            ping_timeout=10,
        ):
            # 启动 Kafka 消费循环
            await self.kafka_consumer_loop()
    
    def stop(self):
        self.running = False
        if self.consumer:
            self.consumer.close()


# 主入口
if __name__ == "__main__":
    bridge = WebSocketBridge()
    
    try:
        asyncio.run(bridge.run())
    except KeyboardInterrupt:
        print("\n[停止]")
        bridge.stop()
