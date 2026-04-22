#!/usr/bin/env python3
"""
TDMS Data Reader for FloatData Streaming System
Reads real acoustic emission data from TDMS files and sends to Netty server
Extended with signal filtering and visualization capabilities
"""

import socket
import json
import time
import sys
from pathlib import Path
import numpy as np

try:
    from nptdms import TdmsFile
except ImportError:
    print("ERROR: nptdms library not found")
    print("Install it with: pip install nptdms")
    sys.exit(1)

# Optional imports for visualization
try:
    import matplotlib.pyplot as plt
    from scipy import signal as scipy_signal
    VISUALIZATION_AVAILABLE = True
    # 设置中文显示
    plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei']
    plt.rcParams['axes.unicode_minus'] = False
except ImportError:
    VISUALIZATION_AVAILABLE = False
    print("[WARNING] Visualization libraries not available (matplotlib/scipy)")
    print("          Install with: pip install matplotlib scipy")


class TDMSReader:
    def __init__(self, host='localhost', port=9090):
        self.host = host
        self.port = port
        self.socket = None
        self.sample_rate = 1000000  # 1 MHz
        
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
    
    def read_tdms_file(self, file_path, return_channels=False):
        """Read TDMS file and extract signal data
        
        Args:
            file_path: Path to TDMS file
            return_channels: If True, return dict of channel data; if False, return all data
            
        Returns:
            If return_channels=True: dict {channel_name: data_array}
            If return_channels=False: list of all samples
        """
        try:
            tdms_file = TdmsFile.read(file_path)
            print(f"[OK] Opened TDMS file: {file_path}")
            
            # Get all groups and channels
            groups = tdms_file.groups()
            print(f"[INFO] Found {len(groups)} groups")
            
            if return_channels:
                # Return organized channel data
                channel_data = {}
                for group in groups:
                    channels = group.channels()
                    print(f"[INFO] Group '{group.name}' has {len(channels)} channels")
                    
                    for channel in channels:
                        data = channel.data
                        if data is not None and len(data) > 0:
                            print(f"[INFO] Channel '{channel.name}': {len(data)} samples")
                            channel_data[channel.name] = np.array(data)
                
                return channel_data
            else:
                # Return all data concatenated
                all_data = []
                for group in groups:
                    channels = group.channels()
                    print(f"[INFO] Group '{group.name}' has {len(channels)} channels")
                    
                    for channel in channels:
                        data = channel.data
                        if data is not None and len(data) > 0:
                            print(f"[INFO] Channel '{channel.name}': {len(data)} samples")
                            all_data.extend(data)
                
                return all_data
        except Exception as e:
            print(f"[ERROR] Failed to read TDMS file: {e}")
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
    
    def process_file(self, file_path, chunk_size=1000, delay=0.01):
        """Process TDMS file and send data in chunks"""
        print(f"\n[INFO] Processing file: {file_path}")
        
        # Read TDMS file
        samples = self.read_tdms_file(file_path)
        if samples is None or len(samples) == 0:
            print("[ERROR] No data found in TDMS file")
            return False
        
        print(f"[INFO] Total samples: {len(samples)}")
        
        # Send data in chunks
        total_chunks = (len(samples) + chunk_size - 1) // chunk_size
        
        for i in range(total_chunks):
            start_idx = i * chunk_size
            end_idx = min(start_idx + chunk_size, len(samples))
            
            chunk = samples[start_idx:end_idx]
            
            # Convert to list of floats
            chunk_list = [float(x) for x in chunk]
            
            # Send chunk
            if self.send_signal_data(chunk_list):
                print(f"[OK] Sent chunk {i+1}/{total_chunks} ({len(chunk)} samples)")
            else:
                print(f"[ERROR] Failed to send chunk {i+1}")
                return False
            
            # Add delay between chunks
            time.sleep(delay)
        
        return True
    
    def apply_lowpass_filter(self, signal_data, cutoff_freq=10000, order=6):
        """Apply lowpass filter to signal
        
        Args:
            signal_data: Input signal array
            cutoff_freq: Cutoff frequency in Hz
            order: Filter order
            
        Returns:
            Filtered signal array
        """
        if not VISUALIZATION_AVAILABLE:
            print("[ERROR] Scipy not available for filtering")
            return signal_data
            
        nyquist = self.sample_rate / 2
        normalized_cutoff = cutoff_freq / nyquist
        sos = scipy_signal.butter(order, normalized_cutoff, btype='lowpass', output='sos')
        filtered = scipy_signal.sosfilt(sos, signal_data)
        
        print(f"[OK] Applied lowpass filter: cutoff={cutoff_freq}Hz, order={order}")
        return filtered
    
    def visualize_signals(self, channel_data, time_window=0.01, save_path=None):
        """Visualize signal data with filtering effects
        
        Args:
            channel_data: Dict of {channel_name: signal_array}
            time_window: Time window to display in seconds
            save_path: Path to save the figure (optional)
        """
        if not VISUALIZATION_AVAILABLE:
            print("[ERROR] Visualization libraries not available")
            return
        
        # 提取信号
        sine_signal = None
        noisy_signal = None
        
        for name, data in channel_data.items():
            if 'sine' in name.lower() and 'plus' not in name.lower():
                sine_signal = data
                print(f"[INFO] Found原始信号: {name}")
            elif 'plus' in name.lower() or 'mix' in name.lower():
                noisy_signal = data
                print(f"[INFO] Found加噪信号: {name}")
        
        if sine_signal is None or noisy_signal is None:
            print("[ERROR] 无法找到原始信号和加噪信号")
            return
        
        # 应用滤波
        filtered_signal = self.apply_lowpass_filter(noisy_signal, cutoff_freq=10000, order=6)
        
        # 创建时间轴
        n_samples = len(sine_signal)
        time = np.arange(n_samples) / self.sample_rate
        
        # 选择显示窗口
        end_idx = int(time_window * self.sample_rate)
        t = time[:end_idx]
        
        # 创建图形
        fig, axes = plt.subplots(4, 1, figsize=(14, 12))
        fig.suptitle('信号处理与滤波效果分析', fontsize=16, fontweight='bold')
        
        # 1. 原始信号
        axes[0].plot(t * 1000, sine_signal[:end_idx], 'b-', linewidth=1.5, label='原始信号')
        axes[0].set_title('① 原始信号（纯净正弦波）', fontweight='bold')
        axes[0].set_ylabel('幅值')
        axes[0].grid(True, alpha=0.3)
        axes[0].legend()
        
        # 2. 加噪信号
        axes[1].plot(t * 1000, noisy_signal[:end_idx], 'r-', linewidth=0.8, alpha=0.7, label='加噪信号')
        axes[1].set_title('② 加噪信号（原始 + 噪声）', fontweight='bold')
        axes[1].set_ylabel('幅值')
        axes[1].grid(True, alpha=0.3)
        axes[1].legend()
        
        # 3. 滤波后信号
        axes[2].plot(t * 1000, filtered_signal[:end_idx], 'g-', linewidth=1.5, label='滤波后信号')
        axes[2].set_title('③ 滤波后信号', fontweight='bold')
        axes[2].set_ylabel('幅值')
        axes[2].grid(True, alpha=0.3)
        axes[2].legend()
        
        # 4. 效果对比
        axes[3].plot(t * 1000, sine_signal[:end_idx], 'b-', linewidth=2, alpha=0.6, label='原始信号')
        axes[3].plot(t * 1000, filtered_signal[:end_idx], 'g--', linewidth=1.5, alpha=0.8, label='滤波后')
        axes[3].set_title('④ 滤波效果对比（原始 vs 滤波后）', fontweight='bold')
        axes[3].set_xlabel('时间 (ms)')
        axes[3].set_ylabel('幅值')
        axes[3].grid(True, alpha=0.3)
        axes[3].legend()
        
        plt.tight_layout()
        
        # 保存图片
        if save_path:
            plt.savefig(save_path, dpi=300, bbox_inches='tight')
            print(f"[OK] 图片已保存: {save_path}")
        
        plt.show()
        
        # 计算性能指标
        mse_before = np.mean((sine_signal - noisy_signal) ** 2)
        mse_after = np.mean((sine_signal - filtered_signal) ** 2)
        
        print(f"\n[性能指标]")
        print(f"  滤波前 MSE: {mse_before:.6f}")
        print(f"  滤波后 MSE: {mse_after:.6f}")
        print(f"  MSE 改善: {(1 - mse_after/mse_before) * 100:.2f}%")


