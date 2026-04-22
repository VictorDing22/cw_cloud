"""
滤波服务性能测试
测试不同滤波算法的处理速度
"""

import sys
import time
import numpy as np

# 检查依赖
try:
    from scipy import signal as scipy_signal
except ImportError:
    print("请安装 scipy: pip3 install scipy")
    sys.exit(1)

# 导入滤波器类
sys.path.insert(0, '.')
from high_speed_filter_service import LMSFilter, KalmanFilter, LowPassFilter, BandPassFilter

def test_filter_performance(filter_class, filter_name, signal_data, **kwargs):
    """测试单个滤波器的性能"""
    filter_instance = filter_class(**kwargs)
    
    # 预热
    _ = filter_instance.filter_batch(signal_data[:1000])
    
    # 正式测试
    iterations = 10
    total_time = 0
    total_samples = 0
    
    for _ in range(iterations):
        start = time.perf_counter()
        _ = filter_instance.filter_batch(signal_data)
        elapsed = time.perf_counter() - start
        total_time += elapsed
        total_samples += len(signal_data)
    
    avg_time = total_time / iterations
    samples_per_sec = total_samples / total_time
    
    return {
        'name': filter_name,
        'avg_time_ms': avg_time * 1000,
        'samples_per_sec': samples_per_sec,
        'samples_per_sec_k': samples_per_sec / 1000,
    }


def main():
    print("=" * 60)
    print("  滤波算法性能测试")
    print("=" * 60)
    
    # 生成测试信号
    sample_sizes = [1000, 5000, 10000, 50000]
    
    for size in sample_sizes:
        print(f"\n测试信号大小: {size:,} 样本")
        print("-" * 50)
        
        # 生成带噪声的正弦波
        t = np.linspace(0, 1, size)
        signal_data = np.sin(2 * np.pi * 100 * t) + 0.5 * np.random.randn(size)
        
        results = []
        
        # 测试 LMS
        result = test_filter_performance(
            LMSFilter, "LMS", signal_data,
            order=32, mu=0.01
        )
        results.append(result)
        
        # 测试 Kalman
        result = test_filter_performance(
            KalmanFilter, "Kalman", signal_data,
            q=0.001, r=0.1
        )
        results.append(result)
        
        # 测试 LowPass
        result = test_filter_performance(
            LowPassFilter, "LowPass", signal_data,
            cutoff=1000, sample_rate=50000
        )
        results.append(result)
        
        # 测试 BandPass
        result = test_filter_performance(
            BandPassFilter, "BandPass", signal_data,
            low_cutoff=100, high_cutoff=5000, sample_rate=50000
        )
        results.append(result)
        
        # 打印结果
        print(f"{'算法':<12} {'处理时间(ms)':<15} {'速率(K/s)':<15}")
        print("-" * 50)
        for r in results:
            print(f"{r['name']:<12} {r['avg_time_ms']:<15.2f} {r['samples_per_sec_k']:<15.1f}")
    
    print("\n" + "=" * 60)
    print("  测试完成")
    print("=" * 60)
    print("\n建议:")
    print("  - LMS: 适合自适应去噪，速度中等")
    print("  - Kalman: 适合平滑信号，速度较快")
    print("  - LowPass/BandPass: 使用 scipy，速度最快")


if __name__ == '__main__':
    main()
