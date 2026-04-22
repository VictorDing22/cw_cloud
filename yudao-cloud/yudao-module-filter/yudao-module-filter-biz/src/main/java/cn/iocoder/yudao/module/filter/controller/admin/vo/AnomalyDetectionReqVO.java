package cn.iocoder.yudao.module.filter.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "管理后台 - 异常检测 Request VO")
@Data
public class AnomalyDetectionReqVO {

    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEVICE_001")
    @NotEmpty(message = "设备ID不能为空")
    private String deviceId;

    @Schema(description = "传感器类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "振动传感器")
    @NotEmpty(message = "传感器类型不能为空")
    private String sensorType;

    @Schema(description = "原始信号数据", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "原始信号数据不能为空")
    private List<Double> originalSignal;

    @Schema(description = "滤波后信号数据", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "滤波后信号数据不能为空")
    private List<Double> filteredSignal;

    @Schema(description = "检测配置")
    private DetectionConfig config;

    @Schema(description = "检测配置")
    @Data
    public static class DetectionConfig {

        @Schema(description = "幅值异常阈值倍数", example = "3.0")
        private Double amplitudeThresholdMultiplier = 3.0;

        @Schema(description = "趋势变化敏感度", example = "2.0")
        private Double trendSensitivity = 2.0;

        @Schema(description = "频域异常检测开关", example = "true")
        private Boolean enableFrequencyDetection = true;

        @Schema(description = "信号质量评估开关", example = "true")
        private Boolean enableQualityAssessment = true;
    }
}


