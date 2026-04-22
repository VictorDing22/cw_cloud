package cn.iocoder.yudao.module.monitor.flink;

import cn.iocoder.yudao.framework.common.util.spring.SpringUtils;
import cn.iocoder.yudao.module.monitor.api.dto.MonitorStreamMessage;
import cn.iocoder.yudao.module.monitor.service.MonitorResultHub;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

/**
 * Flink 结果下沉到 WebSocket 的 Sink。
 *
 * 注意：不能在 Flink 算子中直接持有 Spring Bean（例如 MonitorResultHub），
 * 否则在闭包清理和分发时会出现 NotSerializableException。
 *
 * 这里通过 transient 字段 + 在 open() 阶段使用 SpringUtils.getBean(...) 懒加载 Bean，
 * 避免在序列化阶段去序列化整个 Spring Bean。
 */
public class MonitorResultSink extends RichSinkFunction<MonitorStreamMessage> {

    /**
     * 使用 transient，避免被 Flink / Java 序列化。
     * 实际 Bean 在 open() 生命周期回调中通过 Spring 容器获取。
     */
    private transient MonitorResultHub hub;

    public MonitorResultSink() {
    }

    @Override
    public void open(Configuration parameters) {
        // 在 TaskManager 真实运行时，从 Spring 容器中获取 MonitorResultHub
        this.hub = SpringUtils.getBean(MonitorResultHub.class);
        if (this.hub == null) {
            throw new IllegalStateException("MonitorResultSink open() 获取 MonitorResultHub 失败，Spring 上下文中不存在该 Bean");
        }
    }

    @Override
    public void invoke(MonitorStreamMessage value, Context context) {
        if (hub != null) {
            hub.send(value);
        }
    }
}
