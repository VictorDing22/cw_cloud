package cn.iocoder.yudao.module.monitor.api.dto;

/**
 * 滤波器类型。
 */
public enum FilterType {
    /**
     * LMS 自适应滤波
     */
    LMS,
    /**
     * 线性卡尔曼滤波（1D，随机游走模型）
     */
    KALMAN
}

