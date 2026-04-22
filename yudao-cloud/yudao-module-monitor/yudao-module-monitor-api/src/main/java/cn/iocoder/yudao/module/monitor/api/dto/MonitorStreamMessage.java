package cn.iocoder.yudao.module.monitor.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 推送到前端的实时处理结果。
 *
 * <p>必须实现 {@link java.io.Serializable}，否则 Flink 在分发算子、做 checkpoint 时
 * 会抛出 NotSerializableException，导致作业启动失败，从而返回 500。</p>
 * 
 * <p>注意：为了兼容 Flink 的 POJO 序列化（避免 Kryo），需要：
 * 1. 提供 public 无参构造函数（@NoArgsConstructor）
 * 2. 所有字段有 public getter/setter（Lombok @Data 会自动生成）
 * 3. 避免使用不可变集合（如 Arrays.asList()）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitorStreamMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String jobId;

    private long timestamp;

    private double originalValue;

    private double filteredValue;

    private boolean anomaly;

    private double energy;

    private double snrBeforeDb;

    private double snrAfterDb;

    private double snrDeltaDb;

    private double throughputKps;

    private double processingDelayMs;

    private long anomalyCount;

    private TdmsChannelMetadata channel;
}
