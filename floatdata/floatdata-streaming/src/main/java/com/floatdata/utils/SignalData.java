package com.floatdata.utils;

import com.google.gson.Gson;
import java.io.Serializable;

/**
 * 声发射信号数据模型
 */
public class SignalData implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    private long timestamp;           // 时间戳 (ms)
    private int sensorId;             // 传感器ID
    private float[] samples;           // 采样数据
    private int sampleRate;            // 采样率 (Hz)
    private String location;           // 位置标识

    public SignalData() {
    }

    public SignalData(long timestamp, int sensorId, float[] samples, 
                     int sampleRate, String location) {
        this.timestamp = timestamp;
        this.sensorId = sensorId;
        this.samples = samples;
        this.sampleRate = sampleRate;
        this.location = location;
    }

    // JSON 序列化/反序列化
    public String toJson() {
        return gson.toJson(this);
    }

    public static SignalData fromJson(String json) {
        return gson.fromJson(json, SignalData.class);
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

    public float[] getSamples() {
        return samples;
    }

    public void setSamples(float[] samples) {
        this.samples = samples;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return "SignalData{" +
                "timestamp=" + timestamp +
                ", sensorId=" + sensorId +
                ", samples.length=" + (samples != null ? samples.length : 0) +
                ", sampleRate=" + sampleRate +
                ", location='" + location + '\'' +
                '}';
    }
}
