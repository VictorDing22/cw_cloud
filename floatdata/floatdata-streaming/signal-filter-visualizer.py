"""
信号处理与滤波效果可视化
功能：
1. 读取 TDMS 信号数据（原始信号、加噪信号）
2. 对加噪信号进行滤波处理
3. 可视化展示：原始信号、加噪信号、滤波信号
4. 对比滤波效果（原始 vs 滤波后）
"""

import numpy as np
import matplotlib.pyplot as plt
from scipy import signal
from nptdms import TdmsFile
import os

# 设置中文显示
plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei']
plt.rcParams['axes.unicode_minus'] = False


class SignalFilterVisualizer:
    """信号滤波可视化器"""
    
    def __init__(self, tdms_path, sample_rate=100000):
        """
        初始化
        
        Args:
            tdms_path: TDMS文件路径
            sample_rate: 采样率（Hz）
        """
        self.tdms_path = tdms_path
        self.sample_rate = sample_rate
        self.time = None
        self.sine_signal = None
        self.noise_signal = None
        self.noisy_signal = None
        self.filtered_signal = None
        
    def load_data(self):
        """加载TDMS数据"""
        print(f"📂 正在加载数据: {self.tdms_path}")
        
        tdms_file = TdmsFile.read(self.tdms_path)
        
        # 获取第一个组（通常是 'Group'）
        group = tdms_file.groups()[0]
        channels = group.channels()
        
        print(f"✅ 找到 {len(channels)} 个通道:")
        for ch in channels:
            print(f"   - {ch.name}")
        
        # 读取各通道数据
        for channel in channels:
            data = channel[:]
            
            if 'time' in channel.name.lower():
                self.time = data
            elif channel.name == 'sine' or 'sine' in channel.name.lower():
                self.sine_signal = data
            elif 'noise' in channel.name.lower() and 'plus' in channel.name.lower():
                self.noisy_signal = data
            elif 'noise' in channel.name.lower():
                self.noise_signal = data
        
        # 如果没有时间轴，创建一个
        if self.time is None and self.sine_signal is not None:
            self.time = np.arange(len(self.sine_signal)) / self.sample_rate
            
        print(f"✅ 数据加载完成！")
        print(f"   - 采样点数: {len(self.time)}")
        print(f"   - 时长: {self.time[-1]:.3f} 秒")
        
    def design_filter(self, cutoff_freq=10000, filter_type='lowpass', order=4):
        """
        设计数字滤波器
        
        Args:
            cutoff_freq: 截止频率（Hz）
            filter_type: 滤波器类型 ('lowpass', 'highpass', 'bandpass')
            order: 滤波器阶数
        """
        nyquist_freq = self.sample_rate / 2
        normalized_cutoff = cutoff_freq / nyquist_freq
        
        print(f"\n🔧 设计滤波器:")
        print(f"   - 类型: {filter_type}")
        print(f"   - 截止频率: {cutoff_freq} Hz")
        print(f"   - 阶数: {order}")
        
        # 设计巴特沃斯滤波器
        self.sos = signal.butter(order, normalized_cutoff, 
                                 btype=filter_type, output='sos')
        
    def apply_filter(self):
        """应用滤波器到加噪信号"""
        if self.noisy_signal is None:
            print("❌ 错误：没有加噪信号数据")
            return
            
        print("🔄 正在应用滤波器...")
        self.filtered_signal = signal.sosfilt(self.sos, self.noisy_signal)
        print("✅ 滤波完成！")
        
    def calculate_snr(self, signal_data, noise_data):
        """计算信噪比（SNR）"""
        signal_power = np.mean(signal_data ** 2)
        noise_power = np.mean(noise_data ** 2)
        snr_db = 10 * np.log10(signal_power / noise_power)
        return snr_db
    
    def calculate_metrics(self):
        """计算滤波性能指标"""
        if self.filtered_signal is None:
            return
            
        print("\n📊 滤波性能指标:")
        
        # 1. 均方误差（MSE）
        mse_before = np.mean((self.sine_signal - self.noisy_signal) ** 2)
        mse_after = np.mean((self.sine_signal - self.filtered_signal) ** 2)
        
        print(f"   - 滤波前 MSE: {mse_before:.6f}")
        print(f"   - 滤波后 MSE: {mse_after:.6f}")
        print(f"   - MSE 改善: {(1 - mse_after/mse_before) * 100:.2f}%")
        
        # 2. 相关系数
        corr_before = np.corrcoef(self.sine_signal, self.noisy_signal)[0, 1]
        corr_after = np.corrcoef(self.sine_signal, self.filtered_signal)[0, 1]
        
        print(f"   - 滤波前相关系数: {corr_before:.4f}")
        print(f"   - 滤波后相关系数: {corr_after:.4f}")
        
    def visualize_all(self, time_range=(0, 0.01)):
        """
        完整可视化：4个子图
        
        Args:
            time_range: 显示的时间范围（秒）
        """
        # 选择显示的时间范围
        start_idx = np.searchsorted(self.time, time_range[0])
        end_idx = np.searchsorted(self.time, time_range[1])
        
        t = self.time[start_idx:end_idx]
        
        fig, axes = plt.subplots(4, 1, figsize=(14, 12))
        fig.suptitle('信号处理与滤波效果分析', fontsize=16, fontweight='bold')
        
        # 1. 原始信号
        ax1 = axes[0]
        ax1.plot(t * 1000, self.sine_signal[start_idx:end_idx], 
                 'b-', linewidth=1.5, label='原始正弦波')
        ax1.set_title('① 原始信号（纯净的 5 kHz 正弦波）', fontsize=12, fontweight='bold')
        ax1.set_xlabel('时间 (ms)')
        ax1.set_ylabel('幅值')
        ax1.grid(True, alpha=0.3)
        ax1.legend()
        
        # 2. 加噪信号
        ax2 = axes[1]
        ax2.plot(t * 1000, self.noisy_signal[start_idx:end_idx], 
                 'r-', linewidth=0.8, alpha=0.7, label='加噪信号 (SNR=10dB)')
        ax2.set_title('② 加噪信号（原始信号 + 噪声）', fontsize=12, fontweight='bold')
        ax2.set_xlabel('时间 (ms)')
        ax2.set_ylabel('幅值')
        ax2.grid(True, alpha=0.3)
        ax2.legend()
        
        # 3. 滤波后信号
        ax3 = axes[2]
        ax3.plot(t * 1000, self.filtered_signal[start_idx:end_idx], 
                 'g-', linewidth=1.5, label='滤波后信号')
        ax3.set_title('③ 滤波后信号（经过低通滤波）', fontsize=12, fontweight='bold')
        ax3.set_xlabel('时间 (ms)')
        ax3.set_ylabel('幅值')
        ax3.grid(True, alpha=0.3)
        ax3.legend()
        
        # 4. 原始信号 vs 滤波后信号（叠加对比）
        ax4 = axes[3]
        ax4.plot(t * 1000, self.sine_signal[start_idx:end_idx], 
                 'b-', linewidth=2, alpha=0.7, label='原始信号')
        ax4.plot(t * 1000, self.filtered_signal[start_idx:end_idx], 
                 'g--', linewidth=1.5, alpha=0.8, label='滤波后信号')
        ax4.set_title('④ 滤波效果对比（原始 vs 滤波后）', fontsize=12, fontweight='bold')
        ax4.set_xlabel('时间 (ms)')
        ax4.set_ylabel('幅值')
        ax4.grid(True, alpha=0.3)
        ax4.legend()
        
        plt.tight_layout()
        
        # 保存图片
        output_path = os.path.join(os.path.dirname(self.tdms_path), 
                                   'signal_filter_comparison.png')
        plt.savefig(output_path, dpi=300, bbox_inches='tight')
        print(f"\n💾 图片已保存: {output_path}")
        
        plt.show()
        
    def visualize_frequency_domain(self):
        """频域分析：显示频谱对比"""
        fig, axes = plt.subplots(3, 1, figsize=(14, 10))
        fig.suptitle('频域分析（FFT 频谱）', fontsize=16, fontweight='bold')
        
        # 计算FFT
        n = len(self.sine_signal)
        freq = np.fft.fftfreq(n, 1/self.sample_rate)[:n//2]
        
        # 1. 原始信号频谱
        fft_sine = np.abs(np.fft.fft(self.sine_signal))[:n//2]
        axes[0].plot(freq / 1000, 20 * np.log10(fft_sine), 'b-', linewidth=1)
        axes[0].set_title('原始信号频谱', fontweight='bold')
        axes[0].set_xlabel('频率 (kHz)')
        axes[0].set_ylabel('幅度 (dB)')
        axes[0].grid(True, alpha=0.3)
        axes[0].set_xlim(0, 20)
        
        # 2. 加噪信号频谱
        fft_noisy = np.abs(np.fft.fft(self.noisy_signal))[:n//2]
        axes[1].plot(freq / 1000, 20 * np.log10(fft_noisy), 'r-', linewidth=1)
        axes[1].set_title('加噪信号频谱', fontweight='bold')
        axes[1].set_xlabel('频率 (kHz)')
        axes[1].set_ylabel('幅度 (dB)')
        axes[1].grid(True, alpha=0.3)
        axes[1].set_xlim(0, 20)
        
        # 3. 滤波后信号频谱
        fft_filtered = np.abs(np.fft.fft(self.filtered_signal))[:n//2]
        axes[2].plot(freq / 1000, 20 * np.log10(fft_filtered), 'g-', linewidth=1)
        axes[2].set_title('滤波后信号频谱', fontweight='bold')
        axes[2].set_xlabel('频率 (kHz)')
        axes[2].set_ylabel('幅度 (dB)')
        axes[2].grid(True, alpha=0.3)
        axes[2].set_xlim(0, 20)
        
        plt.tight_layout()
        
        # 保存图片
        output_path = os.path.join(os.path.dirname(self.tdms_path), 
                                   'frequency_spectrum_comparison.png')
        plt.savefig(output_path, dpi=300, bbox_inches='tight')
        print(f"💾 频谱图已保存: {output_path}")
        
        plt.show()


def main():
    """主函数"""
    print("=" * 60)
    print("🎯 信号处理与滤波效果可视化系统")
    print("=" * 60)
    
    # TDMS 文件路径
    tdms_path = r"e:\Code\CW_Cloud\floatdata\signal-1\ae_sim_2s.tdms"
    
    # 创建可视化器
    visualizer = SignalFilterVisualizer(tdms_path, sample_rate=100000)
    
    # 1. 加载数据
    visualizer.load_data()
    
    # 2. 设计滤波器（低通滤波器，截止频率 10 kHz）
    # 因为信号频率是 5 kHz，所以设置 10 kHz 截止可以保留信号，过滤高频噪声
    visualizer.design_filter(cutoff_freq=10000, filter_type='lowpass', order=6)
    
    # 3. 应用滤波器
    visualizer.apply_filter()
    
    # 4. 计算性能指标
    visualizer.calculate_metrics()
    
    # 5. 时域可视化（显示前 10ms）
    print("\n📊 生成时域对比图...")
    visualizer.visualize_all(time_range=(0, 0.01))
    
    # 6. 频域可视化
    print("\n📊 生成频域对比图...")
    visualizer.visualize_frequency_domain()
    
    print("\n" + "=" * 60)
    print("✅ 分析完成！")
    print("=" * 60)


if __name__ == "__main__":
    main()
