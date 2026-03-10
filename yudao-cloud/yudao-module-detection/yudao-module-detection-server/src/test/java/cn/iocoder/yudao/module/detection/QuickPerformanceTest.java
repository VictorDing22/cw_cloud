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

/**
 * 快速性能测试 - 测试单个文件
 * 用法: java QuickPerformanceTest [文件路径] [算法名称]
 * 示例: java QuickPerformanceTest test.tdms KALMAN
 */
public class QuickPerformanceTest {

    public static void main(String[] args) throws Exception {
        // 默认参数
        String filePath = args.length > 0 ? args[0] : null;
        FilterAlgorithm algorithm = FilterAlgorithm.KALMAN;
        
        if (args.length > 1) {
            try {
                algorithm = FilterAlgorithm.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("未知算法: " + args[1] + ", 使用默认算法: KALMAN");
            }
        }

        // 如果没有提供文件路径，生成一个测试文件
        File testFile;
        if (filePath == null || filePath.isEmpty()) {
            System.out.println("未提供文件路径，生成测试文件（500万样本）...");
            testFile = generateTestFile(5000000);
            if (testFile == null) {
                System.err.println("无法生成测试文件");
                return;
            }
        } else {
            testFile = new File(filePath);
            if (!testFile.exists()) {
                System.err.println("文件不存在: " + filePath);
                return;
            }
        }

        double fileSizeMB = testFile.length() / 1024.0 / 1024.0;
        System.out.println("\n" + "=".repeat(70));
        System.out.println("TDMS文件处理速度测试");
        System.out.println("=".repeat(70));
        System.out.println("文件路径: " + testFile.getAbsolutePath());
        System.out.println("文件大小: " + String.format("%.2f", fileSizeMB) + " MB");
        System.out.println("使用算法: " + algorithm.name());
        System.out.println("CPU核心数: " + Runtime.getRuntime().availableProcessors());
        System.out.println("=".repeat(70) + "\n");

        long totalStart = System.currentTimeMillis();

        // 1. 解析阶段
        System.out.println("[1/2] 解析TDMS文件...");
        long parseStart = System.currentTimeMillis();
        FastTdmsParser.FastParsedData data = FastTdmsParser.parse(testFile);
        long parseEnd = System.currentTimeMillis();
        long parseTime = parseEnd - parseStart;
        double parseThroughput = fileSizeMB / (parseTime / 1000.0);

        System.out.println("  ✓ 解析完成");
        System.out.println("    样本数: " + String.format("%,d", data.getSamples().size()));
        System.out.println("    耗时: " + parseTime + " ms");
        System.out.println("    吞吐: " + String.format("%.2f", parseThroughput) + " MB/s\n");

        // 2. Flink处理阶段
        System.out.println("[2/2] Flink处理（滤波+异常检测）...");
        long flinkStart = System.currentTimeMillis();
        
        // 配置Flink
        Configuration flinkConfig = new Configuration();
        try {
            flinkConfig.set(org.apache.flink.configuration.TaskManagerOptions.MEMORY_SEGMENT_SIZE, MemorySize.parse("32kb"));
        } catch (Exception e) {
            // 忽略
        }

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(flinkConfig);
        int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        env.setParallelism(parallelism);
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

        System.out.println("  ✓ 处理完成");
        System.out.println("    并行度: " + parallelism);
        System.out.println("    耗时: " + flinkTime + " ms");
        System.out.println("    吞吐: " + String.format("%.2f", flinkThroughput) + " MB/s");
        System.out.println("    异常点数: " + String.format("%,d", anomalyCount) + "\n");

        // 3. 总体性能
        long totalEnd = System.currentTimeMillis();
        long totalTime = totalEnd - totalStart;
        double totalThroughput = fileSizeMB / (totalTime / 1000.0);

        System.out.println("=".repeat(70));
        System.out.println("性能汇总");
        System.out.println("=".repeat(70));
        System.out.println("总耗时: " + totalTime + " ms (" + String.format("%.2f", totalTime / 1000.0) + " 秒)");
        System.out.println("总吞吐: " + String.format("%.2f", totalThroughput) + " MB/s");
        System.out.println("解析耗时: " + parseTime + " ms (" + String.format("%.1f", parseTime * 100.0 / totalTime) + "%)");
        System.out.println("处理耗时: " + flinkTime + " ms (" + String.format("%.1f", flinkTime * 100.0 / totalTime) + "%)");
        System.out.println("=".repeat(70));

        // 清理临时文件
        if (filePath == null && testFile.exists()) {
            testFile.delete();
        }
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
                "\n" +
                "# 生成测试数据\n" +
                "data = np.random.normal(0, 1, " + sampleCount + ").astype(np.float64)\n" +
                "# 添加一些异常点\n" +
                "anomaly_count = min(1000, " + sampleCount + " // 100)\n" +
                "if anomaly_count > 0:\n" +
                "    anomaly_indices = np.random.choice(" + sampleCount + ", size=anomaly_count, replace=False)\n" +
                "    data[anomaly_indices] = np.random.normal(0, 1, len(anomaly_indices)) * 10 + 50\n" +
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
                
                boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
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
