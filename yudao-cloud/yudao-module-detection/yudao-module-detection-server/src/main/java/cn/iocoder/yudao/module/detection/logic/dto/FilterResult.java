package cn.iocoder.yudao.module.detection.logic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Filter result DTO containing processed data, anomaly status, and statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private long timestamp;
    private double originalValue;
    private double filteredValue;
    private boolean anomaly;
    private double energy;
    
    // Statistics
    private double snrBeforeDb;
    private double snrAfterDb;
    private double snrDeltaDb;
    
    // Throughput
    private double throughputKps;
    private double throughputMbps;
    
    private long anomalyCount;
}
