from nptdms import TdmsFile
import numpy as np

# 读取TDMS文件
tdms_file = TdmsFile.read('floatdata/signal-1/ae_sim_2s.tdms')

# 获取通道数据
for group in tdms_file.groups():
    for channel in group.channels():
        if 'sine' in channel.name.lower() and 'plus' not in channel.name.lower():
            data = channel[:]
            # 简单的频率估计：计算零交叉
            sign_changes = np.diff(np.sign(data))
            zero_crossings = np.where(sign_changes != 0)[0]
            
            # 假设采样率100kHz，2秒数据
            sample_rate = 100000
            duration = len(data) / sample_rate
            num_cycles = len(zero_crossings) / 2  # 每个周期2个零交叉
            estimated_freq = num_cycles / duration
            
            print(f"通道: {channel.name}")
            print(f"采样点数: {len(data)}")
            print(f"持续时间: {duration}秒")
            print(f"估计频率: {estimated_freq:.2f} Hz")
            print(f"数据范围: [{np.min(data):.4f}, {np.max(data):.4f}]")
            print(f"均值: {np.mean(data):.4f}")
            print(f"标准差: {np.std(data):.4f}")
