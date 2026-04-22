#!/usr/bin/env python3
"""
检查 TDMS 文件的通道结构
"""
import sys
from pathlib import Path
from nptdms import TdmsFile
import numpy as np

def check_file(file_path):
    print(f"\n{'='*60}")
    print(f"文件: {file_path}")
    print('='*60)
    
    try:
        tdms = TdmsFile.read(str(file_path))
        
        for group in tdms.groups():
            print(f"\n组: '{group.name}'")
            for channel in group.channels():
                data = channel[:]
                if data is not None and len(data) > 0:
                    arr = np.array(data)
                    print(f"  通道: '{channel.name}'")
                    print(f"    数据点数: {len(data)}")
                    print(f"    数据类型: {arr.dtype}")
                    print(f"    最小值: {arr.min():.6f}")
                    print(f"    最大值: {arr.max():.6f}")
                    print(f"    前5个值: {arr[:5].tolist()}")
                    print(f"    后5个值: {arr[-5:].tolist()}")
                else:
                    print(f"  通道: '{channel.name}' - 无数据")
    except Exception as e:
        print(f"错误: {e}")

if __name__ == "__main__":
    base_path = Path(__file__).parent.parent.parent
    
    # 检查 Signal-1
    signal1_file = base_path / 'floatdata' / 'signal-1' / 'ae_sim_2s.tdms'
    if signal1_file.exists():
        check_file(signal1_file)
    
    # 检查 Signal-2 的三个文件
    signal2_dir = base_path / 'floatdata' / 'signal-2'
    for fname in ['ae_sine_2s.tdms', 'ae_noise_2s.tdms', 'ae_mix_2s.tdms']:
        fpath = signal2_dir / fname
        if fpath.exists():
            check_file(fpath)
