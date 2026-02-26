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
    private String duration;
}
