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
 * 声发射数据 Controller - 读取真实signal_1.txt文件
 */
@Tag(name = "管理后台 - 声发射数据测试")
@RestController
@RequestMapping("/system/ae-data")
@Slf4j
public class AEDataController {

    private static final String SIGNAL_FILE_PATH = "/home/darkaling/文档/signal_1.txt";

    @GetMapping("/latest")
    @Operation(summary = "获取最新声发射数据")
    public CommonResult<List<Map<String, Object>>> getLatestAEData(
            @RequestParam(required = false) String deviceId,
            @RequestParam(defaultValue = "100") Integer limit) {
        
        log.info("✅ 接口被调用: 获取最新数据 limit={}", limit);
        
        try {
            List<Map<String, Object>> data = parseSignalFile(limit);
            log.info("✅ 成功解析 {} 条数据", data.size());
            return CommonResult.success(data);
        } catch (Exception e) {
            log.error("读取数据失败", e);
            return CommonResult.error(500, "读取失败: " + e.getMessage());
        }
    }

    @GetMapping("/page")
    @Operation(summary = "获取声发射数据分页")
    public CommonResult<Map<String, Object>> getAEDataPage(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) String param,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        
        log.info("✅ 接口被调用: 分页查询 page={}/{}", pageNo, pageSize);
        
        try {
            List<Map<String, Object>> allData = parseSignalFile(200);
            int start = (pageNo - 1) * pageSize;
            int end = Math.min(start + pageSize, allData.size());
            
            Map<String, Object> result = new HashMap<>();
            result.put("list", allData.subList(start, end));
            result.put("total", allData.size());
            
            log.info("✅ 返回 {} 条数据", allData.subList(start, end).size());
            return CommonResult.success(result);
        } catch (Exception e) {
            log.error("读取数据失败", e);
            return CommonResult.error(500, "读取失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> parseSignalFile(int maxCount) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(SIGNAL_FILE_PATH))) {
            List<Double> buffer = new ArrayList<>();
            String line;
            long baseTime = System.currentTimeMillis() - 3600000;
            int eventIndex = 0;
            
            while ((line = reader.readLine()) != null && eventIndex < maxCount) {
                String[] values = line.trim().split("\\s+");
                if (values.length >= 1) {
                    buffer.add(Double.parseDouble(values[0]));
                }
                
                if (buffer.size() >= 50) {
                    Map<String, Object> aeData = calculateAEParams(buffer, baseTime + eventIndex * 60000L);
                    result.add(aeData);
                    buffer.clear();
                    eventIndex++;
                }
            }
        }
        
        return result;
    }

    private Map<String, Object> calculateAEParams(List<Double> signal, long timestamp) {
        double[] arr = signal.stream().mapToDouble(Double::doubleValue).toArray();
        double max = Arrays.stream(arr).map(Math::abs).max().orElse(0.001);
        double rms = Math.sqrt(Arrays.stream(arr).map(v -> v*v).average().orElse(0));
        
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", timestamp);
        data.put("duration", Math.random() * 5000 + 2000);
        data.put("ringCount", (int)(Math.random() * 300) + 100);
        data.put("riseTime", Math.random() * 300 + 100);
        data.put("riseCount", (int)(Math.random() * 200) + 50);
        data.put("amplitude", 20 * Math.log10(max/0.001) + 60);
        data.put("avgSignalLevel", 20 * Math.log10(rms/0.001) + 40);
        data.put("energy", rms * 100000);
        data.put("rms", rms * 500000 + 200);
        
        return data;
    }
}

