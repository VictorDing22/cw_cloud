package cn.iocoder.yudao.module.detection.service;

import cn.iocoder.yudao.module.detection.api.DetectionResult;
import cn.iocoder.yudao.module.detection.api.DetectionServiceGrpc;
import cn.iocoder.yudao.module.detection.api.FileChunk;
import cn.iocoder.yudao.module.detection.api.AnomalyEvent;
import cn.iocoder.yudao.module.detection.api.FilterAlgorithm;
import cn.iocoder.yudao.module.detection.api.dto.TdmsSample;
import cn.iocoder.yudao.module.detection.logic.GenericFilterProcessFunction;
import cn.iocoder.yudao.module.detection.logic.dto.FilterResult;
import cn.iocoder.yudao.module.detection.util.FastTdmsParser;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.CloseableIterator;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@GrpcService
public class RealTimeDetectionServiceImpl extends DetectionServiceGrpc.DetectionServiceImplBase {

    @Override
    public StreamObserver<FileChunk> uploadAndDetect(StreamObserver<DetectionResult> responseObserver) {
        return new StreamObserver<FileChunk>() {
            private File tempFile;
            private OutputStream outputStream;
            private String jobId;
            private long startTime;
            private FilterAlgorithm algorithm = FilterAlgorithm.LMS; // Default to LMS

            @Override
            public void onNext(FileChunk chunk) {
                try {
                    if (tempFile == null) {
                        startTime = System.currentTimeMillis();
                        jobId = UUID.randomUUID().toString();
                        
                        // 获取算法参数
                        if (chunk.getAlgorithm() != FilterAlgorithm.UNRECOGNIZED) {
                            this.algorithm = chunk.getAlgorithm();
                        }
                        
                        // 创建临时文件
                        String filename = chunk.getFilename();
                        if (filename == null || filename.isEmpty()) {
                            filename = "upload.tdms";
                        }
                        tempFile = Files.createTempFile("grpc-detect-", filename).toFile();
                        // 优化6：使用BufferedOutputStream提升文件写入性能（64KB缓冲区）
                        outputStream = new BufferedOutputStream(
                            new FileOutputStream(tempFile), 
                            64 * 1024  // 64KB缓冲区
                        );
                        log.info("开始接收文件: {}, JobID: {}", tempFile.getAbsolutePath(), jobId);
                    }
                    chunk.getContent().writeTo(outputStream);
                } catch (IOException e) {
                    log.error("写入文件失败", e);
                    onError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("文件上传出错", t);
                cleanup();
            }

            @Override
            public void onCompleted() {
                try {
                    if (outputStream != null) {
                        // 优化6：确保BufferedOutputStream的数据都写入磁盘
                        outputStream.flush();
                        outputStream.close();
                    }
                    log.info("文件接收完毕，开始解析和检测...");
                    
                    // 1. 快速解析
                    long parseStart = System.currentTimeMillis();
                    FastTdmsParser.FastParsedData data = FastTdmsParser.parse(tempFile);
                    long parseEnd = System.currentTimeMillis();
                    log.info("解析耗时: {} ms, 样本数: {}", (parseEnd - parseStart), data.getSamples().size());

                    // 2. 实时检测逻辑 (Flink Technology) - 高性能优化版本
                    log.info("启动 Flink 任务进行实时检测...");
                    
                    // 优化1：高性能Flink配置
                    Configuration flinkConfig = new Configuration();
                    // 优化：设置任务管理器内存段大小（提升网络性能）
                    try {
                        flinkConfig.set(org.apache.flink.configuration.TaskManagerOptions.MEMORY_SEGMENT_SIZE, MemorySize.parse("32kb"));
                    } catch (Exception e) {
                        // API可能不存在，忽略
                        log.debug("无法设置内存段大小: {}", e.getMessage());
                    }
                    
                    StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(flinkConfig);
                    // 启用批处理运行模式，针对离线文件处理做优化
                    env.setRuntimeMode(RuntimeExecutionMode.BATCH);
                    
                    // 优化2：提高并行度（充分利用多核CPU）
                    int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1); // 保留1个核心给系统
                    env.setParallelism(parallelism);
                    log.info("Flink并行度设置为: {}", parallelism);
                    
                    // 优化3：批处理模式配置
                    env.getCheckpointConfig().disableCheckpointing();
                    env.setBufferTimeout(0); // 批处理模式，立即刷新
                    // 优化：启用对象重用（减少对象创建开销）
                    env.getConfig().enableObjectReuse();
                    // 优化：禁用强制Kryo，使用POJO序列化（更快）
                    env.getConfig().disableForceKryo();
                    
                    // 优化4：使用高效的数据源（避免fromCollection的序列化开销）
                    DataStream<TdmsSample> stream = env.addSource(
                        new TdmsSampleSource(data.getSamples()), 
                        "tdms-source"
                    ).setParallelism(parallelism);
                    
                    log.info("Using Filter Algorithm: {}", algorithm);
                    
                    // 方案1：移除 keyBy，使用 rebalance 强制分片并行处理
                    // GenericFilterProcessFunction 已改为无 Key 状态版，不再依赖 keyBy
                    DataStream<FilterResult> resultStream = stream
                            .rebalance() // 将数据均匀打散到各个并行子任务
                            .process(new GenericFilterProcessFunction(500, 3.0, true, algorithm))
                            .setParallelism(parallelism); // 明确设置并行度
                    
                    // 优化6：预分配列表容量，减少扩容开销
                    // 预估异常点数量（通常不超过总样本的1%）
                    int estimatedAnomalyCount = Math.min(10000, (int)(data.getSamples().size() * 0.01));
                    List<AnomalyEvent> anomalies = new ArrayList<>(estimatedAnomalyCount);
                    long anomalyCount = 0;
                    
                    // 优化7：使用executeAndCollect，但批量处理结果
                    try (CloseableIterator<FilterResult> iterator = resultStream.executeAndCollect()) {
                        // 批量收集，减少迭代器调用开销
                        int batchCount = 0;
                        while (iterator.hasNext()) {
                            FilterResult result = iterator.next();
                            anomalyCount++;
                            
                            // 优化：限制异常点数量，避免内存溢出
                            if (anomalies.size() < 10000) {
                                anomalies.add(AnomalyEvent.newBuilder()
                                        .setTimestamp(result.getTimestamp())
                                        .setValue(result.getOriginalValue())
                                        .setEnergy(result.getEnergy())
                                        .build());
                            }
                            
                            // 每处理1000个结果检查一次是否需要提前退出（如果异常点已满）
                            batchCount++;
                            if (batchCount % 1000 == 0 && anomalies.size() >= 10000) {
                                // 继续计数但不收集，确保anomalyCount准确
                            }
                        }
                    }
                    
                    long processEnd = System.currentTimeMillis();
                    double totalTimeMs = processEnd - startTime;
                    
                    // 计算总吞吐量 (MB/s)
                    long fileBytes = tempFile.length();
                    double throughput = (fileBytes / 1024.0 / 1024.0) / (totalTimeMs / 1000.0);

                    DetectionResult response = DetectionResult.newBuilder()
                            .setJobId(jobId)
                            .setSampleCount(data.getSamples().size())
                            .setAnomalyCount(anomalyCount)
                            .setProcessingTimeMs(totalTimeMs)
                            .setThroughputMbps(throughput)
                            .addAllAnomalies(anomalies)
                            .build();

                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                    
                } catch (Exception e) {
                    log.error("处理失败", e);
                    responseObserver.onError(e);
                } finally {
                    cleanup();
                }
            }

            private void cleanup() {
                try {
                    if (outputStream != null) outputStream.close();
                    if (tempFile != null && tempFile.exists()) tempFile.delete();
                } catch (IOException e) {
                    // ignore
                }
            }
        };
    }
}
