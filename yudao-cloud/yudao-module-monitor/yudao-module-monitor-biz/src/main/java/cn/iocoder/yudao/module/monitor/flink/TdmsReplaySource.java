package cn.iocoder.yudao.module.monitor.flink;

import cn.iocoder.yudao.module.monitor.api.dto.TdmsSample;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将 TDMS 样本按原始时间间隔回放到 Flink。
 */
public class TdmsReplaySource extends RichParallelSourceFunction<TdmsSample> {

    private final List<TdmsSample> samples;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public TdmsReplaySource(List<TdmsSample> samples) {
        this.samples = samples;
    }

    @Override
    public void run(SourceContext<TdmsSample> ctx) throws Exception {
        TdmsSample previous = null;
        for (TdmsSample sample : samples) {
            if (!running.get()) {
                break;
            }
            if (previous != null) {
                long gap = sample.getTimestamp() - previous.getTimestamp();
                if (gap > 0) {
                    Thread.sleep(Math.min(gap, 2000)); // 避免长时间阻塞
                }
            }
            synchronized (ctx.getCheckpointLock()) {
                ctx.collectWithTimestamp(sample, sample.getTimestamp());
            }
            previous = sample;
        }
    }

    @Override
    public void cancel() {
        running.set(false);
    }
}
