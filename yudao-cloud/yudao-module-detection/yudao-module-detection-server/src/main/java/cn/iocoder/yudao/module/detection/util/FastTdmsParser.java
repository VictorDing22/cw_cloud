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
    private static volatile File cachedScriptFile;
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB缓冲区

    @Data
    public static class FastParsedData {
        private String channelName;
        private double sampleRate;
        private long startTimestamp;
        private List<TdmsSample> samples;
    }

    public static FastParsedData parse(File tdmsFile) throws IOException {
        File scriptFile = getOrCreateScript();
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "-X", "utf8",
                    scriptFile.getAbsolutePath(), tdmsFile.getAbsolutePath());
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = pb.start();

            return readProcessOutput(process, tdmsFile);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("解析被中断", e);
        }
        // 不再删除脚本文件，保持缓存
    }
    
    /**
     * 获取或创建缓存的Python脚本
     */
    private static synchronized File getOrCreateScript() throws IOException {
        if (cachedScriptFile == null || !cachedScriptFile.exists()) {
            cachedScriptFile = createPythonScript();
            // 标记为程序退出时删除
            cachedScriptFile.deleteOnExit();
            }
        return cachedScriptFile;
    }

    static FastParsedData readProcessOutput(Process process, File tdmsFile) throws IOException, InterruptedException {
        // 使用更大的缓冲区提升I/O性能
        BufferedInputStream bufferedIn = new BufferedInputStream(process.getInputStream(), BUFFER_SIZE);
        
        // 1. 读取元数据行（优化：使用BufferedReader提升读取效率）
        BufferedReader reader = new BufferedReader(new InputStreamReader(bufferedIn, StandardCharsets.UTF_8), BUFFER_SIZE);
        String metaJson = reader.readLine();
        
        if (metaJson == null || metaJson.isEmpty()) {
            throw new IOException("Python 输出为空");
        }
        
        JsonNode meta = objectMapper.readTree(metaJson);
        
        String channelName = meta.get("name").asText();
        double sampleRate = meta.get("sampleRate").asDouble();
        long startTimestamp = meta.get("startTimestamp").asLong();
        int count = meta.get("count").asInt();

        // 2. 读取二进制 Double 数组（使用更大的缓冲区和批量读取优化）
        DataInputStream dis = new DataInputStream(new BufferedInputStream(bufferedIn, BUFFER_SIZE));
        
        // 预分配列表容量，减少扩容开销
        List<TdmsSample> samples = new ArrayList<>(count);
        
        // 批量读取优化：一次读取多个double值
        byte[] buffer = new byte[8 * Math.min(1024, count)]; // 每次最多读取1024个double
        int remaining = count;
        int index = 0;
        
        while (remaining > 0) {
            int batchSize = Math.min(buffer.length / 8, remaining);
            int bytesToRead = batchSize * 8;
            
            int bytesRead = dis.read(buffer, 0, bytesToRead);
            if (bytesRead == -1) {
                break;
            }
            
            // 处理读取到的数据（Big Endian格式）
            int doublesRead = bytesRead / 8;
            for (int i = 0; i < doublesRead; i++) {
                long bits = ((long)(buffer[i*8] & 0xFF) << 56) |
                           ((long)(buffer[i*8+1] & 0xFF) << 48) |
                           ((long)(buffer[i*8+2] & 0xFF) << 40) |
                           ((long)(buffer[i*8+3] & 0xFF) << 32) |
                           ((long)(buffer[i*8+4] & 0xFF) << 24) |
                           ((long)(buffer[i*8+5] & 0xFF) << 16) |
                           ((long)(buffer[i*8+6] & 0xFF) << 8) |
                           ((long)(buffer[i*8+7] & 0xFF));
                double value = Double.longBitsToDouble(bits);
                
                // 计算时间戳: start + i * (1000 / rate)
                long ts = startTimestamp + (long) (index * 1000.0 / sampleRate);
                samples.add(new TdmsSample(ts, value, channelName));
                index++;
            }
            
            remaining -= doublesRead;
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Python 进程异常退出: " + exitCode);
        }

        FastParsedData result = new FastParsedData();
        result.setChannelName(channelName);
        result.setStartTimestamp(startTimestamp);
        result.setSampleRate(sampleRate);
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
