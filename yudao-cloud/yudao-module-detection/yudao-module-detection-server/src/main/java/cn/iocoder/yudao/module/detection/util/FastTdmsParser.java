package cn.iocoder.yudao.module.detection.util;

import cn.iocoder.yudao.module.detection.api.dto.TdmsSample;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FastTdmsParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    public static class FastParsedData {
        private String channelName;
        private double sampleRate;
        private long startTimestamp;
        private List<TdmsSample> samples;
    }

    public static FastParsedData parse(File tdmsFile) throws IOException {
        File scriptFile = createPythonScript();
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "-X", "utf8",
                    scriptFile.getAbsolutePath(), tdmsFile.getAbsolutePath());
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = pb.start();

            return readProcessOutput(process, tdmsFile);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("解析被中断", e);
        } finally {
            if (scriptFile.exists()) {
                scriptFile.delete();
            }
        }
    }

    private static FastParsedData readProcessOutput(Process process, File tdmsFile) throws IOException, InterruptedException {
        InputStream in = process.getInputStream();
        
        // 1. 读取元数据行
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            lineBuffer.write(b);
        }
        
        if (lineBuffer.size() == 0) {
            throw new IOException("Python 输出为空");
        }
        
        String metaJson = lineBuffer.toString(StandardCharsets.UTF_8.name());
        JsonNode meta = objectMapper.readTree(metaJson);
        
        String channelName = meta.get("name").asText();
        double sampleRate = meta.get("sampleRate").asDouble();
        long startTimestamp = meta.get("startTimestamp").asLong();
        int count = meta.get("count").asInt();

        // 2. 读取二进制 Double 数组
        // data.tofile() 输出的是机器字节序，通常是 Little Endian (Intel)。
        // Java DataInputStream 是 Big Endian。需要转换。
        // 或者让 Python 输出 Big Endian: data.astype('>f8').tofile(...)
        
        List<TdmsSample> samples = new ArrayList<>(count);
        DataInputStream dis = new DataInputStream(new BufferedInputStream(in));
        
        // 假设 Python 端输出了 Big Endian 的 double
        for (int i = 0; i < count; i++) {
            try {
                double value = dis.readDouble();
                // 计算时间戳: start + i * (1000 / rate)
                // 注意 sampleRate 是 Hz (每秒点数)
                // 间隔 ms = 1000.0 / sampleRate
                long ts = startTimestamp + (long) (i * 1000.0 / sampleRate);
                samples.add(new TdmsSample(ts, value, channelName));
            } catch (EOFException e) {
                break;
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Python 进程异常退出: " + exitCode);
        }

        FastParsedData result = new FastParsedData();
        result.setChannelName(channelName);
        result.setSampleRate(sampleRate);
        result.setStartTimestamp(startTimestamp);
        result.setSamples(samples);
        return result;
    }

    private static File createPythonScript() throws IOException {
        File script = File.createTempFile("fast_tdms_parser", ".py");
        try (PrintWriter writer = new PrintWriter(script, StandardCharsets.UTF_8.name())) {
            writer.println("import sys");
            writer.println("import json");
            writer.println("try:");
            writer.println("    from nptdms import TdmsFile");
            writer.println("    import numpy as np");
            writer.println("except ImportError:");
            writer.println("    sys.exit(1)");
            writer.println("");
            writer.println("path = sys.argv[1]");
            writer.println("try:");
            writer.println("    tdms = TdmsFile.read(path)");
            writer.println("    channel = None");
            writer.println("    for g in tdms.groups():");
            writer.println("        chs = g.channels()");
            writer.println("        if chs:");
            writer.println("            channel = chs[0]");
            writer.println("            break");
            writer.println("    if not channel:");
            writer.println("        sys.exit(2)");
            writer.println("");
            writer.println("    data = channel.data");
            writer.println("    props = channel.properties");
            writer.println("    sample_rate = props.get('wf_increment', 1.0)");
            writer.println("    # 时间转换需要处理，这里简化，假设 start_time 属性存在或使用 0");
            writer.println("    # nptdms 的 time_track() 比较慢，我们自己算");
            writer.println("    # 尝试获取 start_time");
            writer.println("    start_time_prop = props.get('wf_start_time')");
            writer.println("    start_ts = 0");
            writer.println("    if start_time_prop:");
            writer.println("        # npTDMS datetime 转换");
            writer.println("        try:");
            writer.println("            start_ts = int(start_time_prop.timestamp() * 1000)");
            writer.println("        except:");
            writer.println("            pass");
            writer.println("");
            writer.println("    # 构造元数据");
            writer.println("    meta = {");
            writer.println("        'name': channel.name,");
            writer.println("        'sampleRate': 1.0 / sample_rate if sample_rate < 1 else sample_rate,");
            writer.println("        'startTimestamp': start_ts,");
            writer.println("        'count': len(data)");
            writer.println("    }");
            writer.println("    sys.stdout.write(json.dumps(meta) + '\\n')");
            writer.println("    sys.stdout.flush()");
            writer.println("");
            writer.println("    # 输出二进制数据 (Big Endian double)");
            writer.println("    # 必须确保 data 是 float64 类型");
            writer.println("    if data.dtype != np.float64:");
            writer.println("        data = data.astype(np.float64)");
            writer.println("    # 转换为 Big Endian");
            writer.println("    data.astype('>f8').tofile(sys.stdout.buffer)");
            writer.println("except Exception as e:");
            writer.println("    sys.stderr.write(str(e))");
            writer.println("    sys.exit(3)");
        }
        return script;
    }
}
