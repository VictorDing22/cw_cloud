package cn.iocoder.yudao.module.detection.service;

import cn.iocoder.yudao.module.detection.api.DetectionResult;
import cn.iocoder.yudao.module.detection.api.FileChunk;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 实时检测服务本地集成测试
 * 注意：运行此测试需要本机安装 Python 3 以及 nptdms 库 (pip install nptdms numpy)
 */
public class RealTimeDetectionServiceTest {

    @Test
    public void testUploadAndDetect() throws Exception {
        // 设置日志级别为 ERROR，避免 Flink 打印大量调试日志影响性能
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.apache.flink")).setLevel(ch.qos.logback.classic.Level.ERROR);
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.apache.pekko")).setLevel(ch.qos.logback.classic.Level.ERROR);

        // 1. 生成测试 TDMS 文件 (模拟客户端数据)
        File testFile = new File("test_sample.tdms");
        System.out.println("正在生成测试文件: " + testFile.getAbsolutePath());
        generateTdmsFile(testFile);

        // 2. 初始化服务实例 (不依赖 Spring 容器，直接 new)
        RealTimeDetectionServiceImpl service = new RealTimeDetectionServiceImpl();

        // 3. 准备响应观察者 (Client 接收 Server 的响应)
        CountDownLatch latch = new CountDownLatch(1);
        StreamObserver<DetectionResult> responseObserver = new StreamObserver<DetectionResult>() {
            @Override
            public void onNext(DetectionResult value) {
                System.out.println("================ 检测结果 ================");
                System.out.println("Job ID: " + value.getJobId());
                System.out.println("样本总数: " + value.getSampleCount());
                System.out.println("异常点数: " + value.getAnomalyCount());
                System.out.println("处理耗时: " + value.getProcessingTimeMs() + " ms");
                System.out.println("处理吞吐: " + String.format("%.2f", value.getThroughputMbps()) + " MB/s");
                
                if (value.getAnomaliesCount() > 0) {
                    System.out.println("发现前 5 个异常:");
                    value.getAnomaliesList().stream().limit(5).forEach(a -> 
                        System.out.println("  Time: " + a.getTimestamp() + ", Value: " + a.getValue() + ", Energy: " + a.getEnergy())
                    );
                }
                System.out.println("=========================================");
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("RPC 调用出错:");
                t.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("RPC 调用完成");
                latch.countDown();
            }
        };

        // 4. 获取请求观察者 (用于发送数据)
        StreamObserver<FileChunk> requestObserver = service.uploadAndDetect(responseObserver);

        // 5. 模拟流式发送文件数据
        System.out.println("开始上传文件...");
        long start = System.currentTimeMillis();
        try (FileInputStream fis = new FileInputStream(testFile)) {
            byte[] buffer = new byte[1024 * 64]; // 64KB chunk
            int bytesRead;
            boolean first = true;
            while ((bytesRead = fis.read(buffer)) != -1) {
                FileChunk.Builder builder = FileChunk.newBuilder()
                        .setContent(ByteString.copyFrom(buffer, 0, bytesRead));
                
                if (first) {
                    builder.setFilename(testFile.getName());
                    first = false;
                }
                
                requestObserver.onNext(builder.build());
                // 模拟网络延迟（可选）
                // Thread.sleep(1);
            }
        }
        
        // 发送完成信号
        requestObserver.onCompleted();
        long uploadTime = System.currentTimeMillis() - start;
        System.out.println("文件上传完毕，耗时: " + uploadTime + " ms");

        // 6. 等待服务端处理完成
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        if (!completed) {
            throw new RuntimeException("测试超时，未收到服务端响应");
        }
        
        // 清理临时文件
        if (testFile.exists()) {
             testFile.delete();
        }
    }

    /**
     * 调用 Python 脚本生成一个标准的 TDMS 文件
     */
    private void generateTdmsFile(File file) throws IOException, InterruptedException {
        String scriptCode = 
            "import numpy as np\n" +
            "from nptdms import TdmsWriter, ChannelObject, GroupObject\n" +
            "import sys\n" +
            "\n" +
            "try:\n" +
            "    filename = sys.argv[1]\n" +
            "    root_object = GroupObject('Group1')\n" +
            "    # 生成 500万个点 (约 40MB)\n" +
            "    # 制造一些异常点 (值 > 5.0)\n" +
            "    count = 5000000\n" +
            "    data = np.random.normal(0, 1, count).astype(np.float64)\n" +
            "    data[5000:5100] = 20.0  # 注入异常\n" +
            "    \n" +
            "    channel_object = ChannelObject('Group1', 'Channel1', data, properties={\n" +
            "        'wf_increment': 0.0001,\n" +
            "        'wf_start_time': 0\n" +
            "    })\n" +
            "    with TdmsWriter(filename) as tdms_writer:\n" +
            "        tdms_writer.write_segment([root_object, channel_object])\n" +
            "except Exception as e:\n" +
            "    print(e)\n" +
            "    sys.exit(1)\n";

        File scriptFile = File.createTempFile("gen_tdms", ".py");
        java.nio.file.Files.write(scriptFile.toPath(), scriptCode.getBytes());

        ProcessBuilder pb = new ProcessBuilder("python", scriptFile.getAbsolutePath(), file.getAbsolutePath());
        pb.inheritIO();
        Process p = pb.start();
        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("生成测试 TDMS 文件失败。请确认已安装 python 和 nptdms (pip install nptdms numpy)");
        }
        scriptFile.delete();
    }
}
