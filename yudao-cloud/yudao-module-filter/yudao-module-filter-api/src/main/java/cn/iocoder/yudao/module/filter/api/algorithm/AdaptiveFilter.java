package cn.iocoder.yudao.module.filter.api.algorithm;

/**
 * 自适应滤波器接口
 * 
 * @author yudao
 */
public interface AdaptiveFilter {
    
    /**
     * 初始化滤波器
     * 
     * @param filterOrder 滤波器阶数
     * @param stepSize 步长
     */
    void initialize(int filterOrder, double stepSize);
    
    /**
     * 处理单个样本
     * 
     * @param input 输入样本
     * @param desired 期望输出（用于训练）
     * @return 滤波器输出
     */
    double process(double input, double desired);
    
    /**
     * 批量处理信号
     * 
     * @param inputSignal 输入信号数组
     * @param desiredSignal 期望信号数组
     * @return 滤波后的信号
     */
    double[] processSignal(double[] inputSignal, double[] desiredSignal);
    
    /**
     * 获取当前权重向量
     * 
     * @return 权重数组
     */
    double[] getWeights();
    
    /**
     * 重置滤波器状态
     */
    void reset();
    
    /**
     * 获取当前误差
     * 
     * @return 误差值
     */
    double getCurrentError();
}
