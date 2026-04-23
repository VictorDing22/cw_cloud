"""
Edge Device Acoustic Emission Simulator
Simulates multiple devices with 3 channels each, sending data to Kafka raw_topic.

Message format per the technical spec:
  deviceid:通道号:片段号:时间戳,v1,v2,v3,...

Partition routing: hash(deviceId) % num_partitions — same device always lands in the same partition.

Usage:
  pip install kafka-python
  python simulate_edge_device.py                           # default: 2 devices, 1000 Hz, continuous
  python simulate_edge_device.py --devices 4 --rate 2000   # 4 devices, 2000 samples/fragment
  python simulate_edge_device.py --burst 100               # send 100 fragments then stop
"""

import argparse
import hashlib
import json
import math
import random
import signal
import sys
import time
from datetime import datetime

try:
    from kafka import KafkaProducer
    from kafka.errors import KafkaError
except ImportError:
    print("ERROR: kafka-python not installed. Run: pip install kafka-python")
    sys.exit(1)

TOPIC = "raw_topic"
running = True


def sig_handler(_, __):
    global running
    running = False
    print("\nStopping simulator...")

signal.signal(signal.SIGINT, sig_handler)
signal.signal(signal.SIGTERM, sig_handler)


def device_partitioner(key: bytes, all_partitions, available_partitions):
    """Hash-based partitioner: same deviceId always goes to same partition."""
    h = int(hashlib.md5(key).hexdigest(), 16)
    parts = available_partitions if available_partitions else all_partitions
    return parts[h % len(parts)]


def generate_ae_fragment(num_samples: int, inject_anomaly: bool = False):
    """
    Generate a realistic acoustic emission voltage waveform fragment.
    Normal background noise ~0.001V with occasional burst signals.
    """
    samples = []
    base_noise = 0.002
    for i in range(num_samples):
        noise = random.gauss(0, base_noise)
        if inject_anomaly and (num_samples * 0.4 < i < num_samples * 0.6):
            burst_amplitude = random.uniform(0.05, 0.3)
            freq = random.uniform(100_000, 500_000)
            t = i / 2_000_000
            noise += burst_amplitude * math.sin(2 * math.pi * freq * t) * math.exp(
                -((i - num_samples * 0.5) ** 2) / (2 * (num_samples * 0.05) ** 2)
            )
        samples.append(round(noise, 6))
    return samples


def run_simulator(args):
    global running

    print(f"Connecting to Kafka at {args.broker}...")
    producer = KafkaProducer(
        bootstrap_servers=[args.broker],
        key_serializer=lambda k: k.encode("utf-8"),
        value_serializer=lambda v: v.encode("utf-8"),
        acks=1,
        linger_ms=5,
        batch_size=65536,
        compression_type="gzip",
        partitioner=device_partitioner,
    )

    device_ids = [f"AE-DEVICE-{i+1:03d}" for i in range(args.devices)]
    channels_per_device = 3
    sampling_rate = 2_000_000

    print(f"Simulator started:")
    print(f"  Devices       : {device_ids}")
    print(f"  Channels/dev  : {channels_per_device}")
    print(f"  Samples/frag  : {args.rate}")
    print(f"  Sampling rate : {sampling_rate} Hz")
    print(f"  Anomaly prob  : {args.anomaly_pct}%")
    print(f"  Target topic  : {TOPIC}")
    print(f"  Burst count   : {'infinite' if args.burst == 0 else args.burst}")
    print()

    seq = 0
    total_sent = 0
    total_bytes = 0
    start_time = time.time()

    try:
        while running:
            seq += 1
            ts_ms = int(time.time() * 1000)

            for device_id in device_ids:
                inject = random.randint(1, 100) <= args.anomaly_pct

                for ch in range(1, channels_per_device + 1):
                    samples = generate_ae_fragment(args.rate, inject_anomaly=inject)
                    samples_str = ",".join(str(v) for v in samples)

                    # Format: deviceid:通道号:片段号:时间戳,v1,v2,v3,...
                    message = f"{device_id}:{ch}:{seq}:{ts_ms},{samples_str}"

                    try:
                        producer.send(TOPIC, key=device_id, value=message)
                        total_sent += 1
                        total_bytes += len(message)
                    except KafkaError as e:
                        print(f"  SEND ERROR: {e}")

            if seq % 10 == 0:
                producer.flush()
                elapsed = time.time() - start_time
                rate_msg = total_sent / elapsed if elapsed > 0 else 0
                rate_mb = (total_bytes / 1024 / 1024) / elapsed if elapsed > 0 else 0
                print(
                    f"  [seq={seq:>6}] sent={total_sent:>8} msgs | "
                    f"{rate_mb:.2f} MB/s | {rate_msg:.0f} msg/s | "
                    f"ts={datetime.fromtimestamp(ts_ms/1000).strftime('%H:%M:%S')}"
                )

            if args.burst > 0 and seq >= args.burst:
                print(f"\nBurst complete: {args.burst} fragments sent.")
                break

            time.sleep(args.interval)

    finally:
        producer.flush()
        producer.close()
        elapsed = time.time() - start_time
        print(f"\n--- Summary ---")
        print(f"Total messages : {total_sent}")
        print(f"Total data     : {total_bytes / 1024 / 1024:.2f} MB")
        print(f"Elapsed        : {elapsed:.1f}s")
        if elapsed > 0:
            print(f"Throughput     : {(total_bytes / 1024 / 1024) / elapsed:.2f} MB/s")


def main():
    parser = argparse.ArgumentParser(description="AE Edge Device Simulator → Kafka raw_topic")
    parser.add_argument("--broker", default="localhost:9094", help="Kafka bootstrap server (default: localhost:9094)")
    parser.add_argument("--devices", type=int, default=2, help="Number of simulated devices (default: 2)")
    parser.add_argument("--rate", type=int, default=1000, help="Samples per fragment (default: 1000)")
    parser.add_argument("--interval", type=float, default=0.1, help="Seconds between fragments (default: 0.1)")
    parser.add_argument("--anomaly-pct", type=int, default=5, help="Anomaly injection probability %% (default: 5)")
    parser.add_argument("--burst", type=int, default=0, help="Send N fragments then stop; 0=continuous (default: 0)")
    args = parser.parse_args()

    run_simulator(args)


if __name__ == "__main__":
    main()
