package cn.iocoder.yudao.module.detection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(scanBasePackages = {
        "cn.iocoder.yudao.module.detection",
        "cn.iocoder.yudao.framework.common.biz.infra.logger"
}, exclude = {
        DataSourceAutoConfiguration.class,
        net.devh.boot.grpc.server.autoconfigure.GrpcServerSecurityAutoConfiguration.class
})
public class GrpcServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrpcServerApplication.class, args);
    }

}
