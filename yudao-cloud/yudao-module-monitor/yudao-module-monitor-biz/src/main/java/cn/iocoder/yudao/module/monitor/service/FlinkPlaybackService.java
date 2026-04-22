package cn.iocoder.yudao.module.monitor.service;

import cn.iocoder.yudao.module.monitor.service.dto.ParsedTdmsData;
import cn.iocoder.yudao.module.monitor.api.dto.FilterConfig;

public interface FlinkPlaybackService {

    /**
     * 启动基于 TDMS 的回放式实时流。
     *
     * @param jobId 任务 ID
     * @param data  解析后的 TDMS 数据
     * @param anomalyThreshold 能量阈值
     * @param anomalyEnabled 是否启用异常检测
     * @param filterConfig 滤波配置（LMS / Kalman）
     */
    void startJob(String jobId, ParsedTdmsData data, double anomalyThreshold, boolean anomalyEnabled, FilterConfig filterConfig);

    void stopJob(String jobId);

    void updateAnomalyConfig(String jobId, double threshold, boolean enabled);

    void updateFilterConfig(String jobId, FilterConfig filterConfig);
}
