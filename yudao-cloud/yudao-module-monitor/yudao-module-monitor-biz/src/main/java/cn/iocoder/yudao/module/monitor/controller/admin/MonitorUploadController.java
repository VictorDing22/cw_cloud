package cn.iocoder.yudao.module.monitor.controller.admin;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.module.monitor.api.dto.FilterConfig;
import cn.iocoder.yudao.module.monitor.api.dto.FilterType;
import cn.iocoder.yudao.module.monitor.api.dto.MonitorUploadResponse;
import cn.iocoder.yudao.module.monitor.api.dto.HistoryAnalysisResult;
import cn.iocoder.yudao.module.monitor.api.dto.TdmsChannelMetadata;
import cn.iocoder.yudao.module.monitor.service.FlinkPlaybackService;
import cn.iocoder.yudao.module.monitor.service.HistoryAnalysisService;
import cn.iocoder.yudao.module.monitor.service.TdmsParsingService;
import cn.iocoder.yudao.module.monitor.service.dto.ParsedTdmsData;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/monitor")
@Validated
@Tag(name = "TDMS 实时检测")
@RequiredArgsConstructor
@Slf4j
public class MonitorUploadController {

    private final TdmsParsingService tdmsParsingService;
    private final FlinkPlaybackService flinkPlaybackService;
    private final HistoryAnalysisService historyAnalysisService;

    @PostMapping("/upload")
    @Operation(summary = "上传 TDMS 文件并启动实时处理")
    public CommonResult<MonitorUploadResponse> upload(@RequestParam(value = "file", required = false) MultipartFile file,
                                                      @RequestParam(value = "anomalyThreshold", required = false) Double threshold,
                                                      @RequestParam(value = "anomalyEnabled", defaultValue = "true") boolean anomalyEnabled,
                                                      @RequestParam(value = "filterType", required = false, defaultValue = "KALMAN") FilterType filterType,
                                                      @RequestParam(value = "kalmanQ", required = false, defaultValue = "1e-5") Double kalmanQ,
                                                      @RequestParam(value = "kalmanR", required = false, defaultValue = "0.1") Double kalmanR,
                                                      @RequestParam(value = "kalmanP0", required = false, defaultValue = "1.0") Double kalmanP0,
                                                      @RequestParam(value = "kalmanX0N", required = false, defaultValue = "10") Integer kalmanX0N) {
        if (file == null || file.isEmpty()) {
            throw ServiceExceptionUtil.invalidParamException("请先上传有效 TDMS 文件");
        }
        String fileName = file.getOriginalFilename();
        log.info("开始处理上传文件: {}", fileName);

        ParsedTdmsData data = tdmsParsingService.parse(file);
        TdmsChannelMetadata meta = data.getChannel();

        String jobId = IdUtil.fastSimpleUUID();
        double autoThreshold = threshold != null ? threshold : calculateEnergyThreshold(data);
        log.info("准备提交 Flink 作业, jobId={}, threshold={}, anomalyEnabled={}", jobId, autoThreshold, anomalyEnabled);
        try {
            FilterConfig filterConfig = new FilterConfig();
            filterConfig.setType(filterType != null ? filterType : FilterType.KALMAN);
            filterConfig.setKalmanQ(kalmanQ != null ? kalmanQ : 1e-5);
            filterConfig.setKalmanR(kalmanR != null ? kalmanR : 0.1);
            filterConfig.setKalmanP0(kalmanP0 != null ? kalmanP0 : 1.0);
            filterConfig.setKalmanX0N(kalmanX0N != null ? kalmanX0N : 10);
            flinkPlaybackService.startJob(jobId, data, autoThreshold, anomalyEnabled, filterConfig);
        } catch (Exception e) {
            log.error("Flink 作业提交失败, jobId={}", jobId, e);
            throw ServiceExceptionUtil.exception(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR,
                    "Flink 作业启动失败，请检查 Flink 依赖与序列化配置：" + e.getMessage());
        }

        MonitorUploadResponse resp = new MonitorUploadResponse();
        resp.setJobId(jobId);
        resp.setChannel(meta);
        resp.setPlaybackSeconds((meta.getEndTimestamp() - meta.getStartTimestamp()) / 1000.0);
        resp.setWebsocketPath("/admin-api/monitor/ws?jobId=" + jobId);
        return success(resp);
    }

