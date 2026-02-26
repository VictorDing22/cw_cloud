package cn.iocoder.yudao.module.detection.netty;

import cn.iocoder.yudao.module.detection.api.dto.TdmsSample;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * Netty 数据提供者：将队列中的数据广播给所有连接的客户端（如 Flink）
 */
@Slf4j
public class NettyDataProviderHandler extends ChannelInboundHandlerAdapter {

    private static final ChannelGroup ALL_CHANNELS = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    static {
        // 启动一个后台线程，不断从队列取数据并发送给所有通道
        new Thread(() -> {
            while (true) {
                try {
                    TdmsSample sample = NettyServerHandler.DATA_QUEUE.take();
                    if (ALL_CHANNELS.isEmpty()) continue;

                    // 序列化数据并广播
                    for (Channel ch : ALL_CHANNELS) {
                        ByteBuf buf = ch.alloc().buffer();
                        byte[] nameBytes = sample.getChannel().getBytes(StandardCharsets.UTF_8);
                        
                        buf.writeInt(8 + 4 + nameBytes.length + 8); // total length
                        buf.writeLong(sample.getTimestamp());
                        buf.writeInt(nameBytes.length);
                        buf.writeBytes(nameBytes);
                        buf.writeDouble(sample.getValue());
                        
                        ch.writeAndFlush(buf);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("广播数据失败", e);
                }
            }
        }, "netty-broadcast-thread").start();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ALL_CHANNELS.add(ctx.channel());
        log.info("Flink 客户端已连接: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ALL_CHANNELS.remove(ctx.channel());
        log.info("Flink 客户端已断开: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("数据提供者连接异常", cause);
        ctx.close();
    }
}
