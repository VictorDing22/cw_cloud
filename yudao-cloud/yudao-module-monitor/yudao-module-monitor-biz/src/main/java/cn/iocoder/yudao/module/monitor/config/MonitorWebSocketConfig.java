package cn.iocoder.yudao.module.monitor.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@Slf4j
public class MonitorWebSocketConfig implements WebSocketConfigurer {

    private final MonitorWebSocketHandler webSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
//         同时兼容直连后端（/monitor/ws）和通过网关+前端代理的路径（/admin-api/monitor/ws）
registry.addHandler(webSocketHandler, "/monitor/ws", "/admin-api/monitor/ws")
                .setAllowedOrigins("*");
//
       log.info("✅ WebSocket 处理器已注册: /monitor/ws, /admin-api/monitor/ws");

        // ✅ 正确做法：只注册内部路径 /monitor/ws
        // 因为前端请求的是 /admin-api/monitor/ws，
        // 而 Spring MVC 的 addPathPrefix 机制只影响 Controller，不影响 WebSocket。
        // WebSocket 处理器需要直接监听外部看到的完整路径。

        // 但是！根据你的项目架构，最安全的方式是只注册内部路径
        // 并确保前端请求的路径能被正确路由到它。
        //registry.addHandler(webSocketHandler, "/monitor/ws")
         //       .setAllowedOrigins("*");

        // 注意：不要注册 "/admin-api/monitor/ws"！
    }
}
