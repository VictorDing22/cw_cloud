package cn.iocoder.yudao.detection.flink.schema;

import java.io.Serializable;

/**
 * One voltage sample expanded from a Kafka raw_topic message.
 * Each Kafka message produces frag_size of these records.
 */
public class RawSignalRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private String deviceId;
    private int channelId;
    private int seq;
    private long timestampNs;
    private float voltage;
    private int samplingRate;

    public RawSignalRecord() {}

    public RawSignalRecord(String deviceId, int channelId, int seq,
                           long timestampNs, float voltage, int samplingRate) {
        this.deviceId = deviceId;
        this.channelId = channelId;
        this.seq = seq;
        this.timestampNs = timestampNs;
        this.voltage = voltage;
        this.samplingRate = samplingRate;
    }

    public String getDeviceId()   { return deviceId; }
    public int getChannelId()     { return channelId; }
    public int getSeq()           { return seq; }
    public long getTimestampNs()  { return timestampNs; }
    public float getVoltage()     { return voltage; }
    public int getSamplingRate()  { return samplingRate; }

    public void setDeviceId(String deviceId)      { this.deviceId = deviceId; }
    public void setChannelId(int channelId)       { this.channelId = channelId; }
    public void setSeq(int seq)                   { this.seq = seq; }
    public void setTimestampNs(long timestampNs)  { this.timestampNs = timestampNs; }
    public void setVoltage(float voltage)         { this.voltage = voltage; }
    public void setSamplingRate(int samplingRate)  { this.samplingRate = samplingRate; }
}
