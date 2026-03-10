package cn.iocoder.yudao.module.detection.logic;

import cn.iocoder.yudao.module.detection.api.FilterAlgorithm;
import cn.iocoder.yudao.module.detection.api.dto.TdmsSample;
import cn.iocoder.yudao.module.detection.logic.dto.FilterResult;
import cn.iocoder.yudao.module.detection.logic.filter.FilterFactory;
import cn.iocoder.yudao.module.detection.logic.filter.FilterStrategy;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 通用滤波逻辑（无 Key 状态版）：
 * - 不依赖 keyBy / KeyedState，只在本地维护窗口
 * - 适合批处理单通道或少通道数据，减少状态和分区开销
 */
public class GenericFilterProcessFunction extends ProcessFunction<TdmsSample, FilterResult> {

    private final long windowMs;
    private final double anomalyThreshold;
    private final boolean anomalyEnabled;
    private final FilterAlgorithm algorithm;

    // 本地窗口与统计信息
    private transient Deque<WindowEntry> window;
    private transient long anomalyCount;
    private transient WindowStats stats;
    private transient FilterStrategy strategy;

    // 吞吐统计
    private transient long lastCheckTime;
    private transient long localProcessedCount;

    // 异常点上限控制
    private static final long MAX_ANOMALY_COUNT = 10000;
    private transient boolean anomalyLimitReached = false;

    public GenericFilterProcessFunction(long windowMs, double anomalyThreshold, boolean anomalyEnabled, FilterAlgorithm algorithm) {
        this.windowMs = windowMs;
        this.anomalyThreshold = anomalyThreshold;
        this.anomalyEnabled = anomalyEnabled;
        this.algorithm = algorithm;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        lastCheckTime = System.currentTimeMillis();
        localProcessedCount = 0;
        anomalyLimitReached = false;
        anomalyCount = 0;
        window = new ArrayDeque<>();
        stats = new WindowStats();
        // 每个并行任务独立的滤波策略
        strategy = FilterFactory.create(algorithm);
    }

