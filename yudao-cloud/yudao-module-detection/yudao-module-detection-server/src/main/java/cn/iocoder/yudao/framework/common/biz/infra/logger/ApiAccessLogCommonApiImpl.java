package cn.iocoder.yudao.framework.common.biz.infra.logger;

import cn.iocoder.yudao.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.stereotype.Component;

@Component
public class ApiAccessLogCommonApiImpl implements ApiAccessLogCommonApi {

    @Override
    public CommonResult<Boolean> createApiAccessLog(ApiAccessLogCreateReqDTO createDTO) {
        return CommonResult.success(true);
    }
}

