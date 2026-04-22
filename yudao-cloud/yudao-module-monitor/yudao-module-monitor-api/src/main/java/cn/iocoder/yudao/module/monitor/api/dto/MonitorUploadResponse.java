package cn.iocoder.yudao.module.monitor.api.dto;

import lombok.Data;

/**
 * 上传 TDMS 后返回给前端的 session 信息。
 */
@Data
public class MonitorUploadResponse {

    /**
        * 当前处理任务的唯一 ID。
        */
    private String jobId;

    /**
        * 默认展示的通道元数据。
        */
    private TdmsChannelMetadata channel;

    /**
        * 预计播放时长（秒），由 TDMS 原始时间跨度计算。
        */
    private double playbackSeconds;

    /**
        * WebSocket 连接地址（供前端直连）。
        */
    private String websocketPath;
}
