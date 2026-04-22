package cn.iocoder.yudao.module.detection;

import cn.iocoder.yudao.module.detection.api.FilterAlgorithm;
import cn.iocoder.yudao.module.detection.api.dto.TdmsSample;
import cn.iocoder.yudao.module.detection.logic.GenericFilterProcessFunction;
import cn.iocoder.yudao.module.detection.logic.dto.FilterResult;
import cn.iocoder.yudao.module.detection.util.FastTdmsParser;
import org.apache.flink.util.Collector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * TDMS 处理速度基准测试 (本地逻辑验证)
 */
public class SpeedTest {

    public static void main(String[] args) throws Exception {
        // 1. 生成测试 TDMS 文件 (500万个点, 约 40MB)
        File testFile = new File("speed_test.tdms");
        generateDummyTdms(testFile, 5000000);
        
        System.out.println("测试文件已准备: " + testFile.getAbsolutePath() + " (" + (testFile.length() / 1024 / 1024) + " MB)");

        // 2. 解析文件
        long startParse = System.currentTimeMillis();
        FastTdmsParser.FastParsedData data = FastTdmsParser.parse(testFile);
        long endParse = System.currentTimeMillis();
        System.out.println("TDMS 解析完成, 耗时: " + (endParse - startParse) + " ms");

        // 3. 模拟 Flink 处理逻辑进行基准测试
        GenericFilterProcessFunction function = new GenericFilterProcessFunction(500, 3.0, true, FilterAlgorithm.KALMAN);
        // 手动初始化 function (模拟 open)
        function.open(new org.apache.flink.configuration.Configuration());

        List<FilterResult> results = new ArrayList<>();
        Collector<FilterResult> collector = new Collector<FilterResult>() {
            @Override
            public void collect(FilterResult record) {
                if (record.isAnomaly()) {
                    results.add(record);
                }
            }
            @Override
            public void close() {}
        };

        System.out.println("开始核心算法基准测试...");
        long startProcess = System.currentTimeMillis();
        
        for (TdmsSample sample : data.getSamples()) {
            function.processElement(sample, null, collector);
        }
        
        long endProcess = System.currentTimeMillis();
        double totalTimeMs = endProcess - startProcess;
        double throughput = (testFile.length() / 1024.0 / 1024.0) / (totalTimeMs / 1000.0);

        System.out.println("\n========================================");
        System.out.println("TDMS 处理速度基准测试结果");
        System.out.println("----------------------------------------");
        System.out.println("样本总数: " + data.getSamples().size());
        System.out.println("检测到异常点: " + results.size());
        System.out.println("算法耗时: " + totalTimeMs + " ms");
        System.out.println("处理吞吐: " + String.format("%.2f", throughput) + " MB/s");
        System.out.println("========================================");

        // 清理
        testFile.delete();
        System.exit(0);
    }

    private static void generateDummyTdms(File file, int count) throws IOException {
        String script = 
            "import numpy as np\n" +
            "from nptdms import TdmsWriter, ChannelObject, GroupObject\n" +
            "import sys\n" +
            "data = np.random.normal(0, 1, " + count + ").astype(np.float64)\n" +
            "data[1000:1100] = 50.0\n" +
            "c = ChannelObject('G', 'C', data)\n" +
            "with TdmsWriter(sys.argv[1]) as w:\n" +
            "    w.write_segment([GroupObject('G'), c])\n";
        
        File pyScript = new File("gen_speed_test.py");
        Files.write(pyScript.toPath(), script.getBytes());
        
        try {
            ProcessBuilder pb = new ProcessBuilder("python", pyScript.getAbsolutePath(), file.getAbsolutePath());
            pb.inheritIO();
            pb.start().waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            pyScript.delete();
        }
    }
}
