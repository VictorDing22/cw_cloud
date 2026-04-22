package cn.iocoder.yudao.module.monitor.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * TDMS 单点采样。
 *
 * 注意：需要实现 {@link Serializable}，以便 Flink 在闭包清理和分发时进行序列化，
 * 否则会出现 NotSerializableException，导致作业启动失败。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TdmsSample implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * TDMS 中的原始时间戳（毫秒或纳秒换算为毫秒）。
     */
    private long timestamp;

    /**
     * 幅值。
     */
    private double value;

    /**
     * 通道名称。
     */
    private String channel;
}
