package cn.iocoder.yudao.framework.common.biz.infra.logger;

import cn.iocoder.yudao.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorLogCommonApiImpl implements ApiErrorLogCommonApi {

    @Override
    public CommonResult<Boolean> createApiErrorLog(ApiErrorLogCreateReqDTO createDTO) {
        return CommonResult.success(true);
    }
}

