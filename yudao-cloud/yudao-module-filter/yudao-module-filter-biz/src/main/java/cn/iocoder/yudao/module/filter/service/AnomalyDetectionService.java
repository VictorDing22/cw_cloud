package cn.iocoder.yudao.module.filter.service;

import cn.iocoder.yudao.module.filter.controller.admin.vo.AnomalyDetectionReqVO;
import cn.iocoder.yudao.module.filter.controller.admin.vo.AnomalyDetectionRespVO;

/**
 * 异常检测服务接口
 *
 * @author yudao
 */
public interface AnomalyDetectionService {

    /**
     * 执行异常检测
     *
     * @param reqVO 异常检测请求参数
     * @return 异常检测结果
     */
    AnomalyDetectionRespVO detectAnomaly(AnomalyDetectionReqVO reqVO);

    /**
     * 批量异常检测
     *
     * @param originalSignal 原始信号
     * @param filteredSignal 滤波后信号
     * @param deviceId 设备ID
     * @param sensorType 传感器类型
     * @return 异常检测结果
     */
    AnomalyDetectionRespVO batchDetectAnomaly(double[] originalSignal, 
                                            double[] filteredSignal,
                                            String deviceId,
                                            String sensorType);
}

