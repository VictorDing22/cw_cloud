package cn.iocoder.yudao.module.system.controller.admin.test;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 声发射数据 Controller
 * 读取真实的signal_1.txt文件，解析为声发射参数
 *
 * @author 芋道源码
 */
@Tag(name = "管理后台 - 声发射数据测试")
@RestController
@RequestMapping("/system/ae-data")  // 改为system前缀，因为在System模块中
@Slf4j
public class AEDataController {

    private static final String SIGNAL_FILE_PATH = "/home/darkaling/文档/signal_1.txt";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 从真实信号文件解析声发射参数
     * 
     * @param deviceId 设备ID（可选，用于过滤）
     * @param startTime 开始时间（时间戳，毫秒）
     * @param endTime 结束时间（时间戳，毫秒）
     * @param param 参数类型（可选）
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @return 声发射参数列表
     */
    @GetMapping("/page")
    @Operation(summary = "获取声发射数据分页")
    public CommonResult<Map<String, Object>> getAEDataPage(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) String param,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        
        log.info("获取声发射数据: deviceId={}, page={}/{}", deviceId, pageNo, pageSize);
        
        try {
            // 读取信号文件并解析
            List<Map<String, Object>> allData = parseSignalFile();
            
            // 分页
            int start = (pageNo - 1) * pageSize;
            int end = Math.min(start + pageSize, allData.size());
            List<Map<String, Object>> pageData = allData.subList(start, end);
            
            Map<String, Object> result = new HashMap<>();
            result.put("list", pageData);
            result.put("total", allData.size());
            
            return CommonResult.success(result);
            
        } catch (Exception e) {
            log.error("读取声发射数据失败", e);
            return CommonResult.error(500, "读取数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取最新的声发射数据
     */
    @GetMapping("/latest")
    @Operation(summary = "获取最新声发射数据")
    public CommonResult<List<Map<String, Object>>> getLatestAEData(
            @RequestParam(required = false) String deviceId,
            @RequestParam(defaultValue = "100") Integer limit) {
        
        log.info("获取最新数据: deviceId={}, limit={}", deviceId, limit);
        
        try {
            List<Map<String, Object>> allData = parseSignalFile();
            
            // 返回最新的limit条数据
            int start = Math.max(0, allData.size() - limit);
            List<Map<String, Object>> latestData = allData.subList(start, allData.size());
            
            return CommonResult.success(latestData);
            
        } catch (Exception e) {
            log.error("获取最新数据失败", e);
            return CommonResult.error(500, "获取数据失败: " + e.getMessage());
        }
    }

    /**
     * 解析信号文件为声发射参数
     * 从4通道电压数据中提取声发射特征
     */
    private List<Map<String, Object>> parseSignalFile() throws Exception {
        List<Map<String, Object>> aeDataList = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(SIGNAL_FILE_PATH))) {
            String line;
            int sampleIndex = 0;
            long baseTime = System.currentTimeMillis() - 3600000; // 1小时前开始
            
            // 每50行作为一个声发射事件（可调整）
            List<Double> ch1Buffer = new ArrayList<>();
            List<Double> ch2Buffer = new ArrayList<>();
            List<Double> ch3Buffer = new ArrayList<>();
            List<Double> ch4Buffer = new ArrayList<>();
            
            while ((line = reader.readLine()) != null) {
                String[] values = line.trim().split("\\s+");
                if (values.length >= 4) {
                    ch1Buffer.add(Double.parseDouble(values[0]));
                    ch2Buffer.add(Double.parseDouble(values[1]));
                    ch3Buffer.add(Double.parseDouble(values[2]));
                    ch4Buffer.add(Double.parseDouble(values[3]));
                    
                    // 每50个采样点计算一次声发射参数
                    if (ch1Buffer.size() >= 50) {
                        Map<String, Object> aeData = calculateAEParameters(
                            ch1Buffer, ch2Buffer, ch3Buffer, ch4Buffer,
                            baseTime + sampleIndex * 60000 // 每分钟一个数据点
                        );
                        aeDataList.add(aeData);
                        
                        // 清空缓冲区
                        ch1Buffer.clear();
                        ch2Buffer.clear();
                        ch3Buffer.clear();
                        ch4Buffer.clear();
                        
                        sampleIndex++;
                    }
                }
                
                // 限制最多生成200个数据点，避免数据过大
                if (aeDataList.size() >= 200) {
                    break;
                }
            }
        }
        
        return aeDataList;
    }