    @PostMapping("/history/analyze")
    @Operation(summary = "TDMS 历史离线分析")
    public CommonResult<HistoryAnalysisResult> analyzeHistory(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "groups", required = false) String[] groups,
            @RequestParam(value = "thresholdFactor", required = false, defaultValue = "1.5") Double thresholdFactor,
            @RequestParam(value = "filterType", required = false, defaultValue = "KALMAN") FilterType filterType,
            @RequestParam(value = "kalmanQ", required = false, defaultValue = "1e-5") Double kalmanQ,
            @RequestParam(value = "kalmanR", required = false, defaultValue = "0.1") Double kalmanR,
            @RequestParam(value = "kalmanP0", required = false, defaultValue = "1.0") Double kalmanP0,
            @RequestParam(value = "kalmanX0N", required = false, defaultValue = "10") Integer kalmanX0N) {
        if (files == null || files.length == 0) {
            throw ServiceExceptionUtil.invalidParamException("请上传至少一个 TDMS 文件");
        }
        double factor = thresholdFactor != null ? thresholdFactor : 1.5;
        FilterConfig filterConfig = new FilterConfig();
        filterConfig.setType(filterType != null ? filterType : FilterType.KALMAN);
        filterConfig.setKalmanQ(kalmanQ != null ? kalmanQ : 1e-5);
        filterConfig.setKalmanR(kalmanR != null ? kalmanR : 0.1);
        filterConfig.setKalmanP0(kalmanP0 != null ? kalmanP0 : 1.0);
        filterConfig.setKalmanX0N(kalmanX0N != null ? kalmanX0N : 10);
        HistoryAnalysisResult result = historyAnalysisService.analyze(
                List.of(files),
                groups != null ? List.of(groups) : List.of(),
                factor,
                filterConfig
        );
        return success(result);
    }

    // ✅ 关键修复：为 jobId 添加 UUID 正则约束，避免与 /ws 冲突
    @PostMapping("/{jobId:[a-fA-F0-9\\-]{36}}/anomaly")
    @Operation(summary = "更新异常检测阈值")
    public CommonResult<Boolean> updateAnomaly(@PathVariable String jobId,
                                               @RequestParam double threshold,
                                               @RequestParam(defaultValue = "true") boolean enabled) {
        flinkPlaybackService.updateAnomalyConfig(jobId, threshold, enabled);
        return success(true);
    }

    // ✅ 关键修复：为 jobId 添加 UUID 正则约束，避免与 /ws 冲突
    @PostMapping("/{jobId:[a-fA-F0-9\\-]{36}}/filter")
    @Operation(summary = "更新滤波器类型与参数（会重启实时作业）")
    public CommonResult<Boolean> updateFilter(@PathVariable String jobId,
                                              @RequestParam(value = "filterType", required = false, defaultValue = "KALMAN") FilterType filterType,
                                              @RequestParam(value = "kalmanQ", required = false, defaultValue = "1e-5") Double kalmanQ,
                                              @RequestParam(value = "kalmanR", required = false, defaultValue = "0.1") Double kalmanR,
                                              @RequestParam(value = "kalmanP0", required = false, defaultValue = "1.0") Double kalmanP0,
                                              @RequestParam(value = "kalmanX0N", required = false, defaultValue = "10") Integer kalmanX0N) {
        FilterConfig filterConfig = new FilterConfig();
        filterConfig.setType(filterType != null ? filterType : FilterType.KALMAN);
        filterConfig.setKalmanQ(kalmanQ != null ? kalmanQ : 1e-5);
        filterConfig.setKalmanR(kalmanR != null ? kalmanR : 0.1);
        filterConfig.setKalmanP0(kalmanP0 != null ? kalmanP0 : 1.0);
        filterConfig.setKalmanX0N(kalmanX0N != null ? kalmanX0N : 10);
        flinkPlaybackService.updateFilterConfig(jobId, filterConfig);
        return success(true);
    }

    // ✅ 同样修复 stop 接口（虽然 DELETE 不影响 WebSocket GET，但保持一致性）
    @DeleteMapping("/{jobId:[a-fA-F0-9\\-]{36}}")
    @Operation(summary = "停止任务")
    public CommonResult<Boolean> stop(@PathVariable String jobId) {
        flinkPlaybackService.stopJob(jobId);
        return success(true);
    }

    @PostMapping(value = "/realtime/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "实时检测：上传文件并返回处理后的JSON数据")
    public CommonResult<HistoryAnalysisResult> analyzeRealtime(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "filterType", required = false, defaultValue = "KALMAN") FilterType filterType,
            @RequestParam(value = "kalmanQ", required = false, defaultValue = "1e-5") Double kalmanQ,
            @RequestParam(value = "kalmanR", required = false, defaultValue = "0.1") Double kalmanR,
            @RequestParam(value = "kalmanP0", required = false, defaultValue = "1.0") Double kalmanP0,
            @RequestParam(value = "kalmanX0N", required = false, defaultValue = "10") Integer kalmanX0N) {
        log.info("✅ [TRACE] analyzeRealtime 被调用！收到请求，正在检查文件...");
        log.info("✅ [TRACE] 文件信息: name={}, size={}, contentType={}",
                file != null ? file.getOriginalFilename() : "null",
                file != null ? file.getSize() : 0,
                file != null ? file.getContentType() : "null");
        log.info("✅ [TRACE] 请求参数: filterType={}, kalmanQ={}, kalmanR={}, kalmanP0={}, kalmanX0N={}",
                filterType, kalmanQ, kalmanR, kalmanP0, kalmanX0N);

        if (file == null || file.isEmpty()) {
            log.warn("⚠️ [TRACE] 文件为空，拒绝请求");
            throw ServiceExceptionUtil.invalidParamException("请上传有效 TDMS 文件");
        }

        log.info("🔍 [Monitor] 收到实时分析请求，开始处理...");

        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();
        log.info("📁 [Monitor] 接收文件: name={}, size={} bytes ({} MB)", fileName, fileSize, fileSize / 1024.0 / 1024.0);

        FilterConfig filterConfig = new FilterConfig();
        filterConfig.setType(filterType != null ? filterType : FilterType.KALMAN);
        filterConfig.setKalmanQ(kalmanQ != null ? kalmanQ : 1e-5);
        filterConfig.setKalmanR(kalmanR != null ? kalmanR : 0.1);
        filterConfig.setKalmanP0(kalmanP0 != null ? kalmanP0 : 1.0);
        filterConfig.setKalmanX0N(kalmanX0N != null ? kalmanX0N : 10);

        try {
            log.info("📊 [Monitor] 开始历史数据分析...");
            HistoryAnalysisResult result = historyAnalysisService.analyze(
                    List.of(file),
                    List.of("single"),
                    1.5,
                    filterConfig
            );

            int pointCount = result.getPoints() != null ? result.getPoints().size() : 0;
            long estimatedSize = pointCount * 150L;
            log.info("🎉 [Monitor] 实时分析成功完成，异常点数: {}, 数据点数: {}, 估算响应大小: {} KB ({} MB)",
                    result.getAnomalyCount(), pointCount, estimatedSize / 1024, estimatedSize / 1024.0 / 1024.0);

            return success(result);
        } catch (Exception e) {
            log.error("💥 [Monitor] 实时分析过程中发生异常，文件: {}", fileName, e);
            throw ServiceExceptionUtil.exception(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR,
                    "分析失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/realtime/analyze-stream", produces = MediaType.APPLICATION_NDJSON_VALUE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "实时检测：上传文件并流式返回处理后的JSON数据（支持大文件）")
    public Flux<String> analyzeRealtimeStream(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "filterType", required = false, defaultValue = "KALMAN") FilterType filterType,
            @RequestParam(value = "kalmanQ", required = false, defaultValue = "1e-5") Double kalmanQ,
            @RequestParam(value = "kalmanR", required = false, defaultValue = "0.1") Double kalmanR,
            @RequestParam(value = "kalmanP0", required = false, defaultValue = "1.0") Double kalmanP0,
            @RequestParam(value = "kalmanX0N", required = false, defaultValue = "10") Integer kalmanX0N) {
        log.info("✅ [TRACE] analyzeRealtimeStream 被调用！收到流式请求，正在检查文件...");
        log.info("✅ [TRACE] 文件信息: name={}, size={}, contentType={}",
                file != null ? file.getOriginalFilename() : "null",
                file != null ? file.getSize() : 0,
                file != null ? file.getContentType() : "null");
        log.info("🔍 [Monitor] 收到流式实时分析请求，开始处理...");

        if (file == null || file.isEmpty()) {
            log.warn("⚠️ [Monitor] 文件为空，拒绝请求");
            return Flux.error(ServiceExceptionUtil.invalidParamException("请上传有效 TDMS 文件"));
        }

        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();
        log.info("📁 [Monitor] 接收文件: name={}, size={} bytes ({} MB)", fileName, fileSize, fileSize / 1024.0 / 1024.0);

        FilterConfig filterConfig = new FilterConfig();
        filterConfig.setType(filterType != null ? filterType : FilterType.KALMAN);
        filterConfig.setKalmanQ(kalmanQ != null ? kalmanQ : 1e-5);
        filterConfig.setKalmanR(kalmanR != null ? kalmanR : 0.1);
        filterConfig.setKalmanP0(kalmanP0 != null ? kalmanP0 : 1.0);
        filterConfig.setKalmanX0N(kalmanX0N != null ? kalmanX0N : 10);

        try {
            log.info("📊 [Monitor] 开始历史数据分析（流式模式）...");
            HistoryAnalysisResult result = historyAnalysisService.analyze(
                    List.of(file),
                    List.of("single"),
                    1.5,
                    filterConfig
            );

            int pointCount = result.getPoints() != null ? result.getPoints().size() : 0;
            log.info("🎉 [Monitor] 实时分析成功完成，异常点数: {}, 数据点数: {}, 开始流式传输...",
                    result.getAnomalyCount(), pointCount);

            String metadataJson = buildMetadataJson(result);
            int batchSize = 1000;
            List<HistoryAnalysisResult.Point> points = result.getPoints();

            return Flux.concat(
                    Flux.just(metadataJson + "\n"),
                    Flux.fromIterable(points)
                            .buffer(batchSize)
                            .delayElements(Duration.ofMillis(10))
                            .map(batch -> {
                                try {
                                    com.fasterxml.jackson.databind.ObjectMapper mapper =
                                            new com.fasterxml.jackson.databind.ObjectMapper();
                                    return mapper.writeValueAsString(batch) + "\n";
                                } catch (Exception e) {
                                    log.error("序列化 points 批次失败", e);
                                    return "[]\n";
                                }
                            })
            ).doOnComplete(() -> {
                log.info("✅ [Monitor] 流式传输完成，共发送 {} 个数据点", pointCount);
            }).doOnError(error -> {
                log.error("❌ [Monitor] 流式传输过程中发生错误", error);
            });

        } catch (Exception e) {
            log.error("💥 [Monitor] 实时分析过程中发生异常，文件: {}", fileName, e);
            return Flux.error(ServiceExceptionUtil.exception(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR,
                    "分析失败: " + e.getMessage()));
        }
    }

    private String buildMetadataJson(HistoryAnalysisResult result) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("channel", result.getChannel());
            metadata.put("anomalyCount", result.getAnomalyCount());
            metadata.put("pointCount", result.getPoints() != null ? result.getPoints().size() : 0);
            metadata.put("type", "metadata");
            return mapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.error("构建元数据 JSON 失败", e);
            return "{\"type\":\"metadata\",\"error\":\"序列化失败\"}";
        }
    }

    private double calculateEnergyThreshold(ParsedTdmsData data) {
        double sumSquares = data.getSamples().stream()
                .mapToDouble(s -> s.getValue() * s.getValue())
                .sum();
        double meanEnergy = sumSquares / Math.max(1, data.getSamples().size());
        return meanEnergy * 5;
    }
}