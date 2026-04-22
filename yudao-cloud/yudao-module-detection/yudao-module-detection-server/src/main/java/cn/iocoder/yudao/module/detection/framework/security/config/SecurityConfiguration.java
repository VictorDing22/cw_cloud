package cn.iocoder.yudao.module.detection.framework.security.config;

import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Detection 模块的 Security 配置
 */
@Configuration(proxyBeanMethods = false, value = "detectionSecurityConfiguration")
public class SecurityConfiguration {

    @Bean("detectionAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                // 放行检测模块的所有接口 (生产环境建议根据需要调整)
                registry.requestMatchers("/admin-api/detection/**").permitAll();
                // 放行 WebSocket
                registry.requestMatchers("/ws/detection/**").permitAll();
            }

        };
    }

}
