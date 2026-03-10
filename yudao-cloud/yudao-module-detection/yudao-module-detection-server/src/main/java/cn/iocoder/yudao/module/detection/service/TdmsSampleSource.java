package cn.iocoder.yudao.module.detection.service;

import cn.iocoder.yudao.module.detection.api.dto.TdmsSample;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;

import java.util.List;

/**
 * 高性能 TDMS 数据源（真正并行）：
 * - 基于 RichParallelSourceFunction
 * - 按 subtask 将样本切分成不重叠的区间，避免重复处理
 * - 批量发送，减少同步和上下文切换开销
 */
public class TdmsSampleSource extends RichParallelSourceFunction<TdmsSample> {

    private final List<TdmsSample> samples;
    private volatile boolean isRunning = true;

    // 每个 subtask 负责的区间 [startIndex, endIndex)
    private transient int startIndex;
    private transient int endIndex;

    public TdmsSampleSource(List<TdmsSample> samples) {
        this.samples = samples;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        int subtaskIndex = getRuntimeContext().getIndexOfThisSubtask(); // 0-based
        int numSubtasks = getRuntimeContext().getNumberOfParallelSubtasks();

        int size = samples.size();
        if (numSubtasks <= 1 || size == 0) {
            // 单并行度或空数据，整个列表由一个 subtask 处理
            this.startIndex = 0;
            this.endIndex = size;
            return;
        }

        // 均匀切分样本到各个 subtask，最后一个 subtask 可能多一点
        int baseChunk = size / numSubtasks;
        int remainder = size % numSubtasks;

        // 前 remainder 个 subtask 多分配一个元素
        if (subtaskIndex < remainder) {
            this.startIndex = subtaskIndex * (baseChunk + 1);
            this.endIndex = this.startIndex + baseChunk + 1;
        } else {
            this.startIndex = subtaskIndex * baseChunk + remainder;
            this.endIndex = this.startIndex + baseChunk;
        }
    }

    @Override
    public void run(SourceContext<TdmsSample> ctx) throws Exception {
        // 批量发送，减少同步和上下文切换开销
        final int batchSize = 2000;
        for (int i = startIndex; i < endIndex && isRunning; i += batchSize) {
            int batchEnd = Math.min(i + batchSize, endIndex);
            // Flink 要求在发射数据时持有 checkpointLock，保证一致性
            synchronized (ctx.getCheckpointLock()) {
                for (int j = i; j < batchEnd && isRunning; j++) {
                    ctx.collect(samples.get(j));
                }
            }
            if (!isRunning) {
                break;
            }
        }
    }

    @Override
    public void cancel() {
        isRunning = false;
    }
}
