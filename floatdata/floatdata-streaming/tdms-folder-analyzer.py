#!/usr/bin/env python3
"""
TDMS Folder Analyzer
分析整个文件夹的TDMS信号数据
支持 signal-1 (单文件多通道) 和 signal-2 (多文件组合)
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

def analyze_signal1(base_path, sample_rate, cutoff_freq, filter_order):
    """分析 Signal-1 文件夹（单文件多通道）"""
    try:
        # Signal-1 是一个文件包含多个通道
        tdms_file_path = Path(base_path) / 'floatdata' / 'signal-1' / 'ae_sim_2s.tdms'
        
        print(f"[INFO] 读取文件: {tdms_file_path}", file=sys.stderr)
        
        tdms_file = TdmsFile.read(str(tdms_file_path))
        
        # 读取通道数据
        channel_data = {}
        for group in tdms_file.groups():
            for channel in group.channels():
                data = channel[:]
                if data is not None and len(data) > 0:
                    channel_data[channel.name] = np.array(data)
                    print(f"[INFO] 通道 '{channel.name}': {len(data)} 个采样点", file=sys.stderr)
        
        # 提取原始信号和加噪信号
        sine_signal = None
        noisy_signal = None
        
        for name, data in channel_data.items():
            if 'sine' in name.lower() and 'plus' not in name.lower():
                sine_signal = data
                print(f"[INFO] 原始信号: {name}", file=sys.stderr)
            elif 'plus' in name.lower() or 'mix' in name.lower():
                noisy_signal = data
                print(f"[INFO] 加噪信号: {name}", file=sys.stderr)
        
        if sine_signal is None or noisy_signal is None:
            return {
                "error": "无法找到原始信号和加噪信号",
                "channels": list(channel_data.keys())
            }
        
        # 应用滤波
        print(f"[INFO] 应用滤波: cutoff={cutoff_freq}Hz, order={filter_order}", file=sys.stderr)
        filtered_signal = apply_lowpass_filter(noisy_signal, sample_rate, cutoff_freq, filter_order)
        
        # 创建时间轴
        n_samples = len(sine_signal)
        time = (np.arange(n_samples) / sample_rate * 1000).tolist()
        
        # 计算性能指标
        mse_before = float(np.mean((sine_signal - noisy_signal) ** 2))
        mse_after = float(np.mean((sine_signal - filtered_signal) ** 2))
        mse_improvement = float((1 - mse_after / mse_before) * 100) if mse_before > 0 else 0
        
        corr_before = float(np.corrcoef(sine_signal, noisy_signal)[0, 1])
        corr_after = float(np.corrcoef(sine_signal, filtered_signal)[0, 1])
        
        # 返回所有采样点（200000个，2秒数据）
        max_samples = n_samples
        
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
                "folder": "signal-1",
                "mode": "单文件多通道",
                "sampleRate": sample_rate,
                "cutoffFreq": cutoff_freq,
                "filterOrder": filter_order,
                "totalSamples": n_samples
            }
        }
        
        print(f"[INFO] Signal-1 分析完成", file=sys.stderr)
        return result
        
    except Exception as e:
        return {
            "error": str(e),
            "traceback": __import__('traceback').format_exc()
        }

def analyze_signal2(base_path, sample_rate, cutoff_freq, filter_order):
    """分析 Signal-2 文件夹（多文件组合）"""
    try:
        signal2_dir = Path(base_path) / 'floatdata' / 'signal-2'
        
        print(f"[INFO] 读取 Signal-2 文件夹: {signal2_dir}", file=sys.stderr)
        
        # 读取三个文件
        sine_file = signal2_dir / 'ae_sine_2s.tdms'
        noise_file = signal2_dir / 'ae_noise_2s.tdms'
        mix_file = signal2_dir / 'ae_mix_2s.tdms'
        
        # 读取原始信号 - 指定通道名 'sine'
        sine_tdms = TdmsFile.read(str(sine_file))
        sine_signal = None
        for group in sine_tdms.groups():
            for channel in group.channels():
                # 跳过时间通道，只读取信号通道
                if channel.name == 'sine':
                    data = channel[:]
                    if data is not None and len(data) > 0:
                        sine_signal = np.array(data)
                        print(f"[INFO] 原始信号 (sine): {len(data)} 个采样点, 范围: [{min(data):.4f}, {max(data):.4f}]", file=sys.stderr)
                        break
            if sine_signal is not None:
                break
        
        # 读取噪声信号 - 指定通道名 'noise'
        noise_tdms = TdmsFile.read(str(noise_file))
        noise_signal = None
        for group in noise_tdms.groups():
            for channel in group.channels():
                if channel.name == 'noise':
                    data = channel[:]
                    if data is not None and len(data) > 0:
                        noise_signal = np.array(data)
                        print(f"[INFO] 噪声信号 (noise): {len(data)} 个采样点, 范围: [{min(data):.4f}, {max(data):.4f}]", file=sys.stderr)
                        break
            if noise_signal is not None:
                break
        
        # 读取混合信号 - 指定通道名 'sine_plus_noise'
        mix_tdms = TdmsFile.read(str(mix_file))
        mix_signal = None
        for group in mix_tdms.groups():
            for channel in group.channels():
                if channel.name == 'sine_plus_noise':
                    data = channel[:]
                    if data is not None and len(data) > 0:
                        mix_signal = np.array(data)
                        print(f"[INFO] 混合信号 (sine_plus_noise): {len(data)} 个采样点, 范围: [{min(data):.4f}, {max(data):.4f}]", file=sys.stderr)
                        break
            if mix_signal is not None:
                break
        
        if sine_signal is None or mix_signal is None:
            return {
                "error": "无法读取信号文件",
                "files_checked": ["ae_sine_2s.tdms", "ae_mix_2s.tdms"]
            }
        
        # 应用滤波
        print(f"[INFO] 应用滤波: cutoff={cutoff_freq}Hz, order={filter_order}", file=sys.stderr)
        filtered_signal = apply_lowpass_filter(mix_signal, sample_rate, cutoff_freq, filter_order)
        
        # 创建时间轴
        n_samples = len(sine_signal)
        time = (np.arange(n_samples) / sample_rate * 1000).tolist()
        
        # 计算性能指标
        mse_before = float(np.mean((sine_signal - mix_signal) ** 2))
        mse_after = float(np.mean((sine_signal - filtered_signal) ** 2))
        mse_improvement = float((1 - mse_after / mse_before) * 100) if mse_before > 0 else 0
        
        corr_before = float(np.corrcoef(sine_signal, mix_signal)[0, 1])
        corr_after = float(np.corrcoef(sine_signal, filtered_signal)[0, 1])
        
        # 返回所有采样点（200000个，2秒数据）
        max_samples = n_samples
        
        result = {
            "signals": {
                "time": time[:max_samples],
                "sine": sine_signal[:max_samples].tolist(),
                "noisy": mix_signal[:max_samples].tolist(),
                "filtered": filtered_signal[:max_samples].tolist(),
                "noise": noise_signal[:max_samples].tolist() if noise_signal is not None else []
            },
            "metrics": {
                "mseImprovement": round(mse_improvement, 2),
                "mseBefore": round(mse_before, 6),
                "mseAfter": round(mse_after, 6),
                "correlation": round(corr_after, 4),
                "correlationBefore": round(corr_before, 4)
            },
            "parameters": {
                "folder": "signal-2",
                "mode": "多文件组合（3个文件）",
                "files": ["ae_sine_2s.tdms", "ae_noise_2s.tdms", "ae_mix_2s.tdms"],
                "sampleRate": sample_rate,
                "cutoffFreq": cutoff_freq,
                "filterOrder": filter_order,
                "totalSamples": n_samples
            }
        }
        
        print(f"[INFO] Signal-2 分析完成", file=sys.stderr)
        return result
        
    except Exception as e:
        return {
            "error": str(e),
            "traceback": __import__('traceback').format_exc()
        }

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "缺少文件夹参数"}))
        sys.exit(1)
    
    folder = sys.argv[1]
    sample_rate = int(sys.argv[2]) if len(sys.argv) > 2 else 100000
    cutoff_freq = int(sys.argv[3]) if len(sys.argv) > 3 else 10000
    filter_order = int(sys.argv[4]) if len(sys.argv) > 4 else 6
    
    # 获取项目根目录
    script_dir = Path(__file__).parent
    base_path = script_dir.parent.parent  # 返回到 CW_Cloud 目录
    
    print(f"[INFO] 开始分析: {folder}", file=sys.stderr)
    print(f"[INFO] 基础路径: {base_path}", file=sys.stderr)
    
    if folder == 'signal-1':
        result = analyze_signal1(base_path, sample_rate, cutoff_freq, filter_order)
    elif folder == 'signal-2':
        result = analyze_signal2(base_path, sample_rate, cutoff_freq, filter_order)
    else:
        result = {"error": f"不支持的文件夹: {folder}"}
    
    print(json.dumps(result, ensure_ascii=False))
