package cn.iocoder.yudao.module.system.controller.admin.test;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 信号测试 Controller
 * 用于生成模拟的声波信号（正弦波+噪声）
 *
 * @author 芋道源码
 */
@Tag(name = "管理后台 - 信号测试")
@RestController
@RequestMapping("/test/signal")
@Slf4j
public class SignalTestController {

    /**
     * 生成模拟的声波信号
     * 
     * @param length 信号长度（采样点数）
     * @param frequency 频率（Hz）
     * @param amplitude 振幅
     * @param noiseLevel 噪声水平（0-1）
     * @return 信号数据
     */
    @GetMapping("/generate")
    @Operation(summary = "生成模拟声波信号")
    public CommonResult<Map<String, Object>> generateSignal(
            @RequestParam(required = false, defaultValue = "500") Integer length,
            @RequestParam(required = false, defaultValue = "5") Double frequency,
            @RequestParam(required = false, defaultValue = "1.0") Double amplitude,
            @RequestParam(required = false, defaultValue = "0.2") Double noiseLevel) {
        
        log.info("生成模拟声波信号: length={}, frequency={}, amplitude={}, noiseLevel={}", 
                length, frequency, amplitude, noiseLevel);
        
        // 生成时间序列
        List<Double> timePoints = new ArrayList<>();
        List<Double> cleanSignal = new ArrayList<>();
        List<Double> noise = new ArrayList<>();
        List<Double> noisySignal = new ArrayList<>();
        
        Random random = new Random();
        double dt = 1.0 / 100.0; // 采样间隔，假设采样率100Hz
        
        for (int i = 0; i < length; i++) {
            double t = i * dt;
            timePoints.add(t);
            
            // 生成正弦信号
            double clean = amplitude * Math.sin(2 * Math.PI * frequency * t);
            cleanSignal.add(clean);
            
            // 生成高斯噪声
            double n = random.nextGaussian() * noiseLevel;
            noise.add(n);
            
            // 叠加噪声
            double noisy = clean + n;
            noisySignal.add(noisy);
        }
        
        // 计算信号统计特征
        double signalMean = cleanSignal.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double signalMax = cleanSignal.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double signalMin = cleanSignal.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double signalRMS = Math.sqrt(cleanSignal.stream().mapToDouble(v -> v * v).average().orElse(0));
        
        // 计算信噪比（SNR）
        double signalPower = cleanSignal.stream().mapToDouble(v -> v * v).average().orElse(0);
        double noisePower = noise.stream().mapToDouble(v -> v * v).average().orElse(0);
        double snr = 10 * Math.log10(signalPower / noisePower);
        
        // 构造返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("length", length);
        result.put("frequency", frequency);
        result.put("amplitude", amplitude);
        result.put("noiseLevel", noiseLevel);
        result.put("timePoints", timePoints);
        result.put("cleanSignal", cleanSignal);
        result.put("noise", noise);
        result.put("noisySignal", noisySignal);
        
        // 统计信息
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("mean", signalMean);
        statistics.put("max", signalMax);
        statistics.put("min", signalMin);
        statistics.put("rms", signalRMS);
        statistics.put("snr", snr);
        result.put("statistics", statistics);
        
        return CommonResult.success(result);
    }

    /**
     * 生成多频率混合信号
     */
    @GetMapping("/generate-multi")
    @Operation(summary = "生成多频率混合信号")
    public CommonResult<Map<String, Object>> generateMultiFrequencySignal(
            @RequestParam(required = false, defaultValue = "500") Integer length,
            @RequestParam(required = false, defaultValue = "0.3") Double noiseLevel) {
        
        log.info("生成多频率混合信号: length={}, noiseLevel={}", length, noiseLevel);
        
        List<Double> timePoints = new ArrayList<>();
        List<Double> signal = new ArrayList<>();
        Random random = new Random();
        double dt = 1.0 / 100.0;
        
        for (int i = 0; i < length; i++) {
            double t = i * dt;
            timePoints.add(t);
            
            // 混合多个频率的正弦波
            double s = 1.0 * Math.sin(2 * Math.PI * 5 * t) +    // 5Hz
                      0.7 * Math.sin(2 * Math.PI * 10 * t) +   // 10Hz
                      0.5 * Math.sin(2 * Math.PI * 20 * t) +   // 20Hz
                      random.nextGaussian() * noiseLevel;      // 噪声
            
            signal.add(s);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("length", length);
        result.put("timePoints", timePoints);
        result.put("signal", signal);
        result.put("components", Arrays.asList(
            Map.of("frequency", 5.0, "amplitude", 1.0),
            Map.of("frequency", 10.0, "amplitude", 0.7),
            Map.of("frequency", 20.0, "amplitude", 0.5)
        ));
        
        return CommonResult.success(result);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public CommonResult<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("service", "signal-test");
        return CommonResult.success(health);
    }
}

