#!/usr/bin/env python3
"""
High-Rate Real Data Simulator for FloatData Streaming System
Streams real TDMS signal data through the Netty server at up to 2 million samples per second.
"""

import argparse
import socket
import struct
import time
from array import array
from pathlib import Path
from typing import List

import numpy as np
from nptdms import TdmsFile


class HighRateSimulator:
    def __init__(self, host: str, port: int, data_dir: Path, sensor_id: int,
                 location: str, sample_rate: int, target_rate: int,
                 chunk_size: int, max_samples: int):
        self.host = host
        self.port = port
        self.data_dir = data_dir
        self.sensor_id = sensor_id
        self.location = location
        self.sample_rate = sample_rate
        self.target_rate = target_rate
        self.chunk_size = chunk_size
        self.max_samples = max_samples

        if self.target_rate < self.chunk_size:
            raise ValueError("target_rate must be greater than chunk_size")

        self.samples = self._load_samples()
        self.sample_count = len(self.samples)
        if self.sample_count == 0:
            raise RuntimeError("No samples loaded from TDMS files")
        self.cursor = 0

        self.socket = None
        self.location_bytes = self.location.encode("utf-8")

    def _load_samples(self) -> np.ndarray:
        tdms_files = sorted(self.data_dir.glob("*.tdms"))
        if not tdms_files:
            raise FileNotFoundError(f"No TDMS files found in {self.data_dir}")

        print(f"[INFO] Loading TDMS files from {self.data_dir}")
        samples: List[np.ndarray] = []
        total_loaded = 0

        for tdms_path in tdms_files:
            try:
                tdms_file = TdmsFile.read(tdms_path)
            except Exception as exc:
                print(f"[WARN] Failed to read {tdms_path.name}: {exc}")
                continue

            for group in tdms_file.groups():
                for channel in group.channels():
                    data = channel[:]  # numpy array
                    if data.size == 0:
                        continue

                    float_data = np.asarray(data, dtype=np.float32)
                    samples.append(float_data)
                    total_loaded += float_data.size

                    print(f"[INFO] Loaded {float_data.size:,} samples from "
                          f"{tdms_path.name}:{group.name}/{channel.name} "
                          f"(total={total_loaded:,})")

                    if total_loaded >= self.max_samples:
                        print(f"[INFO] Reached max_samples={self.max_samples:,}")
                        return np.concatenate(samples)[: self.max_samples]

        return np.concatenate(samples)

    def connect(self):
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.socket.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        self.socket.connect((self.host, self.port))
        print(f"[OK] Connected to Netty server at {self.host}:{self.port}")

    def disconnect(self):
        if self.socket:
            self.socket.close()
            self.socket = None
            print("[OK] Disconnected from server")

    def _next_chunk(self) -> np.ndarray:
        if self.cursor + self.chunk_size <= self.sample_count:
            chunk = self.samples[self.cursor : self.cursor + self.chunk_size]
            self.cursor += self.chunk_size
        else:
            tail = self.samples[self.cursor :]
            remaining = self.chunk_size - tail.size
            head = self.samples[:remaining]
            chunk = np.concatenate((tail, head))
            self.cursor = remaining
        return chunk

    def _build_packet(self, samples: np.ndarray) -> bytes:
        timestamp = int(time.time() * 1000)
        header = struct.pack(
            "<qiiH",
            timestamp,
            self.sensor_id,
            self.sample_rate,
            len(self.location_bytes),
        )
        samples_len = len(samples)
        body = struct.pack("<i", samples_len)
        float_bytes = samples.tobytes()
        return header + self.location_bytes + body + float_bytes

    def run(self):
        target_chunks_per_sec = self.target_rate / self.chunk_size
        print(
            "=" * 70,
            "\nHigh-Rate Real Data Simulator\n",
            f"Target throughput   : {self.target_rate:,} samples/sec",
            f"Chunk size          : {self.chunk_size:,} samples",
            f"Chunks per second   : {target_chunks_per_sec:,.0f}",
            f"Loaded samples      : {self.sample_count:,}",
            f"Sensor ID           : {self.sensor_id}",
            f"Location            : {self.location}",
            f"Sample rate (metadata): {self.sample_rate:,} Hz",
            sep="\n",
        )

        samples_sent = 0
        start_time = time.perf_counter()
        last_report = start_time
        last_samples_report = 0

        try:
            while True:
                chunk = self._next_chunk()
                packet = self._build_packet(chunk)
                self.socket.sendall(packet)
                samples_sent += len(chunk)

                # Throttle to maintain target rate
                elapsed = time.perf_counter() - start_time
                expected = samples_sent / self.target_rate
                if expected > elapsed:
                    time.sleep(min(expected - elapsed, 0.01))

                # Per-second reporting
                now = time.perf_counter()
                if now - last_report >= 1.0:
                    interval_samples = samples_sent - last_samples_report
                    rate = interval_samples / (now - last_report)
                    print(
                        f"[INFO] Sent {samples_sent:,} samples total | "
                        f"{rate:,.0f} samples/sec"
                    )
                    last_report = now
                    last_samples_report = samples_sent

        except KeyboardInterrupt:
            print("\n[INFO] Simulator interrupted by user")
        except Exception as exc:
            print(f"[ERROR] Simulator error: {exc}")
        finally:
            self.disconnect()


def parse_args():
    parser = argparse.ArgumentParser(
        description="High-rate real data simulator for FloatData streaming system"
    )
    parser.add_argument("--host", default="localhost", help="Netty server host")
    parser.add_argument("--port", type=int, default=9090, help="Netty server port")
    parser.add_argument(
        "--data-dir",
        type=Path,
        default=Path("e:/Code/floatdata/data"),
        help="Directory containing TDMS files",
    )
    parser.add_argument("--sensor-id", type=int, default=99, help="Sensor ID")
    parser.add_argument(
        "--location",
        default="high-rate-simulator",
        help="Location label included in metadata",
    )
    parser.add_argument(
        "--sample-rate",
        type=int,
        default=1_000_000,
        help="Sample rate metadata (Hz)",
    )
    parser.add_argument(
        "--target-rate",
        type=int,
        default=2_000_000,
        help="Target samples per second",
    )
    parser.add_argument(
        "--chunk-size",
        type=int,
        default=2000,
        help="Samples per packet",
    )
    parser.add_argument(
        "--max-samples",
        type=int,
        default=5_000_000,
        help="Limit on samples loaded into memory",
    )
    return parser.parse_args()


def main():
    args = parse_args()
    simulator = HighRateSimulator(
        host=args.host,
        port=args.port,
        data_dir=args.data_dir,
        sensor_id=args.sensor_id,
        location=args.location,
        sample_rate=args.sample_rate,
        target_rate=args.target_rate,
        chunk_size=args.chunk_size,
        max_samples=args.max_samples,
    )
    simulator.connect()
    simulator.run()


if __name__ == "__main__":
    main()
