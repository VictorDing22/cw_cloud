package cn.iocoder.yudao.module.monitor.service;

import cn.iocoder.yudao.module.monitor.service.dto.ParsedTdmsData;
import org.springframework.web.multipart.MultipartFile;

public interface TdmsParsingService {

    ParsedTdmsData parse(MultipartFile file);
}
