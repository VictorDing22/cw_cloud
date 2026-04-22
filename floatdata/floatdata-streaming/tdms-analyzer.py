#!/usr/bin/env python3
"""
TDMS Signal Analyzer
分析TDMS信号并应用滤波，输出JSON格式结果
"""

import sys
import json
import numpy as np
from pathlib import Path
from nptdms import TdmsFile
from scipy import signal as scipy_signal

def apply_lowpass_filter(signal_data, sample_rate, cutoff_freq, order):
    """应用低通滤波器"""
    nyquist = sample_rate / 2
    normalized_cutoff = cutoff_freq / nyquist
    sos = scipy_signal.butter(order, normalized_cutoff, btype='lowpass', output='sos')
    filtered = scipy_signal.sosfilt(sos, signal_data)
    return filtered

def analyze_tdms_signal(file_path, sample_rate=100000, cutoff_freq=10000, filter_order=6):
    """分析TDMS信号"""
    try:
        tdms_file = TdmsFile.read(file_path)
        
        # 读取通道数据
        channel_data = {}
        for group in tdms_file.groups():
            for channel in group.channels():
                data = channel[:]
                if data is not None and len(data) > 0:
                    channel_data[channel.name] = np.array(data)
        
        # 提取原始信号和加噪信号
        sine_signal = None
        noisy_signal = None
        
        for name, data in channel_data.items():
            if 'sine' in name.lower() and 'plus' not in name.lower():
                sine_signal = data
            elif 'plus' in name.lower() or 'mix' in name.lower():
                noisy_signal = data
        
        if sine_signal is None or noisy_signal is None:
            return {
                "error": "Cannot find original and noisy signals",
                "channels": list(channel_data.keys())
            }
        
        # 应用滤波
        filtered_signal = apply_lowpass_filter(noisy_signal, sample_rate, cutoff_freq, filter_order)
        
        # 计算时间轴
        n_samples = len(sine_signal)
        time = (np.arange(n_samples) / sample_rate * 1000).tolist()  # 转换为ms
        
        # 计算性能指标
        mse_before = float(np.mean((sine_signal - noisy_signal) ** 2))
        mse_after = float(np.mean((sine_signal - filtered_signal) ** 2))
        mse_improvement = float((1 - mse_after / mse_before) * 100) if mse_before > 0 else 0
        
        corr_before = float(np.corrcoef(sine_signal, noisy_signal)[0, 1])
        corr_after = float(np.corrcoef(sine_signal, filtered_signal)[0, 1])
        
        # 为了减小数据量，只取前1000个采样点
        max_samples = min(1000, n_samples)
        
        result = {
            "signals": {
                "time": time[:max_samples],
                "sine": sine_signal[:max_samples].tolist(),
                "noisy": noisy_signal[:max_samples].tolist(),
                "filtered": filtered_signal[:max_samples].tolist()
            },
            "metrics": {
                "mseImprovement": round(mse_improvement, 2),
                "mseBefore": round(mse_before, 6),
                "mseAfter": round(mse_after, 6),
                "correlation": round(corr_after, 4),
                "correlationBefore": round(corr_before, 4)
            },
            "parameters": {
                "sampleRate": sample_rate,
                "cutoffFreq": cutoff_freq,
                "filterOrder": filter_order,
                "totalSamples": n_samples
            }
        }
        
        return result
        
    except Exception as e:
        return {
            "error": str(e),
            "traceback": __import__('traceback').format_exc()
        }

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Missing arguments"}))
        sys.exit(1)
    
    file_path = sys.argv[1]
    sample_rate = int(sys.argv[2]) if len(sys.argv) > 2 else 100000
    cutoff_freq = int(sys.argv[3]) if len(sys.argv) > 3 else 10000
    filter_order = int(sys.argv[4]) if len(sys.argv) > 4 else 6
    
    if not Path(file_path).exists():
        print(json.dumps({"error": "File not found"}))
        sys.exit(1)
    
    result = analyze_tdms_signal(file_path, sample_rate, cutoff_freq, filter_order)
    print(json.dumps(result, ensure_ascii=False))
