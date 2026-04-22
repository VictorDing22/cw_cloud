#!/usr/bin/env python3
"""
Wallpainting Signal Reader for FloatData Streaming System
Reads wallpainting fracture signal data from text files and sends to Netty server
"""

import socket
import json
import time
import sys
from pathlib import Path


class WallpaintingReader:
    def __init__(self, host='localhost', port=9090):
        self.host = host
        self.port = port
        self.socket = None
        self.sample_rate = 100000  # Estimated 100 kHz
        
    def connect(self):
        """Connect to Netty server"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((self.host, self.port))
            print(f"[OK] Connected to server at {self.host}:{self.port}")
            return True
        except Exception as e:
            print(f"[ERROR] Connection failed: {e}")
            return False
    
    def disconnect(self):
        """Disconnect from server"""
        if self.socket:
            self.socket.close()
            print("[OK] Disconnected from server")
    
    def read_signal_file(self, file_path):
        """Read signal data from text file"""
        try:
            samples = []
            with open(file_path, 'r') as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    # Parse space-separated values
                    values = line.split()
                    for value in values:
                        try:
                            samples.append(float(value))
                        except ValueError:
                            continue
            
            print(f"[OK] Read {len(samples)} samples from {Path(file_path).name}")
            return samples
        except Exception as e:
            print(f"[ERROR] Failed to read file: {e}")
            return None
    
    def send_signal_data(self, samples, sensor_id=1, location="center-horizontal"):
        """Send signal data to Netty server in binary format"""
        try:
            import struct
            
            # Create binary packet: [timestamp(8)] [sensorId(4)] [sampleRate(4)] 
            #                       [locationLen(2)] [location] [samplesLen(4)] [samples...]
            
            timestamp = int(time.time() * 1000)
            location_bytes = location.encode('utf-8')
            location_len = len(location_bytes)
            samples_len = len(samples)
            
            # Pack header
            packet = struct.pack('<q', timestamp)  # timestamp (long)
            packet += struct.pack('<i', sensor_id)  # sensorId (int)
            packet += struct.pack('<i', self.sample_rate)  # sampleRate (int)
            packet += struct.pack('<h', location_len)  # locationLen (short)
            packet += location_bytes  # location string
            packet += struct.pack('<i', samples_len)  # samplesLen (int)
            
            # Pack samples
            for sample in samples:
                packet += struct.pack('<f', float(sample))  # sample (float)
            
            # Send to server
            self.socket.sendall(packet)
            
            return True
        except Exception as e:
            print(f"[ERROR] Failed to send data: {e}")
            return False
    
    def process_file(self, file_path, chunk_size=1000, delay=0.01, location="center-horizontal"):
        """Process signal file and send data in chunks"""
        print(f"\n[INFO] Processing file: {Path(file_path).name}")
        
        # Read signal file
        samples = self.read_signal_file(file_path)
        if samples is None or len(samples) == 0:
            print("[ERROR] No data found in signal file")
            return False
        
        print(f"[INFO] Total samples: {len(samples)}")
        
        # Send data in chunks
        total_chunks = (len(samples) + chunk_size - 1) // chunk_size
        
        for i in range(total_chunks):
            start_idx = i * chunk_size
            end_idx = min(start_idx + chunk_size, len(samples))
            
            chunk = samples[start_idx:end_idx]
            
            # Send chunk
            if self.send_signal_data(chunk, location=location):
                print(f"[OK] Sent chunk {i+1}/{total_chunks} ({len(chunk)} samples)")
            else:
                print(f"[ERROR] Failed to send chunk {i+1}")
                return False
            
            # Add delay between chunks
            time.sleep(delay)
        
        return True


def main():
    """Main function"""
    print("=" * 60)
    print("Wallpainting Signal Reader for FloatData Streaming System")
    print("=" * 60)
    print()
    
    # Configuration
    data_dir = Path("e:/Code/floatdata/壁画断铅")
    host = "localhost"
    port = 9090
    
    # Check if data directory exists
    if not data_dir.exists():
        print(f"[ERROR] Data directory not found: {data_dir}")
        sys.exit(1)
    
    # Find all signal files
    signal_files = []
    locations = ["中部横向", "中部纵向", "顶部横向"]
    
    for location in locations:
        location_dir = data_dir / location
        if location_dir.exists():
            files = sorted(location_dir.glob("signal_*.txt"))
            signal_files.extend([(f, location) for f in files])
    
    if not signal_files:
        print(f"[ERROR] No signal files found in {data_dir}")
        sys.exit(1)
    
    print(f"[INFO] Found {len(signal_files)} signal files")
    for f, loc in signal_files[:5]:
        print(f"  - {f.name} ({loc})")
    if len(signal_files) > 5:
        print(f"  ... and {len(signal_files) - 5} more")
    print()
    
    # Create reader
    reader = WallpaintingReader(host, port)
    
    # Connect to server
    if not reader.connect():
        sys.exit(1)
    
    try:
        # Process all signal files
        total_files = len(signal_files)
        for idx, (signal_file, location) in enumerate(signal_files, 1):
            print(f"\n[{idx}/{total_files}] Processing: {signal_file.name} ({location})")
            
            # Map location to sensor ID
            location_map = {
                "中部横向": 1,
                "中部纵向": 2,
                "顶部横向": 3
            }
            sensor_id = location_map.get(location, 1)
            
            reader.process_file(str(signal_file), chunk_size=500, delay=0.01, location=location)
            
            # Small delay between files
            time.sleep(0.5)
        
        print()
        print("=" * 60)
        print("[SUCCESS] All signal files processed")
        print("=" * 60)
        
    except KeyboardInterrupt:
        print("\n[INFO] Interrupted by user")
    except Exception as e:
        print(f"[ERROR] Unexpected error: {e}")
    finally:
        reader.disconnect()


if __name__ == "__main__":
    main()
