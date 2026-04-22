package cn.iocoder.yudao.module.system.controller.admin.test;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

/**
 * 真实信号读取 Controller
 * 读取真实的4通道声发射信号数据
 *
 * @author 芋道源码
 */
@Tag(name = "管理后台 - 真实信号读取")
@RestController
@RequestMapping("/test/real-signal")
@Slf4j
public class RealSignalController {

    private static final String SIGNAL_FILE_PATH = "/home/darkaling/文档/signal_1.txt";

    /**
     * 读取4通道声发射信号数据
     *
     * @param startIndex 起始索引（默认0）
     * @param count 读取数量（默认500，最大2000）
     * @return 4通道信号数据
     */
    @GetMapping("/read")
    @Operation(summary = "读取4通道声发射信号")
    public CommonResult<Map<String, Object>> readSignal(
            @RequestParam(required = false, defaultValue = "0") Integer startIndex,
            @RequestParam(required = false, defaultValue = "500") Integer count) {
        
        log.info("读取真实信号: startIndex={}, count={}", startIndex, count);
        
        // 限制读取数量，避免数据过大
        if (count > 2000) {
            count = 2000;
        }
        
        List<Double> channel1 = new ArrayList<>();
        List<Double> channel2 = new ArrayList<>();
        List<Double> channel3 = new ArrayList<>();
        List<Double> channel4 = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(SIGNAL_FILE_PATH))) {
            String line;
            int currentIndex = 0;
            int readCount = 0;
            
            while ((line = reader.readLine()) != null && readCount < count) {
                // 跳过起始索引之前的行
                if (currentIndex < startIndex) {
                    currentIndex++;
                    continue;
                }
                
                // 解析每行的4个通道数据
                String[] values = line.trim().split("\\s+");
                if (values.length >= 4) {
                    indices.add(currentIndex);
                    channel1.add(Double.parseDouble(values[0]));
                    channel2.add(Double.parseDouble(values[1]));
                    channel3.add(Double.parseDouble(values[2]));
                    channel4.add(Double.parseDouble(values[3]));
                    readCount++;
                }
                currentIndex++;
            }
            
            // 计算每个通道的统计特征
            Map<String, Object> channel1Stats = calculateStatistics(channel1, "通道1");
            Map<String, Object> channel2Stats = calculateStatistics(channel2, "通道2");
            Map<String, Object> channel3Stats = calculateStatistics(channel3, "通道3");
            Map<String, Object> channel4Stats = calculateStatistics(channel4, "通道4");
            
            // 构造返回数据
            Map<String, Object> result = new HashMap<>();
            result.put("totalLines", getTotalLines());
            result.put("startIndex", startIndex);
            result.put("readCount", readCount);
            result.put("indices", indices);
            result.put("channel1", channel1);
            result.put("channel2", channel2);
            result.put("channel3", channel3);
            result.put("channel4", channel4);
            result.put("statistics", Map.of(
                "channel1", channel1Stats,
                "channel2", channel2Stats,
                "channel3", channel3Stats,
                "channel4", channel4Stats
            ));
            
            log.info("成功读取 {} 行数据", readCount);
            return CommonResult.success(result);
            
        } catch (Exception e) {
            log.error("读取信号文件失败", e);
            return CommonResult.error(500, "读取文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件总行数
     */
    @GetMapping("/info")
    @Operation(summary = "获取信号文件信息")
    public CommonResult<Map<String, Object>> getFileInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("filePath", SIGNAL_FILE_PATH);
        info.put("totalLines", getTotalLines());
        info.put("channels", 4);
        info.put("description", "4通道声发射信号数据");
        
        return CommonResult.success(info);
    }

    /**
     * 获取文件总行数
     */
    private int getTotalLines() {
        try (BufferedReader reader = new BufferedReader(new FileReader(SIGNAL_FILE_PATH))) {
            int lines = 0;
            while (reader.readLine() != null) {
                lines++;
            }
            return lines;
        } catch (Exception e) {
            log.error("读取文件行数失败", e);
            return 0;
        }
    }

    /**
     * 计算统计特征
     */
    private Map<String, Object> calculateStatistics(List<Double> data, String channelName) {
        if (data.isEmpty()) {
            return Map.of("name", channelName);
        }
        
        DoubleSummaryStatistics stats = data.stream()
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();
        
        // 计算RMS
        double rms = Math.sqrt(data.stream()
                .mapToDouble(v -> v * v)
                .average()
                .orElse(0));
        
        // 计算标准差
        double mean = stats.getAverage();
        double variance = data.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0);
        double std = Math.sqrt(variance);
        
        Map<String, Object> result = new HashMap<>();
        result.put("name", channelName);
        result.put("count", stats.getCount());
        result.put("mean", mean);
        result.put("max", stats.getMax());
        result.put("min", stats.getMin());
        result.put("rms", rms);
        result.put("std", std);
        
        return result;
    }
}

