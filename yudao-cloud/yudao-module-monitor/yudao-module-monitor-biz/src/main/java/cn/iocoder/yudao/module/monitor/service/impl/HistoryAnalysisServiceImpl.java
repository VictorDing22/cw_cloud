package cn.iocoder.yudao.module.monitor.service.impl;

import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.module.monitor.api.dto.FilterConfig;
import cn.iocoder.yudao.module.monitor.api.dto.FilterType;
import cn.iocoder.yudao.module.monitor.api.dto.HistoryAnalysisResult;
import cn.iocoder.yudao.module.monitor.api.dto.TdmsChannelMetadata;
import cn.iocoder.yudao.module.monitor.api.dto.TdmsSample;
import cn.iocoder.yudao.module.monitor.filter.Kalman1DFilter;
import cn.iocoder.yudao.module.monitor.service.HistoryAnalysisService;
import cn.iocoder.yudao.module.monitor.service.TdmsParsingService;
import cn.iocoder.yudao.module.monitor.service.dto.ParsedTdmsData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TDMS 离线历史分析实现。
 *
 * <p>这里不再启动 Flink mini-cluster，而是直接在 JVM 内基于 {@link TdmsSample}
 * 做 LMS 滤波 + 残差 + 滑动窗口能量阈值异常检测，所有数据 100% 来自 TDMS 文件。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HistoryAnalysisServiceImpl implements HistoryAnalysisService {

    private final TdmsParsingService tdmsParsingService;

    private static final int LMS_ORDER = 8;
    private static final double LMS_STEP = 0.01;
    private static final long WINDOW_MS = 50L;
    /**
     * 最多返回给前端的采样点数量，避免一次性返回几百万点导致
     * - JSON 过大
     * - 序列化/反序列化时间过长
     * - ECharts 前端渲染卡顿
     */
    private static final int MAX_OUTPUT_POINTS = 50_000;

    @Override
    public HistoryAnalysisResult analyze(List<MultipartFile> files, List<String> groups, double thresholdFactor, FilterConfig filterConfig) {
        if (files == null || files.isEmpty()) {
            throw ServiceExceptionUtil.invalidParamException("请上传至少一个 TDMS 文件");
        }
        if (thresholdFactor <= 0) {
            thresholdFactor = 1.5;
        }
        if (filterConfig == null) {
            filterConfig = new FilterConfig(); // 默认 Kalman
        }

        List<ParsedTdmsData> parsedList = new ArrayList<>();
        for (MultipartFile file : files) {
            ParsedTdmsData parsed = tdmsParsingService.parse(file);
            parsedList.add(parsed);
        }

        ParsedTdmsData merged = mergeParsed(parsedList);
        return runFilterAndAnomaly(merged, thresholdFactor, filterConfig);
    }

    private ParsedTdmsData mergeParsed(List<ParsedTdmsData> parsedList) {
        if (parsedList.isEmpty()) {
            throw ServiceExceptionUtil.exception(GlobalErrorCodeConstants.BAD_REQUEST, "未解析到有效 TDMS 通道");
        }
        parsedList.sort(Comparator.comparingLong(p -> p.getChannel().getStartTimestamp() != null
                ? p.getChannel().getStartTimestamp() : 0L));

        ParsedTdmsData first = parsedList.get(0);
        TdmsChannelMetadata channel = first.getChannel();

        List<TdmsSample> allSamples = parsedList.stream()
                .flatMap(p -> p.getSamples().stream())
                .sorted(Comparator.comparingLong(TdmsSample::getTimestamp))
                .collect(Collectors.toList());

        ParsedTdmsData merged = new ParsedTdmsData();
        merged.setChannel(channel);
        merged.setSamples(allSamples);
        return merged;
    }

    private HistoryAnalysisResult runFilterAndAnomaly(ParsedTdmsData data, double thresholdFactor, FilterConfig filterConfig) {
        TdmsChannelMetadata meta = data.getChannel();
        List<TdmsSample> samples = data.getSamples();
        if (samples == null || samples.isEmpty()) {
            throw ServiceExceptionUtil.exception(GlobalErrorCodeConstants.BAD_REQUEST, "TDMS 文件中未找到有效采样点");
        }

        int n = samples.size();
        // 根据总点数自适应下采样步长，只控制「输出给前端」的点数，不影响阈值计算与异常统计
        int downSampleStep = Math.max(1, n / MAX_OUTPUT_POINTS);

        long[] tsArray = new long[n];
        double[] rawArray = new double[n];
        double[] filteredArray = new double[n];
        double[] residualArray = new double[n];
        double[] energyArray = new double[n]; // 残差能量（滑窗累计）

        // 运行滤波器 + 计算残差能量（WINDOW_MS）
        final FilterType type = filterConfig.getType() != null ? filterConfig.getType() : FilterType.KALMAN;
        double[] weights = null;
        double[] buffer = null;
        int bufferIndex = 0;
        Kalman1DFilter kalman = null;
        if (type == FilterType.LMS) {
            weights = new double[LMS_ORDER];
            buffer = new double[LMS_ORDER];
        } else {
            kalman = new Kalman1DFilter(filterConfig.getKalmanQ(), filterConfig.getKalmanR(),
                    filterConfig.getKalmanP0(), filterConfig.getKalmanX0N());
        }

        long windowStartTs = samples.get(0).getTimestamp();
        double windowEnergy = 0.0;

        for (int i = 0; i < n; i++) {
            TdmsSample s = samples.get(i);
            long ts = s.getTimestamp();
            double x = s.getValue();
            double y;
            if (type == FilterType.LMS) {
                buffer[bufferIndex] = x;
                bufferIndex = (bufferIndex + 1) % LMS_ORDER;
                y = 0.0;
                int idx = bufferIndex;
                for (int k = 0; k < LMS_ORDER; k++) {
                    idx = (idx - 1 + LMS_ORDER) % LMS_ORDER;
                    y += weights[k] * buffer[idx];
                }
                double desired = buffer[(bufferIndex - 1 + LMS_ORDER) % LMS_ORDER];
                double err = desired - y;
                idx = bufferIndex;
                for (int k = 0; k < LMS_ORDER; k++) {
                    idx = (idx - 1 + LMS_ORDER) % LMS_ORDER;
                    weights[k] += 2 * LMS_STEP * err * buffer[idx];
                }
            } else {
                y = kalman.filter(x);
            }
            double residual = x - y;

            if (ts - windowStartTs > WINDOW_MS) {
                windowStartTs = ts;
                windowEnergy = 0.0;
            }
            // 异常检测以残差能量为准（更能反映“被滤掉的异常/噪声”）
            windowEnergy += residual * residual;

            tsArray[i] = ts;
            rawArray[i] = x;
            filteredArray[i] = y;
            residualArray[i] = residual;
            energyArray[i] = windowEnergy;
        }

        // 阈值：残差能量 95% 分位 * factor
        Percentile percentile = new Percentile(95.0);
        double base = percentile.evaluate(energyArray);
        double threshold = base * thresholdFactor;

        List<HistoryAnalysisResult.Point> points = new ArrayList<>();
        long anomalyCount = 0L;
        long t0 = tsArray[0];
        for (int i = 0; i < n; i++) {
            boolean isAnomaly = energyArray[i] > threshold;
            if (isAnomaly) {
                anomalyCount++;
            }

            // 为了加速分析，只对部分点输出到前端：
            // 1）按照 downSampleStep 均匀下采样形成连续波形
            // 2）所有异常点始终保留，保证告警信息不丢
            boolean shouldEmit = (i % downSampleStep == 0) || isAnomaly;
            if (!shouldEmit) {
                continue;
            }

            HistoryAnalysisResult.Point p = new HistoryAnalysisResult.Point();
            p.setTimestamp((tsArray[i] - t0) / 1000.0);
            p.setRawValue(rawArray[i]);
            p.setFilteredValue(filteredArray[i]);
            p.setResidualValue(residualArray[i]);
            p.setAnomaly(isAnomaly);
            p.setAnomalyType(isAnomaly ? "energy_spike" : null);
            p.setChannelName(meta.getName());
            points.add(p);
        }

        HistoryAnalysisResult result = new HistoryAnalysisResult();
        result.setChannel(meta);
        result.setPoints(points);
        result.setAnomalyCount(anomalyCount);
        return result;
    }
}

