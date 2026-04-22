package cn.iocoder.yudao.module.monitor.config;

import com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 让 Knife4j 的 {@link Knife4jProperties} 作为 Spring Bean 生效。
 *
 * <p>用于满足 yudao-web 中 {@code Knife4jOpenApiCustomizer} 的构造参数依赖。</p>
 */
@Configuration
@EnableConfigurationProperties(Knife4jProperties.class)
public class MonitorKnife4jConfig {
}

