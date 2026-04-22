package cn.iocoder.yudao.module.detection.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TdmsSample implements Serializable {
    private static final long serialVersionUID = 1L;
    private long timestamp;
    private double value;
    private String channel;
}
