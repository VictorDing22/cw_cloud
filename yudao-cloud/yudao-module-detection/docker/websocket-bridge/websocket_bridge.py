"""
Detection WebSocket Bridge — 波形实时推送服务

从 Kafka filtered_topic（可选 raw_topic）消费 CSV 格式波形消息，
解析为 JSON 后通过 WebSocket 广播给前端，用于 detection 页面实时波形展示。

支持：
- 多 topic 消费（filtered_topic + raw_topic 对比展示）
- 按 device:channel 节流（避免 WebSocket 带宽过载）
- 客户端订阅过滤（只接收关注的设备/通道数据）
"""

import os
import json
import time
import asyncio
from typing import Set, Dict, Optional

from kafka import KafkaConsumer
import websockets
from websockets.server import WebSocketServerProtocol


KAFKA_BROKERS = os.getenv("KAFKA_BROKERS", "kafka:9092")
FILTERED_TOPIC = os.getenv("FILTERED_TOPIC", "filtered_topic")
RAW_TOPIC = os.getenv("RAW_TOPIC", "raw_topic")
WS_PORT = int(os.getenv("WS_PORT", "8083"))
THROTTLE_MS = int(os.getenv("THROTTLE_MS", "200"))
INCLUDE_RAW = os.getenv("INCLUDE_RAW", "true").lower() == "true"
MAX_DISPLAY_SAMPLES = int(os.getenv("MAX_DISPLAY_SAMPLES", "500"))


def parse_csv_message(raw_msg: str, topic: str) -> Optional[Dict]:
    """解析 CSV 协议消息为前端 JSON 格式
    输入: DATA-10-LEFT:1:42:1681105991850000000,0.000519,-0.000121,...
    """
    first_comma = raw_msg.find(",")
    if first_comma < 0:
        return None

    header = raw_msg[:first_comma]
    parts = header.split(":")
    if len(parts) < 4:
        return None

    try:
        device_id = parts[0]
        channel_id = int(parts[1])
        seq = int(parts[2])
        timestamp = int(parts[3])
    except (ValueError, IndexError):
        return None

    voltages_csv = raw_msg[first_comma + 1:]
    samples = []
    pos = 0
    csv_len = len(voltages_csv)
    count = 0
    total_count = 0

    while pos < csv_len:
        next_comma = voltages_csv.find(",", pos)
        if next_comma < 0:
            next_comma = csv_len
        total_count += 1
        if count < MAX_DISPLAY_SAMPLES:
            try:
                samples.append(float(voltages_csv[pos:next_comma]))
                count += 1
            except ValueError:
                pass
        pos = next_comma + 1

    signal_type = "raw-signal" if topic == RAW_TOPIC else "filtered-signal"

    return {
        "type": signal_type,
        "deviceId": device_id,
        "channelId": channel_id,
        "seq": seq,
        "timestamp": timestamp,
        "samples": samples,
        "sampleCount": total_count,
        "displayCount": len(samples),
    }


