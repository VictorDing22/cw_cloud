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
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.CloseableIterator;

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
                        outputStream = new FileOutputStream(tempFile);
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
                        outputStream.close();
                    }
                    log.info("文件接收完毕，开始解析和检测...");
                    
                    // 1. 快速解析
                    long parseStart = System.currentTimeMillis();
                    FastTdmsParser.FastParsedData data = FastTdmsParser.parse(tempFile);
                    long parseEnd = System.currentTimeMillis();
                    log.info("解析耗时: {} ms, 样本数: {}", (parseEnd - parseStart), data.getSamples().size());

                    // 2. 实时检测逻辑 (Flink Technology)
                    // 使用 Flink Local Environment 处理数据流
                    log.info("启动 Flink 任务进行实时检测...");
                    
                    StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
                    env.setParallelism(1); // 保证时序处理
                    
                    DataStream<TdmsSample> stream = env.fromCollection(data.getSamples());
                    
                    log.info("Using Filter Algorithm: {}", algorithm);
                    
                    DataStream<FilterResult> resultStream = stream
                            .keyBy(s -> "default_channel") // KeyedStream 必须有 Key
                            .process(new GenericFilterProcessFunction(500, 3.0, true, algorithm))
                            .filter(result -> result.isAnomaly()); // 仅收集异常点以提升性能
                    
                    List<AnomalyEvent> anomalies = new ArrayList<>();
                    long anomalyCount = 0;
                    
                    // executeAndCollect 会触发作业执行并返回迭代器
                    try (CloseableIterator<FilterResult> iterator = resultStream.executeAndCollect()) {
                        while (iterator.hasNext()) {
                            FilterResult result = iterator.next();
                            // result 已经是异常点
                            anomalyCount++;
                            anomalies.add(AnomalyEvent.newBuilder()
                                    .setTimestamp(result.getTimestamp())
                                    .setValue(result.getOriginalValue())
                                    .setEnergy(result.getEnergy())
                                    .build());
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
