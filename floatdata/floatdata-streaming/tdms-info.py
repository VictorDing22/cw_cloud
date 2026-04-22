#!/usr/bin/env python3
"""
TDMS File Information Extractor
获取TDMS文件的详细信息并输出JSON格式
"""

import sys
import json
from pathlib import Path
from nptdms import TdmsFile

def get_tdms_info(file_path):
    """获取TDMS文件信息"""
    try:
        tdms_file = TdmsFile.read(file_path)
        
        groups = tdms_file.groups()
        total_channels = 0
        total_samples = 0
        channel_names = []
        
        for group in groups:
            channels = group.channels()
            total_channels += len(channels)
            
            for channel in channels:
                channel_names.append(channel.name)
                data = channel[:]
                if data is not None and len(data) > total_samples:
                    total_samples = len(data)
        
        # 假设采样率（如果文件中有，从属性中读取）
        sample_rate = 100000  # 默认100kHz
        duration = total_samples / sample_rate if total_samples > 0 else 0
        
        info = {
            "name": Path(file_path).name,
            "size": Path(file_path).stat().st_size,
            "sampleRate": sample_rate,
            "channels": total_channels,
            "channelNames": channel_names,
            "samples": total_samples,
            "duration": round(duration, 3)
        }
        
        return info
        
    except Exception as e:
        return {
            "error": str(e),
            "name": Path(file_path).name,
            "size": Path(file_path).stat().st_size if Path(file_path).exists() else 0
        }

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Missing file path argument"}))
        sys.exit(1)
    
    file_path = sys.argv[1]
    
    if not Path(file_path).exists():
        print(json.dumps({"error": "File not found"}))
        sys.exit(1)
    
    info = get_tdms_info(file_path)
    print(json.dumps(info, ensure_ascii=False))
