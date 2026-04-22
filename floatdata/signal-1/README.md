# Signal-1 数据说明

## 📊 数据信息

**文件**: `ae_sim_2s.tdms`

### 信号参数
- **采样率**: 100,000 Hz (100 kHz)
- **信号频率**: 5 kHz (正弦波)
- **信号幅值**: 1.0
- **信噪比 (SNR)**: 10 dB
- **时长**: 2 秒

### TDMS 通道
1. `time_s` - 时间轴（秒）
2. `sine` - 原始纯净正弦波信号
3. `noise` - 纯噪声信号
4. `sine_plus_noise` - 混合信号（原始信号 + 噪声）

## 🔧 使用方法

### 运行信号滤波可视化脚本

```bash
cd e:\Code\CW_Cloud\floatdata\floatdata-streaming
python signal-filter-visualizer.py
```

### 输出结果

脚本会生成两张图片：

1. **signal_filter_comparison.png** - 时域分析
   - 子图1: 原始信号
   - 子图2: 加噪信号
   - 子图3: 滤波后信号
   - 子图4: 原始 vs 滤波后（叠加对比）

2. **frequency_spectrum_comparison.png** - 频域分析
   - 子图1: 原始信号频谱
   - 子图2: 加噪信号频谱
   - 子图3: 滤波后信号频谱

### 性能指标

脚本会输出：
- 均方误差（MSE）改善百分比
- 相关系数变化
- 滤波前后对比

## 📝 数据用途

此数据用于演示：
- 信号处理流程
- 低通滤波器效果
- 噪声抑制能力
- 信号恢复质量
