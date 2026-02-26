package cn.iocoder.yudao.module.detection.framework;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 检测模块配置项：用于性能调优
 */
@Data
@Component
@ConfigurationProperties(prefix = "yudao.detection")
public class DetectionProperties {

    /**
     * Netty 接入端口
     */
    private Integer nettyPort = 9999;

    /**
     * Flink 数据提供端口
     */
    private Integer dataPort = 9998;

    /**
     * Flink 并行度
     */
    private Integer flinkParallelism = 1;

    /**
     * 滤波算法窗口大小 (ms)
     */
    private Long filterWindowMs = 5000L;

    /**
     * 异常检测阈值 (Sigma)
     */
    private Double anomalyThreshold = 3.0;

    /**
     * TDengine 批量写入大小
     */
    private Integer tdengineBatchSize = 1000;

    /**
     * TDengine 批量写入间隔 (ms)
     */
    private Integer tdengineBatchInterval = 200;
}
