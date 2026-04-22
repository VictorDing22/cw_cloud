package cn.iocoder.yudao.module.monitor.service.impl;

import cn.iocoder.yudao.module.monitor.api.dto.MonitorStreamMessage;
import cn.iocoder.yudao.module.monitor.api.dto.TdmsSample;
import cn.iocoder.yudao.module.monitor.api.dto.FilterConfig;
import cn.iocoder.yudao.module.monitor.flink.SignalProcessFunction;
import cn.iocoder.yudao.module.monitor.flink.MonitorResultSink;
import cn.iocoder.yudao.module.monitor.flink.TdmsReplaySource;
import cn.iocoder.yudao.module.monitor.service.FlinkPlaybackService;
import cn.iocoder.yudao.module.monitor.service.dto.ParsedTdmsData;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlinkPlaybackServiceImpl implements FlinkPlaybackService {

    @Getter
    private static class RunningJob {
        private final ParsedTdmsData data;
        private final double threshold;
        private final boolean anomalyEnabled;
        private final FilterConfig filterConfig;
        private org.apache.flink.core.execution.JobClient client;

        RunningJob(ParsedTdmsData data, double threshold, boolean anomalyEnabled, FilterConfig filterConfig) {
            this.data = data;
            this.threshold = threshold;
            this.anomalyEnabled = anomalyEnabled;
            this.filterConfig = filterConfig;
        }
    }
    private final Map<String, RunningJob> jobs = new ConcurrentHashMap<>();

    @Override
    public synchronized void startJob(String jobId, ParsedTdmsData data, double anomalyThreshold, boolean anomalyEnabled, FilterConfig filterConfig) {
        stopJob(jobId);

        // 显式保证当前线程的上下文 ClassLoader 使用 Spring Boot 的 ClassLoader，
        // 让 Flink MiniCluster 在反序列化 ExecutionConfig 等类时走到 fat jar 的 ClassLoader。
        ClassLoader springCl = FlinkPlaybackServiceImpl.class.getClassLoader();
        Thread.currentThread().setContextClassLoader(springCl);

        // 配置 Flink 以支持 Java 模块系统（Java 9+）
        // 解决 InaccessibleObjectException: Unable to make field accessible
        Configuration flinkConfig = new Configuration();
        // 设置 JVM 参数以开放必要的模块（用于 Kryo 序列化）
        // 这些参数会在 Flink MiniCluster 启动时传递给 TaskManager
        // 注意：Flink 1.18.1 使用 env.java.opts 配置项
        String jvmArgs = "--add-opens=java.base/java.util=ALL-UNNAMED " +
                         "--add-opens=java.base/java.lang=ALL-UNNAMED " +
                         "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED " +
                         "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED";
        flinkConfig.setString("env.java.opts", jvmArgs);
        
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(flinkConfig);
        env.setParallelism(1);
        
        // 禁用强制 Kryo，让 Flink 自动检测并使用 POJO 序列化
        // MonitorStreamMessage 已经提供了无参构造函数（@NoArgsConstructor）和 getter/setter（@Data），符合 POJO 要求
        // 这样 Flink 会自动使用 POJO 序列化，避免 Kryo 的 Java 模块系统问题
        env.getConfig().disableForceKryo();

        // Flink 1.17 中，如果使用 SourceFunction，需要使用 addSource 而不是 fromSource（fromSource 是新 Source API）
        DataStream<TdmsSample> sourceStream = env
                .addSource(new TdmsReplaySource(data.getSamples()), "tdms-replay")
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<TdmsSample>forMonotonousTimestamps()
                                .withTimestampAssigner((event, ts) -> event.getTimestamp())
                );

        DataStream<MonitorStreamMessage> pipeline = sourceStream
                .keyBy(TdmsSample::getChannel)
                .process(new SignalProcessFunction(jobId, data.getChannel(), 5000, anomalyThreshold, anomalyEnabled, filterConfig));

        // 注意：MonitorResultSink 内部自行通过 SpringUtils.getBean(...) 获取 MonitorResultHub，
        // 避免在这里将 Spring Bean 作为字段传入，从而被 Flink 闭包序列化，导致 NotSerializableException。
        pipeline.addSink(new MonitorResultSink()).name("ws-push");

        try {
            org.apache.flink.core.execution.JobClient client = env.executeAsync("monitor-" + jobId);
            RunningJob running = new RunningJob(data, anomalyThreshold, anomalyEnabled, filterConfig);
            running.client = client;
            jobs.put(jobId, running);
        } catch (Exception e) {
            log.error("启动 Flink 作业失败，jobId={}，原因={}", jobId, e.getMessage(), e);
            // 统一包装为业务异常，避免返回 500 系统异常但原因不明
            throw ServiceExceptionUtil.exception(
                    GlobalErrorCodeConstants.BAD_REQUEST,
                    "启动实时处理失败：" + e.getMessage());
        }
    }

    @Override
    public synchronized void stopJob(String jobId) {
        RunningJob running = jobs.remove(jobId);
        if (running != null && running.getClient() != null) {
            try {
                running.getClient().cancel().get();
            } catch (Exception e) {
                log.warn("取消作业 {} 失败: {}", jobId, e.getMessage());
            }
        }
    }

    @Override
    public synchronized void updateAnomalyConfig(String jobId, double threshold, boolean enabled) {
        RunningJob running = jobs.get(jobId);
        if (running == null) {
            return;
        }
        ParsedTdmsData data = running.getData();
        FilterConfig filterConfig = running.getFilterConfig();
        stopJob(jobId);
        startJob(jobId, data, threshold, enabled, filterConfig);
    }

    @Override
    public synchronized void updateFilterConfig(String jobId, FilterConfig filterConfig) {
        RunningJob running = jobs.get(jobId);
        if (running == null) {
            return;
        }
        ParsedTdmsData data = running.getData();
        double threshold = running.getThreshold();
        boolean enabled = running.isAnomalyEnabled();
        stopJob(jobId);
        startJob(jobId, data, threshold, enabled, filterConfig);
    }
}
