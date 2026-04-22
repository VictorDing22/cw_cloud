package cn.iocoder.yudao.module.filter.algorithm.impl;

import cn.iocoder.yudao.module.filter.api.algorithm.AdaptiveFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * LMS (Least Mean Squares) 自适应滤波器实现
 * 基于MATLAB代码的Java版本
 * 
 * @author yudao
 */
@Slf4j
@Component
public class LMSFilter implements AdaptiveFilter {
    
    private int filterOrder;        // 滤波器阶数 K
    private double stepSize;        // 步长 u
    private double[] weights;       // 权重向量 w
    private double[] inputBuffer;   // 输入缓冲区
    private int bufferIndex;        // 缓冲区索引
    private double currentError;    // 当前误差
    
    @Override
    public void initialize(int filterOrder, double stepSize) {
        this.filterOrder = filterOrder;
        this.stepSize = stepSize;
        this.weights = new double[filterOrder];
        this.inputBuffer = new double[filterOrder];
        this.bufferIndex = 0;
        this.currentError = 0.0;
        
        // 初始化权重为0
        Arrays.fill(weights, 0.0);
        Arrays.fill(inputBuffer, 0.0);
        
        log.info("LMS滤波器初始化完成 - 阶数: {}, 步长: {}", filterOrder, stepSize);
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
        
        // 更新权重 - 符号LMS算法
        // if(e(i)>0) w=w+u*XN; else w=w-u*XN;
        for (int i = 0; i < filterOrder; i++) {
            int index = (bufferIndex - 1 - i + filterOrder) % filterOrder;
            if (currentError > 0) {
                weights[i] += stepSize * inputBuffer[index];
            } else {
                weights[i] -= stepSize * inputBuffer[index];
            }
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
        
        log.info("信号处理完成 - 信号长度: {}, 最终误差: {}", signalLength, currentError);
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
        log.info("LMS滤波器已重置");
    }
    
    @Override
    public double getCurrentError() {
        return currentError;
    }
    
    /**
     * 获取滤波器配置信息
     */
    public String getFilterInfo() {
        return String.format("LMS滤波器 - 阶数: %d, 步长: %.6f, 当前误差: %.6f", 
                           filterOrder, stepSize, currentError);
    }
}
