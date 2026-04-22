package cn.iocoder.yudao.module.monitor.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.monitor.api.dto.MonitorStreamMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class MonitorResultHub {

    private final Map<String, Set<WebSocketSession>> jobSessions = new ConcurrentHashMap<>();

    public void register(String jobId, WebSocketSession session) {
        jobSessions.computeIfAbsent(jobId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(session);
    }

    public void unregister(WebSocketSession session) {
        jobSessions.values().forEach(set -> set.remove(session));
    }

    public void send(MonitorStreamMessage message) {
        Set<WebSocketSession> sessions = jobSessions.get(message.getJobId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload = JsonUtils.toJsonString(message);
        sessions.forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(payload));
                } catch (IOException e) {
                    log.warn("推送实时数据失败: {}", e.getMessage());
                }
            }
        });
    }
}