    @Override
    public void processElement(TdmsSample sample, Context ctx, Collector<FilterResult> out) throws Exception {

        // 优化：直接调用滤波算法，减少方法调用开销
        double filtered = strategy.filter(sample.getValue(), sample.getTimestamp());

        // 2. Window Logic
        WindowEntry entry = new WindowEntry(sample.getTimestamp(), sample.getValue(), filtered);
        window.addLast(entry);

        double noise = entry.original - entry.filtered;
        stats.sumOriginalSq += entry.original * entry.original;
        stats.sumFilteredSq += entry.filtered * entry.filtered;
        stats.sumNoiseSq += noise * noise;

        // 优化：窗口清理 - 批量清理，减少循环和计算开销
        long boundary = sample.getTimestamp() - windowMs;
        // 优化：只在窗口较大或确实有过期数据时才清理
        if (window.size() > 50) { // 降低阈值，更频繁清理小窗口
            // 批量清理：一次性清理所有过期数据
            int removedCount = 0;
            while (!window.isEmpty() && window.peekFirst().timestamp < boundary) {
                WindowEntry removed = window.pollFirst();
                double rNoise = removed.original - removed.filtered;
                double origSq = removed.original * removed.original;
                double filtSq = removed.filtered * removed.filtered;
                double noiseSq = rNoise * rNoise;
                
                stats.sumOriginalSq -= origSq;
                stats.sumFilteredSq -= filtSq;
                stats.sumNoiseSq -= noiseSq;
                removedCount++;
            }
            // 优化：只在清理后才检查负值，减少检查次数
            if (removedCount > 0) {
                if (stats.sumOriginalSq < 0) stats.sumOriginalSq = 0;
                if (stats.sumFilteredSq < 0) stats.sumFilteredSq = 0;
                if (stats.sumNoiseSq < 0) stats.sumNoiseSq = 0;
            }
        } else if (!window.isEmpty() && window.peekFirst().timestamp < boundary) {
            // 小窗口时只清理一个过期元素
            WindowEntry removed = window.pollFirst();
            double rNoise = removed.original - removed.filtered;
            stats.sumOriginalSq -= removed.original * removed.original;
            stats.sumFilteredSq -= removed.filtered * removed.filtered;
            stats.sumNoiseSq -= rNoise * rNoise;
        }

        // 优化3：SNR & Anomaly - 减少除法和函数调用
        int windowSize = window.size();
        // 优化：缓存倒数，避免重复除法
        double invWindowSize = (windowSize > 0) ? 1.0 / windowSize : 0;
        double signalPowerRaw = stats.sumOriginalSq * invWindowSize;
        double signalPowerFiltered = stats.sumFilteredSq * invWindowSize;
        double noisePower = stats.sumNoiseSq * invWindowSize;

        // 优化：只在需要时计算SNR（如果不需要SNR可以跳过）
        double snrBefore = toDb(signalPowerRaw, noisePower);
        double snrAfter = toDb(signalPowerFiltered, noisePower);
        double snrDelta = snrAfter - snrBefore;

        double energy = signalPowerRaw;
        // 优化：提前判断，减少计算
        boolean anomaly = anomalyEnabled && windowSize > 0 && energy > anomalyThreshold;
        
        // 优化5：检查异常点上限，达到上限后不再处理
        if (anomalyLimitReached) {
            // 已达到上限，不再创建FilterResult，直接返回
            return;
        }
        
        if (anomaly) {
            anomalyCount++;
            // 优化5：达到上限后设置标志，后续样本不再处理
            if (anomalyCount >= MAX_ANOMALY_COUNT) {
                anomalyLimitReached = true;
            }
        }

        // 优化1：只在异常时才创建FilterResult对象，大幅减少对象创建和GC压力
        if (!anomaly) {
            // 非异常点不需要创建FilterResult，直接返回
            return;
        }

        // 优化4：吞吐量计算 - 减少系统调用和计算频率
        // 只在每1000个样本时更新一次，减少System.currentTimeMillis()调用
        localProcessedCount++;
        double kps = 0;
        double mbps = 0;
        if (localProcessedCount % 1000 == 0) {
            long now = System.currentTimeMillis();
            long deltaMs = now - lastCheckTime;
            if (deltaMs > 0) {
                double deltaSeconds = deltaMs / 1000.0;
                double pointsPerSec = 1000.0 / deltaSeconds; // 1000个样本 / 时间
                kps = pointsPerSec / 1000.0;
                mbps = (pointsPerSec * 16.0) / (1024.0 * 1024.0);
            }
            lastCheckTime = now;
        }

        // 只在异常时创建 FilterResult（已通过上面的 if 检查）
        FilterResult result = FilterResult.builder()
                .timestamp(sample.getTimestamp())
                .channel(sample.getChannel())
                .originalValue(sample.getValue())
                .filteredValue(filtered)
                .anomaly(true) // 这里一定是true，因为已经检查过
                .energy(energy)
                .snrBeforeDb(snrBefore)
                .snrAfterDb(snrAfter)
                .snrDeltaDb(snrDelta)
                .throughputKps(kps)
                .throughputMbps(mbps)
                .anomalyCount(anomalyCount)
                .build();
        
        out.collect(result);
    }

    private double toDb(double signalPower, double noisePower) {
        if (noisePower <= 0) return Double.POSITIVE_INFINITY;
        if (signalPower <= 0) return -Double.MAX_VALUE;
        return 10 * Math.log10(signalPower / noisePower);
    }

    // Helper classes
    public static class WindowEntry implements Serializable {
        public long timestamp;
        public double original;
        public double filtered;

        public WindowEntry() {}
        public WindowEntry(long timestamp, double original, double filtered) {
            this.timestamp = timestamp;
            this.original = original;
            this.filtered = filtered;
        }
    }

    public static class WindowStats implements Serializable {
        public double sumOriginalSq = 0.0;
        public double sumFilteredSq = 0.0;
        public double sumNoiseSq = 0.0;
    }
}
