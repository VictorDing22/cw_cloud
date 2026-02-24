package cn.iocoder.yudao.module.detection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "cn.iocoder.yudao.module.detection",
        "cn.iocoder.yudao.framework.common.biz.infra.logger"
})
public class GrpcServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrpcServerApplication.class, args);
    }

}
