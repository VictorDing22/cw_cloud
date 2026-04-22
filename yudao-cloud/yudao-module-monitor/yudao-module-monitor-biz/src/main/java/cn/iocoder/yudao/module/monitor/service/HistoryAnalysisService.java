package cn.iocoder.yudao.module.monitor.service;

import cn.iocoder.yudao.module.monitor.api.dto.HistoryAnalysisResult;
import cn.iocoder.yudao.module.monitor.api.dto.FilterConfig;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface HistoryAnalysisService {

    /**
     * 对上传的 TDMS 文件做离线历史分析。
     *
     * @param files           TDMS 文件列表（可来自不同“分组”）
     * @param groups          每个文件对应的逻辑分组（signal1/signal2/single）
     * @param thresholdFactor 阈值系数（相对于 95% 分位数）
     * @return 单通道的分析结果（当前版本默认选取首个有效通道）
     */
    HistoryAnalysisResult analyze(List<MultipartFile> files, List<String> groups, double thresholdFactor, FilterConfig filterConfig);
}

