"""
Edge Device Acoustic Emission Simulator — reads real TDMS files from floatdata/data.

Scans the data directory, groups files by device prefix (e.g. data-10-left-{1,2,3}),
slices each channel's waveform into fixed-size fragments, and sends them to Kafka raw_topic
in the protocol format:

    deviceid:通道号:片段号:时间戳,v1,v2,v3,...

Usage:
    pip install kafka-python nptdms

    # Send all TDMS files, 1000 samples per fragment
    python simulate_edge_device.py

    # Only a specific device group, 2000 samples per fragment
    python simulate_edge_device.py --filter "data-10-left" --frag-size 2000

    # Send 50 fragments then stop
    python simulate_edge_device.py --burst 50

    # List detected device groups without sending
    python simulate_edge_device.py --list
"""

import argparse
import hashlib
import os
import re
import signal
import sys
import time
from collections import defaultdict
from datetime import datetime

try:
    from nptdms import TdmsFile
except ImportError:
    print("ERROR: nptdms not installed. Run: pip install nptdms")
    sys.exit(1)

try:
    from kafka import KafkaProducer
    from kafka.errors import KafkaError
except ImportError:
    print("ERROR: kafka-python not installed. Run: pip install kafka-python")
    sys.exit(1)

TOPIC = "raw_topic"
DEFAULT_DATA_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "..", "floatdata", "data")
running = True


def sig_handler(_, __):
    global running
    running = False
    print("\nStopping simulator...")

signal.signal(signal.SIGINT, sig_handler)
signal.signal(signal.SIGTERM, sig_handler)


def device_partitioner(key: bytes, all_partitions, available_partitions):
    h = int(hashlib.md5(key).hexdigest(), 16)
    parts = available_partitions if available_partitions else all_partitions
    return parts[h % len(parts)]


def discover_device_groups(data_dir: str):
    """
    Group TDMS files into virtual devices by naming convention.

    Pattern: data-{id}-{position}-{channel}.tdms  →  device = "data-{id}-{position}", ch = {channel}
    Pattern: data{year}-{position}-{channel}.tdms  →  device = "data{year}-{position}", ch = {channel}
    Fallback: standalone file  →  device = filename stem, ch = 1
    """
    groups = defaultdict(dict)
    pattern = re.compile(r"^(.+?)-(\d+)\.tdms$")

    tdms_files = sorted(f for f in os.listdir(data_dir) if f.endswith(".tdms"))

    for fname in tdms_files:
        m = pattern.match(fname)
        if m:
            device_prefix = m.group(1)
            channel_num = int(m.group(2))
        else:
            device_prefix = fname.replace(".tdms", "")
            channel_num = 1

        groups[device_prefix][channel_num] = os.path.join(data_dir, fname)

    return dict(groups)


def load_channel_data(filepath: str):
    """Read a TDMS file and return (voltage_array, sampling_rate_hz, start_time_ms)."""
    f = TdmsFile.read(filepath)
    group = f.groups()[0]
    ch = group.channels()[0]
    data = ch.data
    wf_inc = ch.properties.get("wf_increment", 5e-7)
    sampling_rate = int(round(1.0 / wf_inc))
    start_time = ch.properties.get("wf_start_time", None)
    if start_time is not None:
        import numpy as np
        ts_ms = int((start_time - np.datetime64("1970-01-01T00:00:00")) / np.timedelta64(1, "ms"))
    else:
        ts_ms = int(time.time() * 1000)
    return data, sampling_rate, ts_ms


