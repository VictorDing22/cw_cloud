package cn.iocoder.yudao.module.detection.logic;

import cn.iocoder.yudao.module.detection.api.FilterAlgorithm;
import cn.iocoder.yudao.module.detection.api.dto.TdmsSample;
import cn.iocoder.yudao.module.detection.logic.dto.FilterResult;
import cn.iocoder.yudao.module.detection.logic.filter.FilterFactory;
import cn.iocoder.yudao.module.detection.logic.filter.FilterStrategy;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Generic Flink Process Function that uses FilterStrategy.
 */
public class GenericFilterProcessFunction extends KeyedProcessFunction<String, TdmsSample, FilterResult> {

    private final long windowMs;
    private final double anomalyThreshold;
    private final boolean anomalyEnabled;
    private final FilterAlgorithm algorithm;

    // Flink State (Only windowing stats)
    private transient ValueState<Deque<WindowEntry>> windowState;
    private transient ValueState<Long> anomalyCountState;
    private transient ValueState<WindowStats> statsState;

    // Local State
    private transient Deque<WindowEntry> window;
    private transient long anomalyCount;
    private transient WindowStats stats;
    private transient FilterStrategy strategy;

    private boolean isStateLoaded = false;
    
    // Throughput tracking
    private transient long lastCheckTime;
    private transient long localProcessedCount;

    public GenericFilterProcessFunction(long windowMs, double anomalyThreshold, boolean anomalyEnabled, FilterAlgorithm algorithm) {
        this.windowMs = windowMs;
        this.anomalyThreshold = anomalyThreshold;
        this.anomalyEnabled = anomalyEnabled;
        this.algorithm = algorithm;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        windowState = getRuntimeContext().getState(new ValueStateDescriptor<>("window", TypeInformation.of(new TypeHint<Deque<WindowEntry>>() {})));
        anomalyCountState = getRuntimeContext().getState(new ValueStateDescriptor<>("anomalyCount", Long.class));
        statsState = getRuntimeContext().getState(new ValueStateDescriptor<>("stats", TypeInformation.of(new TypeHint<WindowStats>() {})));
        
        lastCheckTime = System.currentTimeMillis();
        localProcessedCount = 0;
        
        // Create Strategy (Not persisted in Flink State, re-created on restart)
        strategy = FilterFactory.create(algorithm);
    }

    @Override
    public void processElement(TdmsSample sample, Context ctx, Collector<FilterResult> out) throws Exception {
        if (!isStateLoaded) {
            loadState();
            isStateLoaded = true;
        }

        // 1. Filter
        double filtered = strategy.filter(sample.getValue(), sample.getTimestamp());

        // 2. Window Logic
        WindowEntry entry = new WindowEntry(sample.getTimestamp(), sample.getValue(), filtered);
        window.addLast(entry);

        double noise = entry.original - entry.filtered;
        stats.sumOriginalSq += entry.original * entry.original;
        stats.sumFilteredSq += entry.filtered * entry.filtered;
        stats.sumNoiseSq += noise * noise;

        // Clean window
        long boundary = sample.getTimestamp() - windowMs;
        while (!window.isEmpty() && window.peekFirst().timestamp < boundary) {
            WindowEntry removed = window.pollFirst();
            double rNoise = removed.original - removed.filtered;
            stats.sumOriginalSq -= removed.original * removed.original;
            stats.sumFilteredSq -= removed.filtered * removed.filtered;
            stats.sumNoiseSq -= rNoise * rNoise;
        }
        if (stats.sumOriginalSq < 0) stats.sumOriginalSq = 0;
        if (stats.sumFilteredSq < 0) stats.sumFilteredSq = 0;
        if (stats.sumNoiseSq < 0) stats.sumNoiseSq = 0;

        // 3. SNR & Anomaly
        int windowSize = window.size();
        double signalPowerRaw = (windowSize > 0) ? stats.sumOriginalSq / windowSize : 0;
        double signalPowerFiltered = (windowSize > 0) ? stats.sumFilteredSq / windowSize : 0;
        double noisePower = (windowSize > 0) ? stats.sumNoiseSq / windowSize : 0;

        double snrBefore = toDb(signalPowerRaw, noisePower);
        double snrAfter = toDb(signalPowerFiltered, noisePower);
        double snrDelta = snrAfter - snrBefore;

        double energy = signalPowerRaw;
        boolean anomaly = anomalyEnabled && energy > anomalyThreshold && windowSize > 0;
        if (anomaly) {
            anomalyCount++;
        }

        // 4. Update Throughput
        long now = System.currentTimeMillis();
        localProcessedCount++;
        double kps = 0;
        double mbps = 0;
        if (now - lastCheckTime >= 500) {
            double deltaSeconds = (now - lastCheckTime) / 1000.0;
            if (deltaSeconds > 0) {
                double pointsPerSec = localProcessedCount / deltaSeconds;
                kps = pointsPerSec / 1000.0;
                mbps = (pointsPerSec * 16.0) / (1024.0 * 1024.0);
            }
            lastCheckTime = now;
            localProcessedCount = 0;
        }

        // 5. Output
        FilterResult result = FilterResult.builder()
                .timestamp(sample.getTimestamp())
                .originalValue(sample.getValue())
                .filteredValue(filtered)
                .anomaly(anomaly)
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

    private void loadState() throws Exception {
        window = windowState.value();
        Long ac = anomalyCountState.value();
        anomalyCount = (ac == null) ? 0L : ac;
        stats = statsState.value();

        if (window == null) window = new ArrayDeque<>();
        if (stats == null) stats = new WindowStats();
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