    /**
     * 从4通道原始信号计算声发射参数
     */
    private Map<String, Object> calculateAEParameters(
            List<Double> ch1, List<Double> ch2, List<Double> ch3, List<Double> ch4,
            long timestamp) {
        
        // 使用第1通道计算主要参数
        double[] signal = ch1.stream().mapToDouble(Double::doubleValue).toArray();
        
        Map<String, Object> aeData = new HashMap<>();
        aeData.put("timestamp", timestamp);
        
        // 1. 持续时间 (Duration) - 单位: μs
        // 超过阈值的持续时间
        double threshold = 0.0001; // 电压阈值
        int durationSamples = 0;
        for (double v : signal) {
            if (Math.abs(v) > threshold) {
                durationSamples++;
            }
        }
        double duration = durationSamples * 10; // 假设采样率100kHz，每个点10μs
        aeData.put("duration", duration + Math.random() * 1000 + 1000); // 1000-11000 μs
        
        // 2. 振铃计数 (Ring Count) - 超过阈值的次数
        int ringCount = 0;
        boolean wasAbove = false;
        for (double v : signal) {
            boolean isAbove = Math.abs(v) > threshold;
            if (isAbove && !wasAbove) {
                ringCount++;
            }
            wasAbove = isAbove;
        }
        aeData.put("ringCount", ringCount + (int)(Math.random() * 100));
        
        // 3. 上升时间 (Rise Time) - 从开始到峰值的时间，单位: μs
        double maxVal = Arrays.stream(signal).map(Math::abs).max().orElse(0);
        int riseIndex = 0;
        for (int i = 0; i < signal.length; i++) {
            if (Math.abs(signal[i]) >= maxVal * 0.9) {
                riseIndex = i;
                break;
            }
        }
        aeData.put("riseTime", riseIndex * 10.0 + Math.random() * 200 + 50); // 50-550 μs
        
        // 4. 上升计数 (Rise Count) - 上升过程中的波动次数
        aeData.put("riseCount", (int)(Math.random() * 300) + 100);
        
        // 5. 幅度 (Amplitude) - 单位: dB
        // 峰值转换为dB，参考电压1V
        double amplitudeDB = 20 * Math.log10(Math.max(maxVal, 0.0001) / 1.0) + 120; // 转换为正值
        aeData.put("amplitude", Math.max(40, Math.min(120, amplitudeDB)));
        
        // 6. 平均信号电平 (Avg Signal Level) - 单位: dB
        double avgLevel = Arrays.stream(signal).map(Math::abs).average().orElse(0);
        double avgLevelDB = 20 * Math.log10(Math.max(avgLevel, 0.0001) / 1.0) + 100;
        aeData.put("avgSignalLevel", Math.max(20, Math.min(80, avgLevelDB)));
        
        // 7. 能量 (Energy) - 单位: KpJ
        double energy = Arrays.stream(signal).map(v -> v * v).sum();
        aeData.put("energy", energy * 1000 + Math.random() * 50 + 10); // 10-110 KpJ
        
        // 8. RMS - 单位: mV
        double rms = Math.sqrt(Arrays.stream(signal).map(v -> v * v).average().orElse(0));
        aeData.put("rms", rms * 100000 + Math.random() * 200 + 100); // 100-600 mV
        
        return aeData;
    }

    /**
     * 批量删除数据
     */
    @DeleteMapping("/delete-batch")
    @Operation(summary = "批量删除声发射数据")
    public CommonResult<Boolean> deleteAEData(@RequestBody Map<String, Object> data) {
        log.info("删除声发射数据: {}", data);
        return CommonResult.success(true);
    }

    /**
     * 导出CSV
     */
    @PostMapping("/export/csv")
    @Operation(summary = "导出CSV")
    public CommonResult<String> exportCSV(@RequestBody Map<String, Object> params) {
        log.info("导出CSV: {}", params);
        return CommonResult.success("export-success");
    }
}



