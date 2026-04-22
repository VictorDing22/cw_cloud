package com.floatdata.processor;

import com.floatdata.utils.AnomalyResult;
import com.floatdata.utils.ConfigLoader;
import com.floatdata.utils.SignalData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Anomaly Detector - Detects anomalies based on filtered signals
 */
public class AnomalyDetector {
    private static final Logger logger = LoggerFactory.getLogger(AnomalyDetector.class);
    private final SignalFilter filter;
    private final double energyThreshold;
    private final double frequencyThreshold;
    private final int detectionWindow;

    public AnomalyDetector() {
        this.filter = new SignalFilter();
        this.energyThreshold = ConfigLoader.getDouble("anomaly.threshold.energy", 0.8);
        this.frequencyThreshold = ConfigLoader.getDouble("anomaly.threshold.frequency", 0.75);
        this.detectionWindow = ConfigLoader.getInt("anomaly.detection.window", 1000);
        
        logger.info("Anomaly detector initialized: energyThreshold={}, frequencyThreshold={}",
                energyThreshold, frequencyThreshold);
    }

    /**
     * Process signal data and detect anomalies
     */
    public AnomalyResult detect(SignalData signalData) {
        long startTime = System.currentTimeMillis();
        
        try {
            float[] samples = signalData.getSamples();
            
            // Step 1: Apply Butterworth filter
            float[] filtered = filter.applyButterworthFilter(samples);
            
            // Step 2: Calculate energy
            double energy = filter.calculateEnergy(filtered);
            double normalizedEnergy = normalizeEnergy(energy);
            
            // Step 3: Calculate frequency features
            double[] frequencyMagnitude = filter.calculateFrequencyFeatures(filtered);
            double frequencyScore = filter.calculateFrequencyScore(frequencyMagnitude);
            
            // Step 4: Create result
            AnomalyResult result = new AnomalyResult(
                    signalData.getTimestamp(),
                    signalData.getSensorId(),
                    signalData.getLocation(),
                    normalizedEnergy,
                    frequencyScore
            );
            
            long processingTime = System.currentTimeMillis() - startTime;
            result.setProcessingTime(processingTime);
            
            if (result.isAnomaly()) {
                logger.warn("Anomaly detected: {}", result);
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Anomaly detection failed", e);
            return createErrorResult(signalData);
        }
    }

    /**
     * Normalize energy value to [0, 1]
     */
    private double normalizeEnergy(double energy) {
        // Use sigmoid function for normalization
        // Assume normal energy range is 0-100
        double normalized = 1.0 / (1.0 + Math.exp(-energy / 50.0 + 2.0));
        return Math.min(normalized, 1.0);
    }

    /**
     * Create error result
     */
    private AnomalyResult createErrorResult(SignalData signalData) {
        AnomalyResult result = new AnomalyResult(
                signalData.getTimestamp(),
                signalData.getSensorId(),
                signalData.getLocation(),
                0.0,
                0.0
        );
        result.setAnomalyType("ERROR");
        return result;
    }

    /**
     * Batch process signal data
     */
    public AnomalyResult[] detectBatch(SignalData[] signalDataArray) {
        AnomalyResult[] results = new AnomalyResult[signalDataArray.length];
        for (int i = 0; i < signalDataArray.length; i++) {
            results[i] = detect(signalDataArray[i]);
        }
        return results;
    }
}
