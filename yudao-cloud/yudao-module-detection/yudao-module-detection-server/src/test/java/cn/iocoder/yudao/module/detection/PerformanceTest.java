package cn.iocoder.yudao.module.detection;

import cn.iocoder.yudao.module.detection.api.FilterAlgorithm;
import cn.iocoder.yudao.module.detection.api.dto.TdmsSample;
import cn.iocoder.yudao.module.detection.logic.GenericFilterProcessFunction;
import cn.iocoder.yudao.module.detection.logic.dto.FilterResult;
import cn.iocoder.yudao.module.detection.service.TdmsSampleSource;
import cn.iocoder.yudao.module.detection.util.FastTdmsParser;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.CloseableIterator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * TDMS文件处理速度完整测试
 * 测试完整的处理流程：解析 + Flink处理
 */
public class PerformanceTest {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("TDMS文件处理速度性能测试");
        System.out.println("========================================\n");

        // 测试不同大小的文件
        int[] fileSizes = {1000000, 2000000, 5000000, 10000000}; // 1M, 2M, 5M, 10M样本
        FilterAlgorithm[] algorithms = {
            FilterAlgorithm.KALMAN,
            FilterAlgorithm.LMS,
            FilterAlgorithm.MEAN
        };

        for (int fileSize : fileSizes) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("测试文件大小: " + fileSize + " 样本");
            System.out.println("=".repeat(60));

            // 生成测试文件
            File testFile = generateTestFile(fileSize);
            if (testFile == null || !testFile.exists()) {
                System.err.println("无法生成测试文件，跳过...");
                continue;
            }

            double fileSizeMB = testFile.length() / 1024.0 / 1024.0;
            System.out.println("文件大小: " + String.format("%.2f", fileSizeMB) + " MB");

            // 测试每个算法
            for (FilterAlgorithm algorithm : algorithms) {
                testProcessing(testFile, algorithm, fileSizeMB);
            }

            // 清理测试文件
            testFile.delete();
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("性能测试完成！");
        System.out.println("=".repeat(60));
    }

    /**
     * 测试完整的处理流程
     */
    private static void testProcessing(File testFile, FilterAlgorithm algorithm, double fileSizeMB) throws Exception {
        System.out.println("\n--- 算法: " + algorithm.name() + " ---");

        long totalStart = System.currentTimeMillis();

        // 1. 解析阶段
        long parseStart = System.currentTimeMillis();
        FastTdmsParser.FastParsedData data = FastTdmsParser.parse(testFile);
        long parseEnd = System.currentTimeMillis();
        long parseTime = parseEnd - parseStart;
        double parseThroughput = fileSizeMB / (parseTime / 1000.0);

        System.out.println("  解析阶段:");
        System.out.println("    耗时: " + parseTime + " ms");
        System.out.println("    吞吐: " + String.format("%.2f", parseThroughput) + " MB/s");
        System.out.println("    样本数: " + data.getSamples().size());

        // 2. Flink处理阶段
        long flinkStart = System.currentTimeMillis();
        
        // 配置Flink环境
        Configuration flinkConfig = new Configuration();
        try {
            flinkConfig.set(org.apache.flink.configuration.TaskManagerOptions.MEMORY_SEGMENT_SIZE, MemorySize.parse("32kb"));
        } catch (Exception e) {
            // 忽略
        }

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(flinkConfig);
        
        // 设置并行度
        int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        env.setParallelism(parallelism);
        
        // 优化配置
        env.getCheckpointConfig().disableCheckpointing();
        env.setBufferTimeout(0);
        env.getConfig().enableObjectReuse();
        env.getConfig().disableForceKryo();

        // 创建数据流
        DataStream<TdmsSample> stream = env.addSource(
            new TdmsSampleSource(data.getSamples()), 
            "tdms-source"
        ).setParallelism(parallelism);

        // 处理
        DataStream<FilterResult> resultStream = stream
            .keyBy(s -> s.getChannel())
            .process(new GenericFilterProcessFunction(500, 3.0, true, algorithm))
            .setParallelism(parallelism)
            .filter(result -> result.isAnomaly())
            .setParallelism(parallelism);

        // 收集结果
        long anomalyCount = 0;
        try (CloseableIterator<FilterResult> iterator = resultStream.executeAndCollect()) {
            while (iterator.hasNext()) {
                iterator.next();
                anomalyCount++;
            }
        }

        long flinkEnd = System.currentTimeMillis();
        long flinkTime = flinkEnd - flinkStart;
        double flinkThroughput = fileSizeMB / (flinkTime / 1000.0);

        System.out.println("  Flink处理阶段:");
        System.out.println("    并行度: " + parallelism);
        System.out.println("    耗时: " + flinkTime + " ms");
        System.out.println("    吞吐: " + String.format("%.2f", flinkThroughput) + " MB/s");
        System.out.println("    异常点数: " + anomalyCount);

        // 3. 总体性能
        long totalEnd = System.currentTimeMillis();
        long totalTime = totalEnd - totalStart;
        double totalThroughput = fileSizeMB / (totalTime / 1000.0);

        System.out.println("  总体性能:");
        System.out.println("    总耗时: " + totalTime + " ms");
        System.out.println("    总吞吐: " + String.format("%.2f", totalThroughput) + " MB/s");
        System.out.println("    解析占比: " + String.format("%.1f", parseTime * 100.0 / totalTime) + "%");
        System.out.println("    处理占比: " + String.format("%.1f", flinkTime * 100.0 / totalTime) + "%");
    }

    /**
     * 生成测试TDMS文件
     */
    private static File generateTestFile(int sampleCount) {
        try {
            File testFile = File.createTempFile("perf_test_", ".tdms");
            String script = 
                "import numpy as np\n" +
                "from nptdms import TdmsWriter, ChannelObject, GroupObject\n" +
                "import sys\n" +
                "import time\n" +
                "\n" +
                "# 生成测试数据\n" +
                "data = np.random.normal(0, 1, " + sampleCount + ").astype(np.float64)\n" +
                "# 添加一些异常点\n" +
                "anomaly_indices = np.random.choice(" + sampleCount + ", size=min(1000, " + sampleCount + "//100), replace=False)\n" +
                "data[anomaly_indices] = np.random.normal(0, 1, len(anomaly_indices)) * 10 + 50\n" +
                "\n" +
                "# 创建通道\n" +
                "c = ChannelObject('Group', 'Channel', data)\n" +
                "\n" +
                "# 写入文件\n" +
                "with TdmsWriter(sys.argv[1]) as w:\n" +
                "    w.write_segment([GroupObject('Group'), c])\n";

            File pyScript = File.createTempFile("gen_test_", ".py");
            Files.write(pyScript.toPath(), script.getBytes());

            try {
                ProcessBuilder pb = new ProcessBuilder("python", pyScript.getAbsolutePath(), testFile.getAbsolutePath());
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                // 等待完成，最多30秒
                boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    System.err.println("生成测试文件超时");
                    return null;
                }
                
                if (process.exitValue() != 0) {
                    System.err.println("生成测试文件失败，退出码: " + process.exitValue());
                    return null;
                }
            } finally {
                pyScript.delete();
            }

            return testFile;
        } catch (Exception e) {
            System.err.println("生成测试文件时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
