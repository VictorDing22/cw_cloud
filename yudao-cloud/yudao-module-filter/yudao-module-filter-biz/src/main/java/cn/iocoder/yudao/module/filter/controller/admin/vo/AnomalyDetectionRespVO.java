package cn.iocoder.yudao.module.filter.controller.admin.vo;

import cn.iocoder.yudao.module.filter.enums.AlertLevel;
import cn.iocoder.yudao.module.filter.enums.AnomalyType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 异常检测 Response VO")
@Data
public class AnomalyDetectionRespVO {

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "传感器类型")
    private String sensorType;

    @Schema(description = "检测时间")
    private LocalDateTime detectionTime;

    @Schema(description = "是否存在异常")
    private Boolean hasAnomaly;

    @Schema(description = "异常分数(0-1)")
    private Double anomalyScore;

    @Schema(description = "报警级别")
    private AlertLevel alertLevel;

    @Schema(description = "信噪比改善(dB)")
    private Double snrImprovement;

    @Schema(description = "信号质量评级")
    private String signalQuality;

    @Schema(description = "异常详情列表")
    private List<AnomalyDetail> anomalyList;

    @Schema(description = "处理建议")
    private String recommendation;

    @Schema(description = "异常详情")
    @Data
    public static class AnomalyDetail {

        @Schema(description = "异常类型")
        private AnomalyType type;

        @Schema(description = "异常描述")
        private String description;

        @Schema(description = "严重程度")
        private AlertLevel severity;

        @Schema(description = "异常位置索引")
        private List<Integer> anomalyIndices;

        @Schema(description = "检测阈值")
        private String threshold;

        @Schema(description = "置信度")
        private Double confidence;

        @Schema(description = "持续时间(样本点)")
        private Integer duration;
    }
}


