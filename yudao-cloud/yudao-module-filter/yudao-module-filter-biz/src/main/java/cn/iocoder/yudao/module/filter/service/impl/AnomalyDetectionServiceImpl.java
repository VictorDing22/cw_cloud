package cn.iocoder.yudao.module.filter.service.impl;

import cn.iocoder.yudao.module.filter.controller.admin.vo.AnomalyDetectionReqVO;
import cn.iocoder.yudao.module.filter.controller.admin.vo.AnomalyDetectionRespVO;
import cn.iocoder.yudao.module.filter.enums.AlertLevel;
import cn.iocoder.yudao.module.filter.enums.AnomalyType;
import cn.iocoder.yudao.module.filter.service.AnomalyDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Comparator;

/**
 * 异常检测服务实现
 *
 * @author yudao
 */
@Slf4j
@Service
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    @Override
    public AnomalyDetectionRespVO detectAnomaly(AnomalyDetectionReqVO reqVO) {
        log.info("开始执行异常检测 - 设备ID: {}, 传感器类型: {}", 
                reqVO.getDeviceId(), reqVO.getSensorType());

        return batchDetectAnomaly(
            convertToArray(reqVO.getOriginalSignal()),
            convertToArray(reqVO.getFilteredSignal()),
            reqVO.getDeviceId(),
            reqVO.getSensorType()
        );
    }

    @Override
    public AnomalyDetectionRespVO batchDetectAnomaly(double[] originalSignal, 
                                                   double[] filteredSignal,
                                                   String deviceId,
                                                   String sensorType) {
        
        AnomalyDetectionRespVO respVO = new AnomalyDetectionRespVO();
        respVO.setDeviceId(deviceId);
        respVO.setSensorType(sensorType);
        respVO.setDetectionTime(LocalDateTime.now());
        respVO.setAnomalyList(new ArrayList<>());

        try {
            // 1. 统计特征分析
            StatisticalFeatures originalFeatures = calculateStatisticalFeatures(originalSignal);
            StatisticalFeatures filteredFeatures = calculateStatisticalFeatures(filteredSignal);

            log.info("原始信号统计特征: {}", originalFeatures);
            log.info("滤波后信号统计特征: {}", filteredFeatures);

            // 2. 幅值异常检测
            detectAmplitudeAnomalies(filteredSignal, filteredFeatures, respVO);

            // 3. 趋势异常检测
            detectTrendAnomalies(filteredSignal, respVO);

            // 4. 频域异常检测
            detectFrequencyAnomalies(filteredSignal, respVO);

            // 5. 信号质量评估
            assessSignalQuality(originalSignal, filteredSignal, respVO);

            // 6. 计算总体异常分数
            respVO.setAnomalyScore(calculateOverallAnomalyScore(respVO));

            // 7. 确定异常状态和级别
            determineAnomalyStatus(respVO);

            log.info("异常检测完成 - 设备: {}, 异常数量: {}, 异常分数: {}", 
                    deviceId, respVO.getAnomalyList().size(), respVO.getAnomalyScore());

        } catch (Exception e) {
            log.error("异常检测过程中发生错误", e);
            respVO.setHasAnomaly(false);
            respVO.setAlertLevel(AlertLevel.INFO);
        }

        return respVO;
    }

    /**
     * 幅值异常检测
     */
    private void detectAmplitudeAnomalies(double[] signal, StatisticalFeatures features, 
                                        AnomalyDetectionRespVO respVO) {
        
        // 3-sigma 原则检测异常值
        double upperBound = features.getMean() + 3 * features.getStdDev();
        double lowerBound = features.getMean() - 3 * features.getStdDev();

        List<Integer> anomalyIndices = new ArrayList<>();
        
        for (int i = 0; i < signal.length; i++) {
            if (signal[i] > upperBound || signal[i] < lowerBound) {
                anomalyIndices.add(i);
            }
        }

        if (!anomalyIndices.isEmpty()) {
            AnomalyDetectionRespVO.AnomalyDetail anomaly = new AnomalyDetectionRespVO.AnomalyDetail();
            anomaly.setType(AnomalyType.AMPLITUDE_ANOMALY);
            anomaly.setDescription(String.format("检测到 %d 个幅值异常点", anomalyIndices.size()));
            anomaly.setSeverity(anomalyIndices.size() > signal.length * 0.1 ? AlertLevel.ERROR : AlertLevel.WARNING);
            anomaly.setAnomalyIndices(anomalyIndices);
            anomaly.setThreshold(String.format("正常范围: [%.3f, %.3f]", lowerBound, upperBound));
            
            respVO.getAnomalyList().add(anomaly);
        }
    }

    /**
     * 趋势异常检测
     */
    private void detectTrendAnomalies(double[] signal, AnomalyDetectionRespVO respVO) {
        if (signal.length < 10) return; // 数据太少无法分析趋势

        // 计算移动平均的变化率
        int windowSize = Math.min(10, signal.length / 4);
        double[] movingAverage = calculateMovingAverage(signal, windowSize);
        
        // 检测突变点
        List<Integer> changePoints = new ArrayList<>();
        double threshold = calculateChangeThreshold(movingAverage);
        
        for (int i = 1; i < movingAverage.length; i++) {
            double change = Math.abs(movingAverage[i] - movingAverage[i-1]);
            if (change > threshold) {
                changePoints.add(i + windowSize/2); // 调整到原始信号的位置
            }
        }

        if (!changePoints.isEmpty()) {
            AnomalyDetectionRespVO.AnomalyDetail anomaly = new AnomalyDetectionRespVO.AnomalyDetail();
            anomaly.setType(AnomalyType.TREND_ANOMALY);
            anomaly.setDescription(String.format("检测到 %d 个趋势突变点", changePoints.size()));
            anomaly.setSeverity(changePoints.size() > 3 ? AlertLevel.WARNING : AlertLevel.INFO);
            anomaly.setAnomalyIndices(changePoints);
            anomaly.setThreshold(String.format("变化阈值: %.3f", threshold));
            
            respVO.getAnomalyList().add(anomaly);
        }
    }

    /**
     * 频域异常检测（简化版）
     */
    private void detectFrequencyAnomalies(double[] signal, AnomalyDetectionRespVO respVO) {
        // 简单的频域特征：计算零交叉率
        int zeroCrossings = 0;
        for (int i = 1; i < signal.length; i++) {
            if ((signal[i] >= 0) != (signal[i-1] >= 0)) {
                zeroCrossings++;
            }
        }

        double zeroCrossingRate = (double) zeroCrossings / signal.length;
        
        // 正常信号的零交叉率通常在某个范围内
        if (zeroCrossingRate > 0.5 || zeroCrossingRate < 0.01) {
            AnomalyDetectionRespVO.AnomalyDetail anomaly = new AnomalyDetectionRespVO.AnomalyDetail();
            anomaly.setType(AnomalyType.FREQUENCY_ANOMALY);
            anomaly.setDescription(String.format("零交叉率异常: %.3f", zeroCrossingRate));
            anomaly.setSeverity(AlertLevel.INFO);
            anomaly.setThreshold("正常范围: [0.01, 0.5]");
            
            respVO.getAnomalyList().add(anomaly);
        }
    }

    /**
     * 信号质量评估
     */
    private void assessSignalQuality(double[] originalSignal, double[] filteredSignal, 
                                   AnomalyDetectionRespVO respVO) {
        
        // 计算信噪比改善
        double originalPower = calculateSignalPower(originalSignal);
        double filteredPower = calculateSignalPower(filteredSignal);
        double snrImprovement = 10 * Math.log10(filteredPower / originalPower);

        respVO.setSnrImprovement(snrImprovement);
        respVO.setSignalQuality(categorizeSignalQuality(snrImprovement));

        // 如果滤波效果很差，也算作一种异常
        if (snrImprovement < -10) { // 滤波后反而更差
            AnomalyDetectionRespVO.AnomalyDetail anomaly = new AnomalyDetectionRespVO.AnomalyDetail();
            anomaly.setType(AnomalyType.SIGNAL_QUALITY);
            anomaly.setDescription("滤波效果不佳，可能存在系统问题");
            anomaly.setSeverity(AlertLevel.WARNING);
            anomaly.setThreshold("SNR改善应 > -10dB");
            
            respVO.getAnomalyList().add(anomaly);
        }
    }

    /**
     * 计算统计特征
     */
    private StatisticalFeatures calculateStatisticalFeatures(double[] signal) {
        double sum = Arrays.stream(signal).sum();
        double mean = sum / signal.length;
        
        double variance = Arrays.stream(signal)
            .map(x -> Math.pow(x - mean, 2))
            .sum() / signal.length;
        double stdDev = Math.sqrt(variance);
        
        double min = Arrays.stream(signal).min().orElse(0);
        double max = Arrays.stream(signal).max().orElse(0);
        
        double rms = Math.sqrt(Arrays.stream(signal)
            .map(x -> x * x)
            .sum() / signal.length);

        return StatisticalFeatures.builder()
            .mean(mean)
            .variance(variance)
            .stdDev(stdDev)
            .min(min)
            .max(max)
            .rms(rms)
            .build();
    }

    /**
     * 计算移动平均
     */
    private double[] calculateMovingAverage(double[] signal, int windowSize) {
        double[] result = new double[signal.length - windowSize + 1];
        
        for (int i = 0; i < result.length; i++) {
            double sum = 0;
            for (int j = 0; j < windowSize; j++) {
                sum += signal[i + j];
            }
            result[i] = sum / windowSize;
        }
        
        return result;
    }

    /**
     * 计算变化阈值
     */
    private double calculateChangeThreshold(double[] movingAverage) {
        double[] changes = new double[movingAverage.length - 1];
        for (int i = 0; i < changes.length; i++) {
            changes[i] = Math.abs(movingAverage[i+1] - movingAverage[i]);
        }
        
        // 使用变化量的标准差的2倍作为阈值
        double meanChange = Arrays.stream(changes).average().orElse(0);
        double variance = Arrays.stream(changes)
            .map(x -> Math.pow(x - meanChange, 2))
            .sum() / changes.length;
        
        return meanChange + 2 * Math.sqrt(variance);
    }

    /**
     * 计算信号功率
     */
    private double calculateSignalPower(double[] signal) {
        return Arrays.stream(signal)
            .map(x -> x * x)
            .sum() / signal.length;
    }

    /**
     * 信号质量分类
     */
    private String categorizeSignalQuality(double snrImprovement) {
        if (snrImprovement > 10) return "优秀";
        if (snrImprovement > 5) return "良好";
        if (snrImprovement > 0) return "一般";
        if (snrImprovement > -5) return "较差";
        return "很差";
    }

    /**
     * 计算总体异常分数
     */
    private double calculateOverallAnomalyScore(AnomalyDetectionRespVO respVO) {
        if (respVO.getAnomalyList().isEmpty()) {
            return 0.0;
        }

        double totalScore = 0.0;
        for (AnomalyDetectionRespVO.AnomalyDetail anomaly : respVO.getAnomalyList()) {
            double score = switch (anomaly.getSeverity()) {
                case CRITICAL -> 1.0;
                case ERROR -> 0.8;
                case WARNING -> 0.5;
                case INFO -> 0.2;
            };
            totalScore += score;
        }

        // 归一化到0-1范围
        return Math.min(totalScore / respVO.getAnomalyList().size(), 1.0);
    }

    /**
     * 确定异常状态和级别
     */
    private void determineAnomalyStatus(AnomalyDetectionRespVO respVO) {
        if (respVO.getAnomalyList().isEmpty()) {
            respVO.setHasAnomaly(false);
            respVO.setAlertLevel(AlertLevel.INFO);
            return;
        }

        respVO.setHasAnomaly(true);
        
        // 找到最高严重级别（按权重比较）
        AlertLevel maxLevel = respVO.getAnomalyList().stream()
            .map(AnomalyDetectionRespVO.AnomalyDetail::getSeverity)
            .max(Comparator.comparing(AlertLevel::getWeight))
            .orElse(AlertLevel.INFO);
            
        respVO.setAlertLevel(maxLevel);
    }

    /**
     * 转换List到数组
     */
    private double[] convertToArray(List<Double> list) {
        return list.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /**
     * 统计特征内部类
     */
    @lombok.Builder
    @lombok.Data
    private static class StatisticalFeatures {
        private double mean;
        private double variance;
        private double stdDev;
        private double min;
        private double max;
        private double rms;
    }
}

