package cn.iocoder.yudao.module.monitor.flink;

import cn.iocoder.yudao.module.monitor.api.dto.FilterConfig;
import cn.iocoder.yudao.module.monitor.api.dto.FilterType;
import cn.iocoder.yudao.module.monitor.api.dto.MonitorStreamMessage;
import cn.iocoder.yudao.module.monitor.api.dto.TdmsChannelMetadata;
import cn.iocoder.yudao.module.monitor.api.dto.TdmsSample;
import cn.iocoder.yudao.module.monitor.filter.Kalman1DFilter;
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
 * 实时回放：滤波（LMS / Kalman） + 残差 + SNR + 能量阈值异常检测。
 */
public class SignalProcessFunction extends KeyedProcessFunction<String, TdmsSample, MonitorStreamMessage> {

    private final String jobId;
    private final TdmsChannelMetadata channelMetadata;
    private final long windowMs;
    private final double anomalyThreshold;
    private final boolean anomalyEnabled;
    private final FilterConfig filterConfig;

    // LMS 状态
    private transient double[] weights;
    private transient double[] buffer;
    private transient int bufferIndex;
    private final double lmsStepSize = 0.01;
    private final int lmsOrder = 8;

    // Kalman 状态
    private transient Kalman1DFilter kalman;

    // 窗口统计
    private transient Deque<WindowEntry> window;
    private transient long anomalyCount;
    private transient ValueState<Long> processedCounter;
    private transient ValueState<Long> startTimeMs;

    public SignalProcessFunction(String jobId, TdmsChannelMetadata channelMetadata, long windowMs,
                                 double anomalyThreshold, boolean anomalyEnabled,
                                 FilterConfig filterConfig) {
        this.jobId = jobId;
        this.channelMetadata = channelMetadata;
        this.windowMs = windowMs;
        this.anomalyThreshold = anomalyThreshold;
        this.anomalyEnabled = anomalyEnabled;
        this.filterConfig = filterConfig;
    }

    @Override
    public void open(Configuration parameters) {
        this.weights = new double[lmsOrder];
        this.buffer = new double[lmsOrder];
        this.bufferIndex = 0;
        this.kalman = new Kalman1DFilter(
                filterConfig.getKalmanQ(),
                filterConfig.getKalmanR(),
                filterConfig.getKalmanP0(),
                filterConfig.getKalmanX0N()
        );
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

        double original = sample.getValue();
        double filtered = applyFilter(original);
        double residual = original - filtered;

        WindowEntry entry = new WindowEntry(sample.getTimestamp(), original, filtered, residual);
        window.addLast(entry);
        cleanWindow(sample.getTimestamp());

        // SNR：保持原有计算口径（raw vs filtered，噪声=残差）
        double signalPowerRaw = 0.0;
        double signalPowerFiltered = 0.0;
        double noisePower = 0.0;
        for (WindowEntry e : window) {
            signalPowerRaw += e.original * e.original;
            signalPowerFiltered += e.filtered * e.filtered;
            noisePower += e.residual * e.residual;
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

        // 异常检测：能量阈值法作用在残差信号上（符合“残差=原始-滤波输出”的定义）
        double energy = 0.0;
        for (WindowEntry e : window) {
            energy += e.residual * e.residual;
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
                .originalValue(original)
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

    private double applyFilter(double x) {
        if (filterConfig.getType() == FilterType.KALMAN) {
            return kalman.filter(x);
        }
        // LMS
        buffer[bufferIndex] = x;
        bufferIndex = (bufferIndex + 1) % lmsOrder;
        double y = 0.0;
        int idx = bufferIndex;
        for (int i = 0; i < lmsOrder; i++) {
            idx = (idx - 1 + lmsOrder) % lmsOrder;
            y += weights[i] * buffer[idx];
        }
        double error = buffer[(bufferIndex - 1 + lmsOrder) % lmsOrder] - y;
        idx = bufferIndex;
        for (int i = 0; i < lmsOrder; i++) {
            idx = (idx - 1 + lmsOrder) % lmsOrder;
            weights[i] += 2 * lmsStepSize * error * buffer[idx];
        }
        return y;
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
        final double residual;

        WindowEntry(long timestamp, double original, double filtered, double residual) {
            this.timestamp = timestamp;
            this.original = original;
            this.filtered = filtered;
            this.residual = residual;
        }
    }
}

