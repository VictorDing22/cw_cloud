package cn.iocoder.yudao.module.monitor.service.dto;

import cn.iocoder.yudao.module.monitor.api.dto.TdmsChannelMetadata;
import cn.iocoder.yudao.module.monitor.api.dto.TdmsSample;
import lombok.Data;

import java.util.List;

@Data
public class ParsedTdmsData {
    private TdmsChannelMetadata channel;
    private List<TdmsSample> samples;
}
