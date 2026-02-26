package cn.iocoder.yudao.module.detection.flink;

import cn.iocoder.yudao.module.detection.api.FilterAlgorithm;
import cn.iocoder.yudao.module.detection.api.dto.TdmsSample;
import cn.iocoder.yudao.module.detection.logic.GenericFilterProcessFunction;
import cn.iocoder.yudao.module.detection.logic.dto.FilterResult;
import cn.iocoder.yudao.module.detection.sink.TDengineSink;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

/**
 * Flink 实时处理任务：滤波 + 异常检测
 */
@Slf4j
public class DetectionFlinkJob {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        String host = args.length > 0 ? args[0] : "yudao-module-detection-server-svc";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9998;

        // 1. Source: 从 Netty 数据端口获取数据 (TCP Client)
        DataStream<TdmsSample> source = env.addSource(new SourceFunction<TdmsSample>() {
            private volatile boolean running = true;
            private java.net.Socket socket;

            @Override
            public void run(SourceContext<TdmsSample> ctx) throws Exception {
                while (running) {
                    try {
                        log.info("尝试连接到数据源 {}:{}", host, port);
                        socket = new java.net.Socket(host, port);
                        java.io.InputStream is = socket.getInputStream();
                        java.io.DataInputStream dis = new java.io.DataInputStream(is);

                        while (running) {
                            int totalLen = dis.readInt();
                            long timestamp = dis.readLong();
                            int nameLen = dis.readInt();
                            byte[] nameBytes = new byte[nameLen];
                            dis.readFully(nameBytes);
                            String channel = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
                            double value = dis.readDouble();

                            ctx.collect(new TdmsSample(timestamp, value, channel));
                        }
                    } catch (Exception e) {
                        log.error("数据源连接断开，5秒后重试...", e);
                        Thread.sleep(5000);
                    }
                }
            }

            @Override
            public void cancel() {
                running = false;
                try {
                    if (socket != null) socket.close();
                } catch (java.io.IOException e) {
                    // ignore
                }
            }
        }).name("NettyBinarySource");

        // 2. Process: 滤波 + 异常检测
        DataStream<FilterResult> processed = source
                .keyBy(TdmsSample::getChannel)
                .process(new GenericFilterProcessFunction(
                        5000L, // 5s window
                        3.0,   // 3 sigma threshold
                        true,  // anomaly detection enabled
                        FilterAlgorithm.KALMAN // Default algorithm
                )).name("FilterAndDetection");

        // 3. Sink: 写入 TDengine
        processed.addSink(TDengineSink.getSink()).name("TDengineSink");

        // 4. Sink: 推送实时数据到前端 WebSocket (通过 Server 代理)
        processed.map(result -> {
            try {
                // 生产环境建议使用异步 HTTP 客户端或 MQ
                java.net.URL url = new java.net.URL("http://yudao-module-detection-server-svc:48080/detection/realtime/push");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                String json = String.format("{\"timestamp\":%d, \"channel\":\"%s\", \"originalValue\":%f, \"filteredValue\":%f, \"anomaly\":%b, \"energy\":%f, \"snrAfterDb\":%f}",
                        result.getTimestamp(), result.getChannel(), result.getOriginalValue(), 
                        result.getFilteredValue(), result.isAnomaly(), result.getEnergy(), result.getSnrAfterDb());
                
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes());
                }
                conn.getResponseCode();
            } catch (Exception e) {
                // ignore
            }
            return result;
        }).name("UIPushSink");

        env.execute("DetectionFlinkJob");
    }
}
