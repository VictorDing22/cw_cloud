"""
Detection WebSocket Bridge — 波形实时推送服务

从 Kafka filtered_topic（可选 raw_topic）消费 CSV 格式波形消息，
解析为 JSON 后通过 WebSocket 广播给前端，用于 detection 页面实时波形展示。
"""

import os
import json
import time
import asyncio
import pathlib
from typing import Set, Dict, Optional

from kafka import KafkaConsumer
import websockets
from websockets.http11 import Response as WsResponse
from websockets.datastructures import Headers as WsHeaders


KAFKA_BROKERS = os.getenv("KAFKA_BROKERS", "kafka:9092")
FILTERED_TOPIC = os.getenv("FILTERED_TOPIC", "filtered_topic")
RAW_TOPIC = os.getenv("RAW_TOPIC", "raw_topic")
ANOMALY_TOPIC = os.getenv("ANOMALY_TOPIC", "anomaly_topic")
WS_PORT = int(os.getenv("WS_PORT", "8083"))
THROTTLE_MS = int(os.getenv("THROTTLE_MS", "200"))
INCLUDE_RAW = os.getenv("INCLUDE_RAW", "true").lower() == "true"
INCLUDE_ANOMALY = os.getenv("INCLUDE_ANOMALY", "true").lower() == "true"
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
    total_count = 0

    while pos < csv_len:
        next_comma = voltages_csv.find(",", pos)
        if next_comma < 0:
            next_comma = csv_len
        total_count += 1
        if len(samples) < MAX_DISPLAY_SAMPLES:
            try:
                samples.append(float(voltages_csv[pos:next_comma]))
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


clients: Dict[websockets.WebSocketServerProtocol, Set[str]] = {}
last_sent: Dict[str, float] = {}
stats = {"kafka_received": 0, "ws_broadcast": 0, "ws_throttled": 0, "clients": 0}


def should_send(key: str) -> bool:
    now = time.monotonic()
    last = last_sent.get(key, 0)
    if now - last < THROTTLE_MS / 1000.0:
        stats["ws_throttled"] += 1
        return False
    last_sent[key] = now
    return True


async def broadcast(parsed: Dict):
    if not clients:
        return

    msg_key = f"{parsed['deviceId']}:{parsed['channelId']}"
    msg_str = json.dumps(parsed)
    dead = set()

    for ws, subscriptions in list(clients.items()):
        if subscriptions and msg_key not in subscriptions:
            continue
        try:
            await ws.send(msg_str)
            stats["ws_broadcast"] += 1
        except Exception:
            dead.add(ws)

    for ws in dead:
        clients.pop(ws, None)
        stats["clients"] = len(clients)


async def ws_handler(ws):
    clients[ws] = set()
    stats["clients"] = len(clients)
    remote = getattr(ws, "remote_address", "unknown")
    print(f"[WS] + {remote}  clients={len(clients)}", flush=True)

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

    try:
        async for message in ws:
            try:
                data = json.loads(message)
                msg_type = data.get("type", "")

                if msg_type == "ping":
                    await ws.send(json.dumps({"type": "pong"}))
                elif msg_type == "subscribe":
                    key = f"{data['deviceId']}:{data.get('channelId', '*')}"
                    clients[ws].add(key)
                    await ws.send(json.dumps({
                        "type": "subscribed", "key": key,
                        "subscriptions": list(clients[ws]),
                    }))
                elif msg_type == "unsubscribe":
                    key = f"{data['deviceId']}:{data.get('channelId', '*')}"
                    clients[ws].discard(key)
                elif msg_type == "unsubscribe_all":
                    clients[ws].clear()
            except (json.JSONDecodeError, KeyError):
                pass
    except websockets.exceptions.ConnectionClosed:
        pass
    finally:
        clients.pop(ws, None)
        stats["clients"] = len(clients)
        print(f"[WS] - {remote}  clients={len(clients)}", flush=True)


def parse_anomaly_message(raw_msg: str) -> Optional[Dict]:
    """anomaly_topic messages are already JSON from SignalAnomalyJob"""
    try:
        data = json.loads(raw_msg)
        data["type"] = "anomaly-alert"
        return data
    except (json.JSONDecodeError, KeyError):
        return None


async def kafka_loop():
    topics = [FILTERED_TOPIC]
    if INCLUDE_RAW:
        topics.append(RAW_TOPIC)
    if INCLUDE_ANOMALY:
        topics.append(ANOMALY_TOPIC)

    print(f"[Kafka] Connecting: brokers={KAFKA_BROKERS}, topics={topics}", flush=True)

    loop = asyncio.get_event_loop()
    consumer = KafkaConsumer(
        *topics,
        bootstrap_servers=KAFKA_BROKERS.split(","),
        group_id="detection-ws-bridge",
        auto_offset_reset="latest",
        value_deserializer=lambda m: m.decode("utf-8"),
        max_poll_records=500,
    )

    print(f"[Kafka] Connected, consuming from {topics}", flush=True)
    last_log = time.time()

    while True:
        messages = await loop.run_in_executor(
            None, lambda: consumer.poll(timeout_ms=100, max_records=10000)
        )

        if not messages:
            await asyncio.sleep(0.01)
            continue

        stats["kafka_received"] += sum(len(r) for r in messages.values())

        for tp, records in messages.items():
            if tp.topic == ANOMALY_TOPIC:
                for msg in records[-50:]:
                    parsed = parse_anomaly_message(msg.value)
                    if parsed:
                        await broadcast(parsed)
                continue

            tail = records[-20:] if len(records) > 20 else records
            seen = set()
            for msg in reversed(tail):
                parsed = parse_csv_message(msg.value, tp.topic)
                if parsed is None:
                    continue
                ch_key = f"{parsed['deviceId']}:{parsed['channelId']}"
                if ch_key in seen:
                    continue
                seen.add(ch_key)
                throttle_key = f"{ch_key}:{parsed['type']}"
                if should_send(throttle_key):
                    await broadcast(parsed)

        now = time.time()
        if now - last_log > 5:
            print(f"[Bridge] kafka={stats['kafka_received']:,}  "
                  f"ws_sent={stats['ws_broadcast']:,}  "
                  f"throttled={stats['ws_throttled']:,}  "
                  f"clients={stats['clients']}", flush=True)
            last_log = now


HTML_PATH = pathlib.Path(__file__).parent / "index.html"
HTML_CONTENT = HTML_PATH.read_bytes() if HTML_PATH.exists() else b"<h1>WebSocket Bridge</h1>"


def http_handler(connection, request):
    """非 WebSocket 请求返回波形监控页面"""
    if request.headers.get("Upgrade", "").lower() != "websocket":
        return WsResponse(
            200, "OK",
            WsHeaders([("Content-Type", "text/html; charset=utf-8")]),
            HTML_CONTENT,
        )
    return None


async def main():
    print(f"[OK] Detection WebSocket Bridge", flush=True)
    print(f"     WebSocket: ws://0.0.0.0:{WS_PORT}", flush=True)
    print(f"     Monitor:   http://0.0.0.0:{WS_PORT}/", flush=True)
    async with websockets.serve(
        ws_handler, "0.0.0.0", WS_PORT,
        process_request=http_handler,
    ):
        await kafka_loop()


if __name__ == "__main__":
    asyncio.run(main())
