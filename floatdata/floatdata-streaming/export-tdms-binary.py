#!/usr/bin/env python3
"""Export TDMS samples to a contiguous float32 binary file."""

import argparse
from pathlib import Path

import numpy as np
from nptdms import TdmsFile


def export_tdms(data_dir: Path, output: Path, max_samples: int) -> None:
    files = sorted(data_dir.glob("*.tdms"))
    if not files:
        raise FileNotFoundError(f"No TDMS files found in {data_dir}")

    total = 0
    with output.open("wb") as fout:
        for tdms_path in files:
            tdms = TdmsFile.read(tdms_path)
            for group in tdms.groups():
                for channel in group.channels():
                    data = channel[:]
                    if data.size == 0:
                        continue
                    chunk = np.asarray(data, dtype=np.float32)
                    if max_samples:
                        remaining = max_samples - total
                        if remaining <= 0:
                            return
                        chunk = chunk[:remaining]
                    chunk.tofile(fout)
                    total += chunk.size
                    print(
                        f"[INFO] {tdms_path.name}:{group.name}/{channel.name} -> {chunk.size:,} (total={total:,})"
                    )
                    if max_samples and total >= max_samples:
                        return

    print(f"[OK] Export completed, total samples: {total:,}")


def parse_args():
    parser = argparse.ArgumentParser(description="Export TDMS data to binary float file")
    parser.add_argument("--data-dir", type=Path, default=Path("e:/Code/floatdata/data"))
    parser.add_argument("--output", type=Path, default=Path("tdms-export.bin"))
    parser.add_argument("--max-samples", type=int, default=6_000_000)
    return parser.parse_args()


def main():
    args = parse_args()
    export_tdms(args.data_dir, args.output, args.max_samples)


if __name__ == "__main__":
    main()
