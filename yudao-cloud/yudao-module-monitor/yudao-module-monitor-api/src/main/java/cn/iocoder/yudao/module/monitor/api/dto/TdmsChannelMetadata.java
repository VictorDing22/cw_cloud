package cn.iocoder.yudao.module.monitor.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * TDMS 通道的元数据。
 *
 * <p>作为 Flink 流中的字段，需要实现 {@link java.io.Serializable}。</p>
 */
@Data
public class TdmsChannelMetadata implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 通道名称。
     */
    private String name;

    /**
     * 单位（例如 Voltage (V)）。
     */
    private String unit;

    /**
     * 采样率（Hz）。
     */
    private Double sampleRate;

    /**
     * 起始时间戳（毫秒，来自 TDMS 原始时间基准）。
     */
    private Long startTimestamp;

    /**
     * 结束时间戳（毫秒，来自 TDMS 原始时间基准）。
     */
    private Long endTimestamp;

    /**
     * 样本总数。
     */
    private Long sampleCount;
}
