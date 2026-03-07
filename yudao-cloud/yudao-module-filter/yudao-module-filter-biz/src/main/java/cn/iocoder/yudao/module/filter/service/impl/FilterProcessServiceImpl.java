package cn.iocoder.yudao.module.filter.service.impl;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.module.filter.api.algorithm.AdaptiveFilter;
import cn.iocoder.yudao.module.filter.algorithm.impl.LMSFilter;
import cn.iocoder.yudao.module.filter.algorithm.impl.NLMSFilter;
import cn.iocoder.yudao.module.filter.controller.admin.vo.FilterProcessReqVO;
import cn.iocoder.yudao.module.filter.controller.admin.vo.FilterProcessRespVO;
import cn.iocoder.yudao.module.filter.service.FilterProcessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 滤波器处理服务实现
 * 
 * @author yudao
 */
@Slf4j
@Service
public class FilterProcessServiceImpl implements FilterProcessService {
    
    @Resource
    private LMSFilter lmsFilter;
    
    @Resource
    private NLMSFilter nlmsFilter;
    
    private final Map<String, AdaptiveFilter> filterMap = new HashMap<>();
    
    @Override
    public FilterProcessRespVO processSignal(FilterProcessReqVO reqVO) {
        try {
            // 获取滤波器实例
            AdaptiveFilter filter = getFilterInstance(reqVO.getFilterType());
            
            // 初始化滤波器
            filter.initialize(reqVO.getFilterOrder(), reqVO.getStepSize());
            
            // 处理信号
            double[] outputSignal = filter.processSignal(reqVO.getInputSignal(), reqVO.getDesiredSignal());
            
            // 构建返回结果
            FilterProcessRespVO respVO = new FilterProcessRespVO();
            respVO.setOutputSignal(outputSignal);
            respVO.setWeights(filter.getWeights());
            respVO.setFinalError(filter.getCurrentError());
            respVO.setFilterInfo(getFilterInfo(reqVO.getFilterType()));
            respVO.setProcessTime(System.currentTimeMillis());
            
            log.info("信号处理完成 - 类型: {}, 输入长度: {}, 输出长度: {}", 
                    reqVO.getFilterType(), reqVO.getInputSignal().length, outputSignal.length);
            
            return respVO;

        } catch (Exception e) {
            log.error("信号处理失败", e);
            throw ServiceExceptionUtil.exception(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR,
                    "信号处理失败: " + e.getMessage());
        }
    }
    
    @Override
    public String getFilterInfo(String filterType) {
        AdaptiveFilter filter = getFilterInstance(filterType);
        if (filter instanceof LMSFilter) {
            return ((LMSFilter) filter).getFilterInfo();
        } else if (filter instanceof NLMSFilter) {
            return ((NLMSFilter) filter).getFilterInfo();
        }
        return "未知滤波器类型";
    }
    
    @Override
    public void resetFilter(String filterType) {
        AdaptiveFilter filter = getFilterInstance(filterType);
        filter.reset();
        log.info("滤波器已重置 - 类型: {}", filterType);
    }
    
    /**
     * 获取滤波器实例
     */
    private AdaptiveFilter getFilterInstance(String filterType) {
        switch (filterType.toUpperCase()) {
            case "LMS":
                return lmsFilter;
            case "NLMS":
                return nlmsFilter;
            default:
                throw new IllegalArgumentException("不支持的滤波器类型: " + filterType);
        }
    }
    
    /**
     * 生成测试信号
     */
    public double[] generateTestSignal(int length, double[] frequencies, double noiseLevel) {
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
            sample += noiseLevel * Math.random();
            signal[i] = sample;
        }
        
        return signal;
    }
    
    /**
     * 生成期望信号（延迟版本或清洁版本）
     */
    public double[] generateDesiredSignal(double[] inputSignal, int delay) {
        double[] desired = new double[inputSignal.length];
        
        for (int i = 0; i < inputSignal.length; i++) {
            if (i >= delay) {
                desired[i] = inputSignal[i - delay];
            } else {
                desired[i] = 0.0;
            }
        }
        
        return desired;
    }
}
