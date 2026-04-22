package cn.iocoder.yudao.module.filter.algorithm.impl;

import cn.iocoder.yudao.module.filter.api.algorithm.AdaptiveFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * NLMS (Normalized Least Mean Squares) 自适应滤波器实现
 * 对数归一化版本 - 基于MATLAB算法2
 * 
 * @author yudao
 */
@Slf4j
@Component
public class NLMSFilter implements AdaptiveFilter {
    
    private int filterOrder;        // 滤波器阶数 K
    private double baseMu;          // 基础步长
    private double[] weights;       // 权重向量 w
    private double[] inputBuffer;   // 输入缓冲区
    private int bufferIndex;        // 缓冲区索引
    private double currentError;    // 当前误差
    private double ipsi = 0.01;     // 小常数，防止除零
    
    @Override
    public void initialize(int filterOrder, double stepSize) {
        this.filterOrder = filterOrder;
        this.baseMu = stepSize;
        this.weights = new double[filterOrder];
        this.inputBuffer = new double[filterOrder];
        this.bufferIndex = 0;
        this.currentError = 0.0;
        
        // 初始化权重为0
        Arrays.fill(weights, 0.0);
        Arrays.fill(inputBuffer, 0.0);
        
        log.info("NLMS滤波器初始化完成 - 阶数: {}, 基础步长: {}", filterOrder, stepSize);
    }
    
    @Override
    public double process(double input, double desired) {
        // 更新输入缓冲区（环形缓冲区）
        inputBuffer[bufferIndex] = input;
        bufferIndex = (bufferIndex + 1) % filterOrder;
        
        // 计算滤波器输出 y(i) = w * XN'
        double output = 0.0;
        for (int i = 0; i < filterOrder; i++) {
            int index = (bufferIndex - 1 - i + filterOrder) % filterOrder;
            output += weights[i] * inputBuffer[index];
        }
        
        // 计算误差 e(i) = desired - y(i)
        currentError = desired - output;
        
        // 对数归一化自适应步长计算
        // mu = 0.001*log10(10*power(abs(e(i)),2));
        double adaptiveMu = baseMu * Math.log10(10 * Math.pow(Math.abs(currentError), 2));
        
        // 计算输入向量的范数
        double inputNorm = 0.0;
        for (int i = 0; i < filterOrder; i++) {
            int index = (bufferIndex - 1 - i + filterOrder) % filterOrder;
            inputNorm += Math.pow(inputBuffer[index], 2);
        }
        inputNorm = Math.sqrt(inputNorm);
        
        // 归一化步长 aux = mu/(ipsi+norm(XN))
        double normalizedStepSize = adaptiveMu / (ipsi + inputNorm);
        
        // 更新权重 w = w + ((aux*e(i))*XN)
        for (int i = 0; i < filterOrder; i++) {
            int index = (bufferIndex - 1 - i + filterOrder) % filterOrder;
            weights[i] += normalizedStepSize * currentError * inputBuffer[index];
        }
        
        return output;
    }
    
    @Override
    public double[] processSignal(double[] inputSignal, double[] desiredSignal) {
        if (inputSignal.length != desiredSignal.length) {
            throw new IllegalArgumentException("输入信号和期望信号长度必须相同");
        }
        
        int signalLength = inputSignal.length;
        double[] output = new double[signalLength];
        
        // 逐样本处理
        for (int i = 0; i < signalLength; i++) {
            output[i] = process(inputSignal[i], desiredSignal[i]);
        }
        
        log.info("NLMS信号处理完成 - 信号长度: {}, 最终误差: {}", signalLength, currentError);
        return output;
    }
    
    @Override
    public double[] getWeights() {
        return Arrays.copyOf(weights, weights.length);
    }
    
    @Override
    public void reset() {
        Arrays.fill(weights, 0.0);
        Arrays.fill(inputBuffer, 0.0);
        bufferIndex = 0;
        currentError = 0.0;
        log.info("NLMS滤波器已重置");
    }
    
    @Override
    public double getCurrentError() {
        return currentError;
    }
    
    /**
     * 设置小常数ipsi
     */
    public void setIpsi(double ipsi) {
        this.ipsi = ipsi;
    }
    
    /**
     * 获取滤波器配置信息
     */
    public String getFilterInfo() {
        return String.format("NLMS滤波器 - 阶数: %d, 基础步长: %.6f, 当前误差: %.6f, ipsi: %.6f", 
                           filterOrder, baseMu, currentError, ipsi);
    }
}
