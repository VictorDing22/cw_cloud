package cn.iocoder.yudao.module.monitor.flink;

import cn.iocoder.yudao.module.monitor.api.dto.MonitorStreamMessage;
import cn.iocoder.yudao.module.monitor.api.dto.TdmsChannelMetadata;
import cn.iocoder.yudao.module.monitor.api.dto.TdmsSample;
import org.apache.commons.math3.util.FastMath;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * LMS 自适应滤波 + SNR 统计 + 能量阈值异常检测。
 */
public class LmsProcessFunction extends KeyedProcessFunction<String, TdmsSample, MonitorStreamMessage> {

    private final String jobId;
    private final TdmsChannelMetadata channelMetadata;
    private final long windowMs;
    private final double anomalyThreshold;
    private final boolean anomalyEnabled;

    private transient double[] weights;
    private transient double[] buffer;
    private transient int bufferIndex;
    private transient Deque<WindowEntry> window;
    private transient long anomalyCount;
    private transient ValueState<Long> processedCounter;
    private transient ValueState<Long> startTimeMs;

    private final double stepSize = 0.01;
    private final int order = 8;

    public LmsProcessFunction(String jobId, TdmsChannelMetadata channelMetadata, long windowMs,
                              double anomalyThreshold, boolean anomalyEnabled) {
        this.jobId = jobId;
        this.channelMetadata = channelMetadata;
        this.windowMs = windowMs;
        this.anomalyThreshold = anomalyThreshold;
        this.anomalyEnabled = anomalyEnabled;
    }

    @Override
    public void open(Configuration parameters) {
        this.weights = new double[order];
        this.buffer = new double[order];
        this.bufferIndex = 0;
        this.window = new ArrayDeque<>();
        this.anomalyCount = 0L;
        this.processedCounter = getRuntimeContext().getState(
                new ValueStateDescriptor<>("processedCounter", TypeInformation.of(Long.class)));
        this.startTimeMs = getRuntimeContext().getState(
                new ValueStateDescriptor<>("startTime", TypeInformation.of(Long.class)));
    }

    @Override
    public void processElement(TdmsSample sample, Context ctx, Collector<MonitorStreamMessage> out) throws Exception {
        if (startTimeMs.value() == null) {
            startTimeMs.update(System.currentTimeMillis());
        }
        // 更新环形缓冲
        buffer[bufferIndex] = sample.getValue();
        bufferIndex = (bufferIndex + 1) % order;

        // LMS 滤波
        double y = 0.0;
        int idx = bufferIndex;
        for (int i = 0; i < order; i++) {
            idx = (idx - 1 + order) % order;
            y += weights[i] * buffer[idx];
        }
        double error = buffer[(bufferIndex - 1 + order) % order] - y;
        idx = bufferIndex;
        for (int i = 0; i < order; i++) {
            idx = (idx - 1 + order) % order;
            weights[i] += 2 * stepSize * error * buffer[idx];
        }
        double filtered = y;

        // 窗口能量与 SNR
        WindowEntry entry = new WindowEntry(sample.getTimestamp(), sample.getValue(), filtered);
        window.addLast(entry);
        cleanWindow(sample.getTimestamp());

        double signalPowerRaw = 0.0;
        double signalPowerFiltered = 0.0;
        double noisePower = 0.0;
        for (WindowEntry e : window) {
            signalPowerRaw += e.original * e.original;
            signalPowerFiltered += e.filtered * e.filtered;
            double noise = e.original - e.filtered;
            noisePower += noise * noise;
        }
        int windowSize = window.size();
        if (windowSize > 0) {
            signalPowerRaw /= windowSize;
            signalPowerFiltered /= windowSize;
            noisePower /= windowSize;
        }
        double snrBefore = toDb(signalPowerRaw, noisePower);
        double snrAfter = toDb(signalPowerFiltered, noisePower);
        double snrDelta = snrAfter - snrBefore;

        // 异常检测
        double energy = 0.0;
        for (WindowEntry e : window) {
            energy += e.original * e.original;
        }
        boolean anomaly = anomalyEnabled && energy > anomalyThreshold && windowSize > 0;
        if (anomaly) {
            anomalyCount++;
        }

        long processed = processedCounter.value() == null ? 0L : processedCounter.value();
        processed++;
        processedCounter.update(processed);
        long now = System.currentTimeMillis();
        long start = startTimeMs.value() == null ? now : startTimeMs.value();
        double pointsPerSec = processed <= 0 ? 0 : (processed * 1.0) / Math.max((now - start) / 1000.0, 0.001);
        double throughputKps = pointsPerSec / 1000.0;
        double delayMs = now - sample.getTimestamp();

        MonitorStreamMessage message = MonitorStreamMessage.builder()
                .jobId(jobId)
                .timestamp(sample.getTimestamp())
                .originalValue(sample.getValue())
                .filteredValue(filtered)
                .anomaly(anomaly)
                .energy(energy)
                .snrBeforeDb(snrBefore)
                .snrAfterDb(snrAfter)
                .snrDeltaDb(snrDelta)
                .throughputKps(throughputKps)
                .processingDelayMs(delayMs)
                .anomalyCount(anomalyCount)
                .channel(channelMetadata)
                .build();
        out.collect(message);
    }

    private void cleanWindow(long currentTs) {
        long boundary = currentTs - windowMs;
        while (!window.isEmpty() && window.peekFirst().timestamp < boundary) {
            window.pollFirst();
        }
    }

    private double toDb(double signalPower, double noisePower) {
        if (noisePower <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        if (signalPower <= 0) {
            return -Double.MAX_VALUE;
        }
        return 10 * FastMath.log10(signalPower / noisePower);
    }

    private static class WindowEntry implements Serializable {
        final long timestamp;
        final double original;
        final double filtered;

        WindowEntry(long timestamp, double original, double filtered) {
            this.timestamp = timestamp;
            this.original = original;
            this.filtered = filtered;
        }
    }
}
