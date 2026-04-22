package cn.iocoder.yudao.module.filter.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.filter.controller.admin.vo.AnomalyDetectionReqVO;
import cn.iocoder.yudao.module.filter.controller.admin.vo.AnomalyDetectionRespVO;
import cn.iocoder.yudao.module.filter.service.AnomalyDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 异常检测")
@RestController
@RequestMapping("/filter-api/anomaly")
@Validated
@Slf4j
public class AnomalyDetectionController {

    @Resource
    private AnomalyDetectionService anomalyDetectionService;

    @PostMapping("/detect")
    @Operation(summary = "执行异常检测")
    public CommonResult<AnomalyDetectionRespVO> detectAnomaly(@Valid @RequestBody AnomalyDetectionReqVO reqVO) {
        log.info("收到异常检测请求 - 设备: {}, 传感器: {}, 数据长度: {}", 
                reqVO.getDeviceId(), reqVO.getSensorType(), reqVO.getOriginalSignal().size());
        
        AnomalyDetectionRespVO respVO = anomalyDetectionService.detectAnomaly(reqVO);
        
        log.info("异常检测完成 - 设备: {}, 异常状态: {}, 异常分数: {}", 
                reqVO.getDeviceId(), respVO.getHasAnomaly(), respVO.getAnomalyScore());
        
        return success(respVO);
    }

    @GetMapping("/health")
    @Operation(summary = "异常检测服务健康检查")
    public CommonResult<String> healthCheck() {
        return success("异常检测服务运行正常");
    }
}