def visualize_tdms_signals(file_path, sample_rate=100000):
    """Standalone function to visualize TDMS signals
    
    Args:
        file_path: Path to TDMS file
        sample_rate: Sample rate in Hz
    """
    reader = TDMSReader()
    reader.sample_rate = sample_rate
    
    print(f"\n{'='*60}")
    print(f"可视化TDMS信号: {Path(file_path).name}")
    print(f"{'='*60}\n")
    
    # 读取通道数据
    channel_data = reader.read_tdms_file(file_path, return_channels=True)
    
    if channel_data is None or len(channel_data) == 0:
        print("[ERROR] 无法读取信号数据")
        return
    
    # 可视化
    save_path = Path(file_path).parent / f"{Path(file_path).stem}_filter_analysis.png"
    reader.visualize_signals(channel_data, time_window=0.01, save_path=str(save_path))


def main():
    """Main function with visualization support"""
    import argparse
    
    parser = argparse.ArgumentParser(
        description='TDMS Data Reader - 读取和可视化TDMS信号数据'
    )
    parser.add_argument(
        '--visualize', '-v',
        action='store_true',
        help='启用可视化模式（显示信号滤波效果）'
    )
    parser.add_argument(
        '--file', '-f',
        type=str,
        help='指定TDMS文件路径'
    )
    parser.add_argument(
        '--sample-rate', '-s',
        type=int,
        default=100000,
        help='采样率（Hz），默认100000'
    )
    
    args = parser.parse_args()
    
    print("=" * 60)
    print("TDMS Data Reader for FloatData Streaming System")
    print("=" * 60)
    print()
    
    # 可视化模式
    if args.visualize:
        print("[模式] 可视化与滤波分析")
        print()
        
        # 确定文件路径
        if args.file:
            file_path = Path(args.file)
        else:
            # 尝试在 signal-1 和 signal-2 文件夹查找
            signal1_file = Path("e:/Code/CW_Cloud/floatdata/signal-1/ae_sim_2s.tdms")
            signal2_dir = Path("e:/Code/CW_Cloud/floatdata/signal-2")
            
            if signal1_file.exists():
                file_path = signal1_file
                print(f"[INFO] 使用默认文件: signal-1/ae_sim_2s.tdms")
            elif signal2_dir.exists():
                tdms_files = list(signal2_dir.glob("*.tdms"))
                if tdms_files:
                    file_path = tdms_files[0]
                    print(f"[INFO] 使用默认文件: signal-2/{file_path.name}")
                else:
                    print("[ERROR] 未找到TDMS文件")
                    print("请使用 --file 参数指定文件路径")
                    sys.exit(1)
            else:
                print("[ERROR] 未找到默认TDMS文件")
                print("请使用 --file 参数指定文件路径")
                sys.exit(1)
        
        if not file_path.exists():
            print(f"[ERROR] 文件不存在: {file_path}")
            sys.exit(1)
        
        # 执行可视化
        visualize_tdms_signals(str(file_path), sample_rate=args.sample_rate)
        
    # 正常传输模式
    else:
        print("[模式] 数据传输到Netty服务器")
        print()
        
        # Configuration
        data_dir = Path("e:/Code/floatdata/data")
        host = "localhost"
        port = 9090
        
        # Check if data directory exists
        if not data_dir.exists():
            print(f"[ERROR] Data directory not found: {data_dir}")
            sys.exit(1)
        
        # Find TDMS files
        tdms_files = list(data_dir.glob("*.tdms"))
        if not tdms_files:
            print(f"[ERROR] No TDMS files found in {data_dir}")
            sys.exit(1)
        
        print(f"[INFO] Found {len(tdms_files)} TDMS files")
        for f in sorted(tdms_files)[:5]:
            print(f"  - {f.name}")
        if len(tdms_files) > 5:
            print(f"  ... and {len(tdms_files) - 5} more")
        print()
        
        # Create reader
        reader = TDMSReader(host, port)
        
        # Connect to server
        if not reader.connect():
            sys.exit(1)
        
        try:
            # Process first TDMS file
            first_file = sorted(tdms_files)[0]
            print(f"[INFO] Processing first file: {first_file.name}")
            print()
            
            reader.process_file(str(first_file), chunk_size=1000, delay=0.01)
            
            print()
            print("=" * 60)
            print("[SUCCESS] Data transmission completed")
            print("=" * 60)
            
        except KeyboardInterrupt:
            print("\n[INFO] Interrupted by user")
        except Exception as e:
            print(f"[ERROR] Unexpected error: {e}")
        finally:
            reader.disconnect()


if __name__ == "__main__":
    main()
