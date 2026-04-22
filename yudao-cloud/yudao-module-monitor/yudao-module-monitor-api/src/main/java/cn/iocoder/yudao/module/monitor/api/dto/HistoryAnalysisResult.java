package cn.iocoder.yudao.module.monitor.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * TDMS 离线历史分析结果。
 */
@Data
public class HistoryAnalysisResult implements Serializable {

    private TdmsChannelMetadata channel;

    private List<Point> points;

    private long anomalyCount;

    @Data
    public static class Point implements Serializable {
        private double timestamp;
        private double rawValue;
        private double filteredValue;
        private double residualValue;
        private boolean isAnomaly;
        private String anomalyType;
        private String channelName;
    }
}

