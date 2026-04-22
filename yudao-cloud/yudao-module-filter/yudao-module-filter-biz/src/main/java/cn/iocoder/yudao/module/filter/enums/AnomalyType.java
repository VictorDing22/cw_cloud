package cn.iocoder.yudao.module.filter.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 异常类型枚举
 *
 * @author yudao
 */
@Getter
@AllArgsConstructor
public enum AnomalyType {

    AMPLITUDE_ANOMALY("幅值异常", "信号幅值超出正常范围"),
    TREND_ANOMALY("趋势异常", "信号趋势发生突变"),
    FREQUENCY_ANOMALY("频域异常", "信号频率特征异常"),
    SIGNAL_QUALITY("信号质量", "信号质量评估异常"),
    SYSTEM_ANOMALY("系统异常", "系统运行异常");

    /**
     * 异常类型名称
     */
    private final String name;

    /**
     * 异常描述
     */
    private final String description;
}


