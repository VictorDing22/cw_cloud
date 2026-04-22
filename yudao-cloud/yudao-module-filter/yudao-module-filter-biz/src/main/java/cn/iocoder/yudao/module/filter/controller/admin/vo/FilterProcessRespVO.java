package cn.iocoder.yudao.module.filter.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 滤波器处理响应 VO
 *
 * @author yudao
 */
@Schema(description = "管理后台 - 滤波器处理响应 VO")
@Data
public class FilterProcessRespVO {

    @Schema(description = "处理后的输出信号")
    private double[] outputSignal;

    @Schema(description = "滤波器权重向量")
    private double[] weights;

    @Schema(description = "最终误差")
    private Double finalError;

    @Schema(description = "滤波器信息")
    private String filterInfo;

    @Schema(description = "处理时间戳")
    private Long processTime;

    @Schema(description = "处理状态", example = "SUCCESS")
    private String status = "SUCCESS";

    @Schema(description = "消息", example = "信号处理完成")
    private String message = "信号处理完成";
}
