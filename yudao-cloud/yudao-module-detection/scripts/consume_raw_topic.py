"""
Quick consumer to verify data in raw_topic.
Prints decoded messages with partition/offset info.

Usage:
  python consume_raw_topic.py                       # latest messages
  python consume_raw_topic.py --from-beginning      # all messages
  python consume_raw_topic.py --count 20            # stop after 20
"""

import argparse
import signal
import sys
import time

try:
    from kafka import KafkaConsumer
except ImportError:
    print("ERROR: kafka-python not installed. Run: pip install kafka-python")
    sys.exit(1)

running = True

def sig_handler(_, __):
    global running
    running = False

signal.signal(signal.SIGINT, sig_handler)
signal.signal(signal.SIGTERM, sig_handler)


def parse_message(raw: str):
    """Parse: deviceid:ch:seq:ts,v1,v2,..."""
    header_end = raw.find(",")
    if header_end == -1:
        return None
    header = raw[:header_end]
    parts = header.split(":")
    if len(parts) != 4:
        return None
    values_str = raw[header_end + 1:]
    num_samples = values_str.count(",") + 1
    first_few = values_str.split(",")[:5]
    return {
        "device_id": parts[0],
        "channel": int(parts[1]),
        "seq": int(parts[2]),
        "timestamp": int(parts[3]),
        "num_samples": num_samples,
        "preview": [float(v) for v in first_few],
    }


def main():
    parser = argparse.ArgumentParser(description="Consume raw_topic messages")
    parser.add_argument("--broker", default="localhost:9094", help="Kafka broker")
    parser.add_argument("--topic", default="raw_topic")
    parser.add_argument("--from-beginning", action="store_true")
    parser.add_argument("--count", type=int, default=0, help="Stop after N messages; 0=unlimited")
    args = parser.parse_args()

    offset_reset = "earliest" if args.from_beginning else "latest"

    print(f"Consuming from {args.topic} @ {args.broker} (offset={offset_reset})...")
    consumer = KafkaConsumer(
        args.topic,
        bootstrap_servers=[args.broker],
        auto_offset_reset=offset_reset,
        consumer_timeout_ms=5000,
        group_id=f"debug-consumer-{int(time.time())}",
        value_deserializer=lambda m: m.decode("utf-8"),
        key_deserializer=lambda k: k.decode("utf-8") if k else None,
    )

    received = 0
    try:
        for msg in consumer:
            if not running:
                break
            received += 1
            parsed = parse_message(msg.value)
            if parsed:
                print(
                    f"  P{msg.partition}|OFF={msg.offset:>6} | "
                    f"dev={parsed['device_id']} ch={parsed['channel']} "
                    f"seq={parsed['seq']:>4} samples={parsed['num_samples']:>5} "
                    f"preview={parsed['preview']}"
                )
            else:
                print(f"  P{msg.partition}|OFF={msg.offset:>6} | RAW: {msg.value[:120]}...")

            if args.count > 0 and received >= args.count:
                break
    except KeyboardInterrupt:
        pass
    finally:
        consumer.close()
        print(f"\nReceived {received} messages total.")


if __name__ == "__main__":
    main()
