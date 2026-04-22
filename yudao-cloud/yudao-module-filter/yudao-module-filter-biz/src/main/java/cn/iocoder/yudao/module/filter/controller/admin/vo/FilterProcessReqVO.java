package cn.iocoder.yudao.module.filter.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

/**
 * 滤波器处理请求 VO
 *
 * @author yudao
 */
@Schema(description = "管理后台 - 滤波器处理请求 VO")
@Data
public class FilterProcessReqVO {

    @Schema(description = "滤波器类型", example = "LMS")
    @NotEmpty(message = "滤波器类型不能为空")
    private String filterType;

    @Schema(description = "滤波器阶数", example = "32")
    @NotNull(message = "滤波器阶数不能为空")
    @Positive(message = "滤波器阶数必须大于0")
    private Integer filterOrder;

    @Schema(description = "步长", example = "0.01")
    @NotNull(message = "步长不能为空")
    @Positive(message = "步长必须大于0")
    private Double stepSize;

    @Schema(description = "输入信号")
    @NotNull(message = "输入信号不能为空")
    private double[] inputSignal;

    @Schema(description = "期望信号")
    @NotNull(message = "期望信号不能为空")
    private double[] desiredSignal;

    @Schema(description = "备注", example = "实时故障检测信号处理")
    private String remark;
}
