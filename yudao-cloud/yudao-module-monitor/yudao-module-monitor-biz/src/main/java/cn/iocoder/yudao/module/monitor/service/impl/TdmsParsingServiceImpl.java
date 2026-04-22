package cn.iocoder.yudao.module.monitor.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.module.monitor.api.dto.TdmsChannelMetadata;
import cn.iocoder.yudao.module.monitor.api.dto.TdmsSample;
import cn.iocoder.yudao.module.monitor.service.TdmsParsingService;
import cn.iocoder.yudao.module.monitor.service.dto.ParsedTdmsData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * TDMS 解析实现。为保证 100% 基于实际文件，这里借助 Python 的 nptdms 解析器。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TdmsParsingServiceImpl implements TdmsParsingService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ParsedTdmsData parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ServiceExceptionUtil.invalidParamException("请上传 TDMS 文件");
        }
        if (!file.getOriginalFilename().toLowerCase().endsWith(".tdms")) {
            throw ServiceExceptionUtil.invalidParamException("仅支持 .tdms 文件");
        }

        File tempFile = null;
        File scriptFile = null;
        try {
            tempFile = Files.createTempFile("monitor-", ".tdms").toFile();
            file.transferTo(tempFile);

            scriptFile = writeParserScript();
            String json = runPythonParser(scriptFile, tempFile);

            JsonNode root = objectMapper.readTree(json);
            ParsedTdmsData parsed = new ParsedTdmsData();

            TdmsChannelMetadata meta = new TdmsChannelMetadata();
            JsonNode channelNode = root.get("channel");
            meta.setName(channelNode.get("name").asText());
            meta.setUnit(channelNode.path("unit").asText("Amplitude"));
            meta.setSampleRate(channelNode.path("sampleRate").asDouble());
            meta.setStartTimestamp(channelNode.path("startTimestamp").asLong());
            meta.setEndTimestamp(channelNode.path("endTimestamp").asLong());
            meta.setSampleCount(channelNode.path("sampleCount").asLong());
            parsed.setChannel(meta);

            List<TdmsSample> samples = new ArrayList<>();
            for (JsonNode node : root.withArray("samples")) {
                samples.add(new TdmsSample(
                        node.get("timestamp").asLong(),
                        node.get("value").asDouble(),
                        meta.getName()
                ));
            }
            parsed.setSamples(samples);
            return parsed;
        } catch (IOException e) {
            log.error("解析 TDMS 文件失败: {}", e.getMessage(), e);
            // 这里属于“文件无法解析/环境缺失”的可预期失败，返回 400，前端可直接展示原因
            throw ServiceExceptionUtil.exception(
                    GlobalErrorCodeConstants.BAD_REQUEST,
                    buildUserFriendlyError(e.getMessage()));
        } finally {
            if (tempFile != null) {
                FileUtil.del(tempFile);
            }
            if (scriptFile != null) {
                FileUtil.del(scriptFile);
            }
        }
    }

    private File writeParserScript() throws IOException {
        File script = Files.createTempFile("tdms-parser-", ".py").toFile();
        // 注意：这里显式按行 append，保证写入的是“真正的换行符”，而不是包含 \\n 的单行脚本。
        StringBuilder sb = new StringBuilder();
        sb.append("# -*- coding: utf-8 -*-\n");
        sb.append("import json\n");
        sb.append("import sys\n");
        sb.append("\n");
        sb.append("MAX_SAMPLES = 200000  # 限制回传的最大样本点数，仅影响传输体积，不影响原始文件\n");
        sb.append("\n");
        sb.append("try:\n");
        sb.append("    from nptdms import TdmsFile\n");
        sb.append("except ImportError as ex:\n");
        sb.append("    sys.stderr.write('nptdms 未安装: %s\\n' % ex)\n");
        sb.append("    sys.exit(2)\n");
        sb.append("\n");
        sb.append("if len(sys.argv) < 2:\n");
        sb.append("    sys.stderr.write('缺少文件参数\\n')\n");
        sb.append("    sys.exit(1)\n");
        sb.append("\n");
        sb.append("path = sys.argv[1]\n");
        sb.append("tdms = TdmsFile.read(path)\n");
        sb.append("groups = tdms.groups()\n");
        sb.append("\n");
        sb.append("if len(groups) == 0:\n");
        sb.append("    sys.stderr.write('TDMS 无通道\\n')\n");
        sb.append("    sys.exit(3)\n");
        sb.append("\n");
        sb.append("channel = None\n");
        sb.append("for g in groups:\n");
        sb.append("    chs = g.channels()\n");
        sb.append("    if chs:\n");
        sb.append("        channel = chs[0]\n");
        sb.append("        break\n");
        sb.append("\n");
        sb.append("if channel is None:\n");
        sb.append("    sys.stderr.write('TDMS 未找到有效通道\\n')\n");
        sb.append("    sys.exit(4)\n");
        sb.append("\n");
        sb.append("data = channel.data\n");
        sb.append("times = channel.time_track()\n");
        sb.append("unit = channel.properties.get('unit_string') or channel.properties.get('NI_UnitDescription') or 'Amplitude'\n");
        sb.append("sample_rate = channel.properties.get('wf_increment')\n");
        sb.append("\n");
        sb.append("samples = []\n");
        sb.append("start_ts = int(times[0] * 1000) if len(times) else 0\n");
        sb.append("end_ts = int(times[-1] * 1000) if len(times) else 0\n");
        sb.append("count = 0\n");
        sb.append("for t, v in zip(times, data):\n");
        sb.append("    if count >= MAX_SAMPLES:\n");
        sb.append("        break\n");
        sb.append("    samples.append({'timestamp': int(t * 1000), 'value': float(v)})\n");
        sb.append("    count += 1\n");
        sb.append("\n");
        sb.append("payload = {\n");
        sb.append("    'channel': {\n");
        sb.append("        'name': channel.name,\n");
        sb.append("        'unit': unit,\n");
        sb.append("        'sampleRate': float(sample_rate) if sample_rate is not None else None,\n");
        sb.append("        'startTimestamp': start_ts,\n");
        sb.append("        'endTimestamp': end_ts,\n");
        sb.append("        'sampleCount': len(data)\n");
        sb.append("    },\n");
        sb.append("    'samples': samples\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("json.dump(payload, sys.stdout)\n");

        try (FileOutputStream out = new FileOutputStream(script)) {
            IOUtils.write(sb.toString(), out, StandardCharsets.UTF_8);
        }
        return script;
    }

    private String runPythonParser(File scriptFile, File tdmsFile) throws IOException {
        // Windows 下默认编码可能导致中文乱码；强制 UTF-8 输出
        ProcessBuilder pb = new ProcessBuilder("python", "-X", "utf8",
                scriptFile.getAbsolutePath(), tdmsFile.getAbsolutePath());
        pb.environment().put("PYTHONUTF8", "1");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
        try {
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("Python 解析退出码 " + exit + " 输出: " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("解析中断", e);
        }
        return output;
    }

    private static String buildUserFriendlyError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "TDMS 解析失败，请确认文件有效且已安装 Python3 + nptdms";
        }
        // 只截取核心信息，避免把整段堆栈/脚本内容返回给前端
        String msg = raw;
        int idx = msg.indexOf("输出:");
        if (idx >= 0) {
            msg = msg.substring(0, idx + 3) + " " + msg.substring(idx + 3).trim();
        }
        if (msg.length() > 800) {
            msg = msg.substring(0, 800) + "...";
        }
        // 统一前缀，便于前端 toast 展示
        return "TDMS 解析失败：" + msg;
    }
}
