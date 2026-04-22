#!/usr/bin/env python3
"""
TDMS Upload Analyzer - 分析用户上传的TDMS文件
复用 tdms-folder-analyzer.py 中的滤波函数
"""

import sys
import json
import numpy as np
from pathlib import Path
from nptdms import TdmsFile

# 导入共享的滤波函数
from importlib.util import spec_from_file_location, module_from_spec
script_dir = Path(__file__).parent
spec = spec_from_file_location("folder_analyzer", script_dir / "tdms-folder-analyzer.py")
folder_analyzer = module_from_spec(spec)
spec.loader.exec_module(folder_analyzer)
apply_lowpass_filter = folder_analyzer.apply_lowpass_filter

def analyze_uploaded_file(file_path, sample_rate, cutoff_freq, filter_order):
    """分析上传的TDMS文件，自动检测通道"""
    try:
        tdms = TdmsFile.read(str(file_path))
        
        # 收集非时间通道
        channels = {}
        for g in tdms.groups():
            for ch in g.channels():
                data = ch[:]
                if data is None or len(data) == 0 or 'time' in ch.name.lower():
                    continue
                arr = np.array(data)
                # 跳过递增序列（时间数据）
                if len(arr) > 10 and np.all(np.diff(arr[:100]) > 0) and np.std(np.diff(arr[:100])) < 1e-6:
                    continue
                channels[ch.name] = arr
        
        if not channels:
            return {"error": "未找到有效信号通道"}
        
        # 智能选择通道
        names = list(channels.keys())
        sine, noisy = None, None
        for n, d in channels.items():
            nl = n.lower()
            if 'sine' in nl and 'plus' not in nl:
                sine, sine_name = d, n
            elif 'plus' in nl or 'mix' in nl:
                noisy, noisy_name = d, n
        
        if sine is None:
            sine, sine_name = channels[names[0]], names[0]
        if noisy is None:
            noisy, noisy_name = (channels[names[1]], names[1]) if len(names) > 1 else (sine, sine_name)
        
        # 滤波
        filtered = apply_lowpass_filter(noisy, sample_rate, cutoff_freq, filter_order)
        n_samples = len(sine)
        time = (np.arange(n_samples) / sample_rate * 1000).tolist()
        
        # 指标
        mse_before = float(np.mean((sine - noisy) ** 2)) if len(sine) == len(noisy) else 0
        mse_after = float(np.mean((sine - filtered) ** 2)) if len(sine) == len(filtered) else 0
        mse_imp = (1 - mse_after / mse_before) * 100 if mse_before > 0 else 0
        
        return {
            "signals": {"time": time, "sine": sine.tolist(), "noisy": noisy.tolist(), "filtered": filtered.tolist()},
            "metrics": {"mseImprovement": round(mse_imp, 2), "mseBefore": round(mse_before, 6), "mseAfter": round(mse_after, 6)},
            "parameters": {"fileName": Path(file_path).name, "channels": names, "sampleRate": sample_rate, "totalSamples": n_samples}
        }
    except Exception as e:
        return {"error": str(e), "traceback": __import__('traceback').format_exc()}

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "缺少文件路径"}))
        sys.exit(1)
    
    result = analyze_uploaded_file(
        sys.argv[1],
        int(sys.argv[2]) if len(sys.argv) > 2 else 100000,
        int(sys.argv[3]) if len(sys.argv) > 3 else 10000,
        int(sys.argv[4]) if len(sys.argv) > 4 else 6
    )
    print(json.dumps(result, ensure_ascii=False))