def run_simulator(args):
    global running

    data_dir = os.path.abspath(args.data_dir)
    if not os.path.isdir(data_dir):
        print(f"ERROR: data directory not found: {data_dir}")
        sys.exit(1)

    print(f"Scanning TDMS files in: {data_dir}")
    device_groups = discover_device_groups(data_dir)

    if not device_groups:
        print("ERROR: no TDMS files found.")
        sys.exit(1)

    if args.filter:
        device_groups = {k: v for k, v in device_groups.items() if args.filter in k}
        if not device_groups:
            print(f"ERROR: no device groups matching filter '{args.filter}'")
            sys.exit(1)

    print(f"\nDiscovered {len(device_groups)} device group(s):\n")
    for dev, channels in sorted(device_groups.items()):
        ch_list = ", ".join(f"ch{c}" for c in sorted(channels.keys()))
        sizes = ", ".join(
            f"{os.path.getsize(p)/1024/1024:.0f}MB" for _, p in sorted(channels.items())
        )
        print(f"  {dev:30s}  channels=[{ch_list}]  sizes=[{sizes}]")

    if args.list:
        return

    # Load all channel data
    print(f"\nLoading TDMS data into memory...")
    device_data = {}
    for dev, channels in sorted(device_groups.items()):
        device_data[dev] = {}
        for ch_num, fpath in sorted(channels.items()):
            fname = os.path.basename(fpath)
            data, sr, start_ts = load_channel_data(fpath)
            device_data[dev][ch_num] = {
                "data": data, "sampling_rate": sr,
                "start_ts": start_ts, "filename": fname,
            }
            print(f"  Loaded {fname}: {len(data):,} samples @ {sr:,} Hz")

    # Connect to Kafka
    print(f"\nConnecting to Kafka at {args.broker}...")
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

    frag_size = args.frag_size
    print(f"\nStarting replay: frag_size={frag_size}, interval={args.interval}s, "
          f"burst={'infinite' if args.burst == 0 else args.burst}")
    print()

    seq = 0
    total_sent = 0
    total_bytes = 0
    start_time = time.time()

    # Calculate max fragments across all channels
    max_frags = 0
    for dev, channels in device_data.items():
        for ch_num, info in channels.items():
            n = len(info["data"]) // frag_size
            if n > max_frags:
                max_frags = n

    try:
        while running and (args.burst == 0 or seq < args.burst) and seq < max_frags:
            seq += 1
            offset = (seq - 1) * frag_size

            for dev, channels in sorted(device_data.items()):
                device_id = dev.upper().replace(" ", "-")

                for ch_num, info in sorted(channels.items()):
                    data = info["data"]
                    if offset + frag_size > len(data):
                        continue

                    fragment = data[offset : offset + frag_size]
                    # Timestamp: start_ts + offset-based time increment
                    ts_ms = info["start_ts"] + int(offset * 1000.0 / info["sampling_rate"])

                    samples_str = ",".join(f"{v:.6f}" for v in fragment)
                    message = f"{device_id}:{ch_num}:{seq}:{ts_ms},{samples_str}"

                    try:
                        producer.send(TOPIC, key=device_id, value=message)
                        total_sent += 1
                        total_bytes += len(message)
                    except KafkaError as e:
                        print(f"  SEND ERROR: {e}")

            if seq % 10 == 0 or seq == 1:
                producer.flush()
                elapsed = time.time() - start_time
                rate_mb = (total_bytes / 1024 / 1024) / elapsed if elapsed > 0 else 0
                rate_msg = total_sent / elapsed if elapsed > 0 else 0
                pct = seq / max_frags * 100 if max_frags > 0 else 0
                print(
                    f"  [seq={seq:>6}/{max_frags}] sent={total_sent:>8} msgs | "
                    f"{rate_mb:.2f} MB/s | {rate_msg:.0f} msg/s | "
                    f"progress={pct:.1f}%"
                )

            if args.interval > 0:
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
    parser = argparse.ArgumentParser(
        description="AE Edge Device Simulator — replay real TDMS files → Kafka raw_topic"
    )
    parser.add_argument("--broker", default="localhost:9094",
                        help="Kafka bootstrap server (default: localhost:9094)")
    parser.add_argument("--data-dir", default=DEFAULT_DATA_DIR,
                        help="Directory containing .tdms files (default: floatdata/data)")
    parser.add_argument("--frag-size", type=int, default=1000,
                        help="Samples per fragment (default: 1000)")
    parser.add_argument("--interval", type=float, default=0.05,
                        help="Seconds between fragment batches (default: 0.05)")
    parser.add_argument("--burst", type=int, default=0,
                        help="Send N fragment batches then stop; 0=send all (default: 0)")
    parser.add_argument("--filter", type=str, default=None,
                        help="Only send device groups whose name contains this string")
    parser.add_argument("--list", action="store_true",
                        help="List discovered device groups and exit (no data sent)")
    args = parser.parse_args()

    run_simulator(args)


if __name__ == "__main__":
    main()
