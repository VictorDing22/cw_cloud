package cn.iocoder.yudao.module.monitor.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 滤波配置（用于历史分析与实时回放）。
 *
 * <p>注意：实时回放基于 Flink，配置需要可序列化。</p>
 */
@Data
public class FilterConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 滤波器类型
     */
    private FilterType type = FilterType.KALMAN;

    /**
     * 卡尔曼过程噪声协方差 Q
     */
    private double kalmanQ = 1e-5;

    /**
     * 卡尔曼观测噪声协方差 R
     */
    private double kalmanR = 0.1;

    /**
     * 卡尔曼初始估计误差协方差 P0
     */
    private double kalmanP0 = 1.0;

    /**
     * 初始状态 x0：取前 N 点均值（N=10）
     */
    private int kalmanX0N = 10;
}

