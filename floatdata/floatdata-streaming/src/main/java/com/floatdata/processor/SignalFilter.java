package com.floatdata.processor;

import com.floatdata.utils.ConfigLoader;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 信号滤波器 - 实现 Butterworth 带通滤波
 */
public class SignalFilter {
    private static final Logger logger = LoggerFactory.getLogger(SignalFilter.class);
    private final int filterOrder;
    private final double cutoffLow;
    private final double cutoffHigh;
    private final int sampleRate;
    private final FastFourierTransformer fft;

    public SignalFilter() {
        this.filterOrder = ConfigLoader.getInt("signal.filter.order", 4);
        this.cutoffLow = ConfigLoader.getDouble("signal.filter.cutoff.low", 10000);
        this.cutoffHigh = ConfigLoader.getDouble("signal.filter.cutoff.high", 500000);
        this.sampleRate = ConfigLoader.getInt("signal.sample.rate", 1000000);
        this.fft = new FastFourierTransformer(org.apache.commons.math3.transform.DftNormalization.STANDARD);
        
        logger.info("信号滤波器初始化: order={}, cutoff=[{}, {}] Hz, sampleRate={} Hz",
                filterOrder, cutoffLow, cutoffHigh, sampleRate);
    }

    /**
     * 应用 Butterworth 带通滤波
     */
    public float[] applyButterworthFilter(float[] signal) {
        if (signal == null || signal.length == 0) {
            return signal;
        }

        // 1. 计算归一化频率
        double wLow = 2.0 * Math.PI * cutoffLow / sampleRate;
        double wHigh = 2.0 * Math.PI * cutoffHigh / sampleRate;

        // 2. 计算滤波器系数 (简化的 Butterworth 实现)
        double[] b = new double[filterOrder + 1];
        double[] a = new double[filterOrder + 1];
        
        // 使用双线性变换计算系数
        calculateButterworthCoefficients(wLow, wHigh, b, a);

        // 3. 应用 IIR 滤波 (级联二阶节)
        float[] filtered = new float[signal.length];
        System.arraycopy(signal, 0, filtered, 0, signal.length);
        
        for (int stage = 0; stage < filterOrder / 2; stage++) {
            filtered = applyBiquadFilter(filtered, b, a);
        }

        return filtered;
    }

    /**
     * 计算 Butterworth 滤波器系数
     */
    private void calculateButterworthCoefficients(double wLow, double wHigh, 
                                                  double[] b, double[] a) {
        // 简化实现：使用预计算的系数
        // 实际应用中应使用更完整的 Butterworth 设计算法
        
        double wc = Math.sqrt(wLow * wHigh);  // 中心频率
        double bw = wHigh - wLow;              // 带宽
        
        // 二阶 Butterworth 系数
        double q = wc / bw;
        double alpha = Math.sin(wc) / (2 * q);
        
        b[0] = (1 - Math.cos(wc)) / 2;
        b[1] = 1 - Math.cos(wc);
        b[2] = (1 - Math.cos(wc)) / 2;
        
        a[0] = 1 + alpha;
        a[1] = -2 * Math.cos(wc);
        a[2] = 1 - alpha;
    }

    /**
     * 应用二阶 IIR 滤波器 (Biquad)
     */
    private float[] applyBiquadFilter(float[] signal, double[] b, double[] a) {
        float[] output = new float[signal.length];
        double[] x = new double[3];  // 输入历史
        double[] y = new double[3];  // 输出历史

        for (int n = 0; n < signal.length; n++) {
            x[0] = signal[n];
            
            // 差分方程: y[n] = (b0*x[n] + b1*x[n-1] + b2*x[n-2] - a1*y[n-1] - a2*y[n-2]) / a0
            double yn = (b[0] * x[0] + b[1] * x[1] + b[2] * x[2] 
                       - a[1] * y[1] - a[2] * y[2]) / a[0];
            
            output[n] = (float) yn;
            
            // 更新历史
            x[2] = x[1];
            x[1] = x[0];
            y[2] = y[1];
            y[1] = yn;
        }

        return output;
    }

    /**
     * 计算信号能量
     */
    public double calculateEnergy(float[] signal) {
        if (signal == null || signal.length == 0) {
            return 0.0;
        }
        
        double energy = 0.0;
        for (float sample : signal) {
            energy += sample * sample;
        }
        return Math.sqrt(energy / signal.length);
    }

    /**
     * 计算频域特征 (使用 FFT)
     */
    public double[] calculateFrequencyFeatures(float[] signal) {
        if (signal == null || signal.length == 0) {
            return new double[0];
        }

        try {
            // 补零到 2 的幂次
            int fftSize = 1;
            while (fftSize < signal.length) {
                fftSize *= 2;
            }
            
            double[] input = new double[fftSize];
            for (int i = 0; i < signal.length; i++) {
                input[i] = signal[i];
            }

            // 执行 FFT
            Complex[] fftResult = fft.transform(input, org.apache.commons.math3.transform.TransformType.FORWARD);

            // 计算幅度谱
            double[] magnitude = new double[fftResult.length / 2];
            for (int i = 0; i < magnitude.length; i++) {
                magnitude[i] = fftResult[i].abs();
            }

            return magnitude;
        } catch (Exception e) {
            logger.error("FFT 计算失败", e);
            return new double[0];
        }
    }

    /**
     * 计算频率评分 (基于能量分布)
     */
    public double calculateFrequencyScore(double[] frequencyMagnitude) {
        if (frequencyMagnitude == null || frequencyMagnitude.length == 0) {
            return 0.0;
        }

        // 计算高频能量占比
        int midPoint = frequencyMagnitude.length / 2;
        double lowFreqEnergy = 0.0;
        double highFreqEnergy = 0.0;

        for (int i = 0; i < midPoint; i++) {
            lowFreqEnergy += frequencyMagnitude[i];
        }
        for (int i = midPoint; i < frequencyMagnitude.length; i++) {
            highFreqEnergy += frequencyMagnitude[i];
        }

        double totalEnergy = lowFreqEnergy + highFreqEnergy;
        if (totalEnergy == 0) {
            return 0.0;
        }

        // 高频能量占比越高，异常可能性越大
        double score = highFreqEnergy / totalEnergy;
        return Math.min(score, 1.0);
    }
}
