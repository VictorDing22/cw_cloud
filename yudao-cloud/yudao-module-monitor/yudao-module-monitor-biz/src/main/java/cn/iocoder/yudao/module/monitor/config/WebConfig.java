package cn.iocoder.yudao.module.monitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

/**
 * Web 配置类
 * 
 * 显式注册 MultipartResolver Bean，确保 @RequestPart 能正常工作
 * 即使 application.yaml 中配置了 spring.servlet.multipart.enabled=true，
 * 显式注册也能确保 Bean 被正确创建
 * 
 * 注意：网关配置已修改，不再重写路径
 * 因此 Controller 的路径会是 /admin-api/monitor/**（由 YudaoWebAutoConfiguration 自动添加前缀）
 * 与网关的 /admin-api/monitor/** 路径匹配
 */
@Configuration
public class WebConfig {

    /**
     * 注册 MultipartResolver Bean
     * 
     * 注意：Spring Boot 2.x 使用 StandardServletMultipartResolver
     * 它会自动从 application.yaml 中读取配置（如 max-file-size）
     */
    @Bean(name = "multipartResolver")
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
}
