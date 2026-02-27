package cn.iocoder.yudao.module.detection.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.detection.controller.admin.vo.DetectionTaskVO;
import cn.iocoder.yudao.module.detection.logic.dto.FilterResult;
import cn.iocoder.yudao.module.detection.websocket.DetectionWebSocketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注意：本项目会对 admin 包下的 Controller 自动加上全局前缀 /admin-api
 * 因此这里不要再手动写 /admin-api，避免出现 /admin-api/admin-api 的双前缀。
 */
@Tag(name = "管理后台 - 实时检测")
@RestController
@RequestMapping("/detection/realtime")
public class DetectionController {

    private final Map<String, DetectionTaskVO> TASKS = new ConcurrentHashMap<>();

    @Resource(name = "detectionWebSocketHandler")
    private DetectionWebSocketHandler webSocketHandler;

    @GetMapping("/results")
    @Operation(summary = "获取任务结果（模拟从 TDengine 获取）")
    @PermitAll
    public CommonResult<List<FilterResult>> getTaskResults(@RequestParam("taskId") String taskId) {
        List<FilterResult> results = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            FilterResult r = new FilterResult();
            r.setTimestamp(now + i * 10);
            r.setOriginalValue(Math.sin(i * 0.1) + Math.random() * 0.5);
            r.setFilteredValue(Math.sin(i * 0.1) * 0.8);
            r.setAnomaly(Math.random() > 0.98);
            r.setEnergy(Math.random() * 50);
            results.add(r);
        }
        return CommonResult.success(results);
    }

    @PostMapping("/upload")
    @Operation(summary = "上传 TDMS 文件进行检测")
    @PermitAll
    public CommonResult<DetectionTaskVO> uploadFile(@RequestParam("file") MultipartFile file,
                                      @RequestParam("algorithm") String algorithm) {
        String taskId = UUID.randomUUID().toString();
        DetectionTaskVO task = new DetectionTaskVO();
        task.setId(taskId);
        task.setFilename(file.getOriginalFilename());
        task.setAlgorithm(algorithm);
        task.setStatus("PROCESSING");
        task.setProgress(0);
        task.setSize(String.format("%.2f MB", file.getSize() / 1024.0 / 1024.0));
        task.setSizeBytes(file.getSize());
        task.setStartTime(System.currentTimeMillis());
        task.setLastUpdateTime(task.getStartTime());
        
        TASKS.put(taskId, task);
        
        // 异步模拟处理逻辑
        new Thread(() -> {
            try {
                // 提升“模拟处理”的吞吐：每 200ms 更新一次进度（更平滑，也能体现更高 MB/s）
                for (int i = 0; i <= 100; i += 5) {
                    Thread.sleep(200);
                    task.setProgress(i);
                    task.setLastUpdateTime(System.currentTimeMillis());

                    // 基于当前进度和耗时，计算真实处理速度（MB/s）
                    long elapsedMs = task.getLastUpdateTime() - task.getStartTime();
                    if (elapsedMs > 0 && task.getSizeBytes() != null) {
                        double sizeMB = task.getSizeBytes() / (1024.0 * 1024.0);
                        double processedMB = sizeMB * task.getProgress() / 100.0;
                        double seconds = elapsedMs / 1000.0;
                        double speed = processedMB / seconds;
                        task.setSpeed(String.format("%.2f", speed));
                    }
                    
                    // 模拟实时异常推送
                    if (i % 25 == 0) {
                        FilterResult anomaly = new FilterResult();
                        anomaly.setTimestamp(System.currentTimeMillis());
                        anomaly.setAnomaly(true);
                        anomaly.setEnergy(Math.random() * 100);
                        anomaly.setOriginalValue(Math.random());
                        anomaly.setFilteredValue(Math.random() * 0.5);
                        webSocketHandler.broadcast(anomaly);
                    }
                }
                task.setStatus("COMPLETED");
            } catch (Exception e) {
                task.setStatus("FAILED");
            }
        }).start();

        return CommonResult.success(task);
    }

    @GetMapping("/task-status")
    @Operation(summary = "获取任务状态")
    @PermitAll
    public CommonResult<DetectionTaskVO> getTaskStatus(@RequestParam("taskId") String taskId) {
        return CommonResult.success(TASKS.get(taskId));
    }

    @PostMapping("/push")
    @Operation(summary = "推送检测结果（供 Flink 调用）")
    @PermitAll
    public CommonResult<Boolean> pushResult(@RequestBody FilterResult result) {
        webSocketHandler.broadcast(result);
        return CommonResult.success(true);
    }
}