class DetectionWsBridge:
    """WebSocket Bridge: Kafka CSV → JSON WebSocket 广播"""

    def __init__(self):
        self.clients: Dict[WebSocketServerProtocol, Set[str]] = {}
        self.running = False
        self.last_sent: Dict[str, float] = {}
        self.stats = {
            "kafka_received": 0,
            "ws_broadcast": 0,
            "ws_throttled": 0,
            "clients": 0,
        }

    async def register(self, ws: WebSocketServerProtocol):
        self.clients[ws] = set()
        self.stats["clients"] = len(self.clients)
        print(f"[WS] + {ws.remote_address}  clients={len(self.clients)}")

        topics = [FILTERED_TOPIC]
        if INCLUDE_RAW:
            topics.append(RAW_TOPIC)

        await ws.send(json.dumps({
            "type": "welcome",
            "message": "Detection waveform stream",
            "topics": topics,
            "throttleMs": THROTTLE_MS,
            "maxSamples": MAX_DISPLAY_SAMPLES,
        }))

    async def unregister(self, ws: WebSocketServerProtocol):
        self.clients.pop(ws, None)
        self.stats["clients"] = len(self.clients)
        print(f"[WS] - {ws.remote_address}  clients={len(self.clients)}")

    def should_send(self, key: str) -> bool:
        """按 device:channel:type 节流"""
        now = time.monotonic()
        last = self.last_sent.get(key, 0)
        if now - last < THROTTLE_MS / 1000.0:
            self.stats["ws_throttled"] += 1
            return False
        self.last_sent[key] = now
        return True

    async def broadcast(self, parsed: Dict):
        """广播给已订阅的客户端"""
        if not self.clients:
            return

        msg_key = f"{parsed['deviceId']}:{parsed['channelId']}"
        msg_str = json.dumps(parsed)
        dead = set()

        for ws, subscriptions in list(self.clients.items()):
            # 空订阅集 = 接收全部; 否则只接收订阅的 device:channel
            if subscriptions and msg_key not in subscriptions:
                continue
            try:
                await ws.send(msg_str)
                self.stats["ws_broadcast"] += 1
            except websockets.exceptions.ConnectionClosed:
                dead.add(ws)
            except Exception:
                dead.add(ws)

        for ws in dead:
            await self.unregister(ws)

    async def ws_handler(self, ws: WebSocketServerProtocol):
        await self.register(ws)
        try:
            async for message in ws:
                try:
                    data = json.loads(message)
                    msg_type = data.get("type", "")

                    if msg_type == "ping":
                        await ws.send(json.dumps({"type": "pong"}))

                    elif msg_type == "subscribe":
                        key = f"{data['deviceId']}:{data.get('channelId', '*')}"
                        self.clients[ws].add(key)
                        await ws.send(json.dumps({
                            "type": "subscribed",
                            "key": key,
                            "subscriptions": list(self.clients[ws]),
                        }))

                    elif msg_type == "unsubscribe":
                        key = f"{data['deviceId']}:{data.get('channelId', '*')}"
                        self.clients[ws].discard(key)

                    elif msg_type == "unsubscribe_all":
                        self.clients[ws].clear()

                except (json.JSONDecodeError, KeyError):
                    pass

        except websockets.exceptions.ConnectionClosed:
            pass
        finally:
            await self.unregister(ws)

    async def kafka_loop(self):
        """Kafka 消费循环"""
        topics = [FILTERED_TOPIC]
        if INCLUDE_RAW:
            topics.append(RAW_TOPIC)

        print(f"[Kafka] Connecting: brokers={KAFKA_BROKERS}, topics={topics}")

        loop = asyncio.get_event_loop()
        consumer = KafkaConsumer(
            *topics,
            bootstrap_servers=KAFKA_BROKERS.split(","),
            group_id=f"detection-ws-bridge-{int(time.time())}",
            auto_offset_reset="latest",
            value_deserializer=lambda m: m.decode("utf-8"),
            max_poll_records=50,
        )

        print(f"[Kafka] Connected, consuming from {topics}")
        self.running = True
        last_log = time.time()

        while self.running:
            messages = await loop.run_in_executor(
                None, lambda: consumer.poll(timeout_ms=200, max_records=50)
            )

            if not messages:
                await asyncio.sleep(0.01)
                continue

            for tp, records in messages.items():
                for msg in records:
                    self.stats["kafka_received"] += 1

                    parsed = parse_csv_message(msg.value, tp.topic)
                    if parsed is None:
                        continue

                    throttle_key = f"{parsed['deviceId']}:{parsed['channelId']}:{parsed['type']}"
                    if self.should_send(throttle_key):
                        await self.broadcast(parsed)

            now = time.time()
            if now - last_log > 5:
                print(f"[Bridge] kafka={self.stats['kafka_received']:,}  "
                      f"ws_sent={self.stats['ws_broadcast']:,}  "
                      f"throttled={self.stats['ws_throttled']:,}  "
                      f"clients={self.stats['clients']}")
                last_log = now

    async def run(self):
        print(f"[OK] Detection WebSocket Bridge: ws://0.0.0.0:{WS_PORT}/realtime")
        async with websockets.serve(
            self.ws_handler, "0.0.0.0", WS_PORT,
            ping_interval=30, ping_timeout=10,
        ):
            await self.kafka_loop()

    def stop(self):
        self.running = False


if __name__ == "__main__":
    bridge = DetectionWsBridge()
    try:
        asyncio.run(bridge.run())
    except KeyboardInterrupt:
        print("\n[STOP]")
        bridge.stop()
