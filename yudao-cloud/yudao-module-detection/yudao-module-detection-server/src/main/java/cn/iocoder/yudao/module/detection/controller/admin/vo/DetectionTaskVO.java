package cn.iocoder.yudao.module.detection.controller.admin.vo;

import lombok.Data;

@Data
public class DetectionTaskVO {
    private String id;
    private String filename;
    private String algorithm;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private Integer progress;
    private String size;
    /** 总大小（字节），用于计算真实处理速度 */
    private Long sizeBytes;
    /** 处理开始时间（毫秒时间戳） */
    private Long startTime;
    /** 最近一次进度更新时间（毫秒时间戳） */
    private Long lastUpdateTime;
    /**
     * 实际处理速度，单位 MB/s，已格式化为字符串（例如 "12.35"）
     * 前端可直接展示为 "12.35 MB/s"
     */
    private String speed;
}
