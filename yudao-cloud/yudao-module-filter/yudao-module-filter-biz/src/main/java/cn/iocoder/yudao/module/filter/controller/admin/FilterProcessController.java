package cn.iocoder.yudao.module.filter.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.filter.controller.admin.vo.FilterProcessReqVO;
import cn.iocoder.yudao.module.filter.controller.admin.vo.FilterProcessRespVO;
import cn.iocoder.yudao.module.filter.service.FilterProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 自适应滤波器处理
 *
 * @author yudao
 */
@Tag(name = "管理后台 - 自适应滤波器")
@RestController
@RequestMapping("/filter/adaptive")
@Validated
@Slf4j
public class FilterProcessController {

    @Resource
    private FilterProcessService filterProcessService;

    @PostMapping("/process")
    @Operation(summary = "处理信号数据")
    public CommonResult<FilterProcessRespVO> processSignal(@Valid @RequestBody FilterProcessReqVO reqVO) {
        FilterProcessRespVO respVO = filterProcessService.processSignal(reqVO);
        return success(respVO);
    }

    @GetMapping("/info")
    @Operation(summary = "获取滤波器配置信息")
    public CommonResult<String> getFilterInfo(@Parameter(description = "滤波器类型") @RequestParam String filterType) {
        String info = filterProcessService.getFilterInfo(filterType);
        return success(info);
    }

    @PostMapping("/reset")
    @Operation(summary = "重置滤波器")
    public CommonResult<Boolean> resetFilter(@Parameter(description = "滤波器类型") @RequestParam String filterType) {
        filterProcessService.resetFilter(filterType);
        return success(true);
    }

    @PostMapping("/test")
    @Operation(summary = "测试滤波器功能")
    public CommonResult<FilterProcessRespVO> testFilter(@RequestParam(defaultValue = "LMS") String filterType) {
        // 生成测试数据
        FilterProcessReqVO reqVO = new FilterProcessReqVO();
        reqVO.setFilterType(filterType);
        reqVO.setFilterOrder(32);
        reqVO.setStepSize(0.01);
        
        // 创建测试信号
        int signalLength = 1000;
        double[] frequencies = {100.0, 200.0, 300.0}; // Hz
        double noiseLevel = 0.3;
        
        // 生成含噪声的输入信号
        double[] inputSignal = generateTestSignal(signalLength, frequencies, noiseLevel);
        
        // 生成清洁的期望信号
        double[] desiredSignal = generateTestSignal(signalLength, frequencies, 0.0);
        
        reqVO.setInputSignal(inputSignal);
        reqVO.setDesiredSignal(desiredSignal);
        reqVO.setRemark("滤波器功能测试");
        
        FilterProcessRespVO respVO = filterProcessService.processSignal(reqVO);
        return success(respVO);
    }

    /**
     * 生成测试信号
     */
    private double[] generateTestSignal(int length, double[] frequencies, double noiseLevel) {
        double[] signal = new double[length];
        double fs = 1200; // 采样频率
        
        for (int i = 0; i < length; i++) {
            double t = (double) i / fs;
            double sample = 0.0;
            
            // 叠加多个频率分量
            for (double freq : frequencies) {
                sample += Math.sin(2 * Math.PI * freq * t);
            }
            
            // 添加噪声
            if (noiseLevel > 0) {
                sample += noiseLevel * (Math.random() - 0.5) * 2;
            }
            signal[i] = sample;
        }
        
        return signal;
    }
}
