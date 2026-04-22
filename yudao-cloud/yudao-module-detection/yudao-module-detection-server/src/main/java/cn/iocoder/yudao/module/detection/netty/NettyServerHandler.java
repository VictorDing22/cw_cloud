package cn.iocoder.yudao.module.detection.netty;

import cn.iocoder.yudao.module.detection.api.dto.TdmsSample;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Netty 处理器：解析多通道数据并入队
 */
@Slf4j
public class NettyServerHandler extends ChannelInboundHandlerAdapter {

    // 使用阻塞队列作为 Flink 数据源的中转（生产环境建议使用 MQ）
    public static final BlockingQueue<TdmsSample> DATA_QUEUE = new LinkedBlockingQueue<>(100000);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf in = (ByteBuf) msg;
        try {
            if (in.readableBytes() < 12) return; // min: timestamp(8) + nameLen(4)

            long timestamp = in.readLong();
            int nameLen = in.readInt();
            if (in.readableBytes() < nameLen + 8) return;

            String channel = in.readCharSequence(nameLen, StandardCharsets.UTF_8).toString();
            double value = in.readDouble();

            TdmsSample sample = new TdmsSample(timestamp, value, channel);
            
            // 放入队列供 Flink 消费
            if (!DATA_QUEUE.offer(sample)) {
                log.warn("数据队列已满，丢弃样本: {}", sample);
            }

        } catch (Exception e) {
            log.error("解析数据失败", e);
        } finally {
            in.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Netty 连接异常", cause);
        ctx.close();
    }
}
