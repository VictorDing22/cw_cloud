package com.floatdata.utils;

import com.google.gson.Gson;
import java.io.Serializable;

/**
 * Anomaly Detection Result Model
 */
public class AnomalyResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    private long timestamp;
    private int sensorId;
    private String location;
    private double energyLevel;        // Energy level (0-1)
    private double frequencyScore;     // Frequency score (0-1)
    private double anomalyScore;       // Combined anomaly score (0-1)
    private boolean isAnomaly;         // Is anomaly
    private String anomalyType;        // Anomaly type
    private long processingTime;       // Processing time (ms)

    public AnomalyResult() {
    }

    public AnomalyResult(long timestamp, int sensorId, String location,
                        double energyLevel, double frequencyScore) {
        this.timestamp = timestamp;
        this.sensorId = sensorId;
        this.location = location;
        this.energyLevel = energyLevel;
        this.frequencyScore = frequencyScore;
        this.anomalyScore = (energyLevel + frequencyScore) / 2.0;
        this.isAnomaly = this.anomalyScore > 0.75;
        this.anomalyType = determineAnomalyType();
    }

    private String determineAnomalyType() {
        if (!isAnomaly) return "NORMAL";
        if (energyLevel > 0.8) return "HIGH_ENERGY";
        if (frequencyScore > 0.8) return "FREQUENCY_ANOMALY";
        return "MIXED_ANOMALY";
    }

    public String toJson() {
        return gson.toJson(this);
    }

    public static AnomalyResult fromJson(String json) {
        return gson.fromJson(json, AnomalyResult.class);
    }

    // Getters and Setters
    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getSensorId() {
        return sensorId;
    }

    public void setSensorId(int sensorId) {
        this.sensorId = sensorId;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public double getEnergyLevel() {
        return energyLevel;
    }

    public void setEnergyLevel(double energyLevel) {
        this.energyLevel = energyLevel;
    }

    public double getFrequencyScore() {
        return frequencyScore;
    }

    public void setFrequencyScore(double frequencyScore) {
        this.frequencyScore = frequencyScore;
    }

    public double getAnomalyScore() {
        return anomalyScore;
    }

    public void setAnomalyScore(double anomalyScore) {
        this.anomalyScore = anomalyScore;
    }

    public boolean isAnomaly() {
        return isAnomaly;
    }

    public void setAnomaly(boolean anomaly) {
        isAnomaly = anomaly;
    }

    public String getAnomalyType() {
        return anomalyType;
    }

    public void setAnomalyType(String anomalyType) {
        this.anomalyType = anomalyType;
    }

    public long getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    @Override
    public String toString() {
        return "AnomalyResult{" +
                "timestamp=" + timestamp +
                ", sensorId=" + sensorId +
                ", location='" + location + '\'' +
                ", energyLevel=" + String.format("%.4f", energyLevel) +
                ", frequencyScore=" + String.format("%.4f", frequencyScore) +
                ", anomalyScore=" + String.format("%.4f", anomalyScore) +
                ", isAnomaly=" + isAnomaly +
                ", anomalyType='" + anomalyType + '\'' +
                ", processingTime=" + processingTime + "ms" +
                '}';
    }
}
