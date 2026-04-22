package cn.iocoder.yudao.module.detection.websocket;

import cn.iocoder.yudao.module.detection.logic.dto.FilterResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 检测结果 WebSocket 处理器：负责将实时数据推送到前端
 */
@Slf4j
@Component("detectionWebSocketHandler")
public class DetectionWebSocketHandler extends TextWebSocketHandler {

    private static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SESSIONS.put(session.getId(), session);
        log.info("WebSocket 已连接: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSIONS.remove(session.getId());
        log.info("WebSocket 已断开: {}", session.getId());
    }

    /**
     * 广播检测结果给所有订阅者
     */
    public void broadcast(FilterResult result) {
        if (SESSIONS.isEmpty()) return;

        try {
            String json = OBJECT_MAPPER.writeValueAsString(result);
            TextMessage message = new TextMessage(json);
            
            for (WebSocketSession session : SESSIONS.values()) {
                if (session.isOpen()) {
                    synchronized (session) {
                        try {
                            session.sendMessage(message);
                        } catch (IOException e) {
                            log.error("推送消息失败: {}", session.getId(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("序列化检测结果失败", e);
        }
    }
}
