package cn.iocoder.yudao.module.filter.service;

import cn.iocoder.yudao.module.filter.controller.admin.vo.FilterProcessReqVO;
import cn.iocoder.yudao.module.filter.controller.admin.vo.FilterProcessRespVO;

/**
 * 滤波器处理服务接口
 * 
 * @author yudao
 */
public interface FilterProcessService {
    
    /**
     * 处理信号数据
     * 
     * @param reqVO 请求参数
     * @return 处理结果
     */
    FilterProcessRespVO processSignal(FilterProcessReqVO reqVO);
    
    /**
     * 获取滤波器配置信息
     * 
     * @param filterType 滤波器类型
     * @return 配置信息
     */
    String getFilterInfo(String filterType);
    
    /**
     * 重置滤波器
     * 
     * @param filterType 滤波器类型
     */
    void resetFilter(String filterType);
}
