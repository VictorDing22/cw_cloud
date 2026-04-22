package com.floatdata.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * 声发射信号采集客户端 - 模拟样机实时采集
 * 
 * 启动方式:
 * AcousticEmissionClient client = new AcousticEmissionClient("localhost", 9090);
 * client.connect();
 * client.startSending();
 */
public class AcousticEmissionClient {
    private static final Logger logger = LoggerFactory.getLogger(AcousticEmissionClient.class);
    private final String host;
    private final int port;
    private Channel channel;
    private EventLoopGroup group;
    private volatile boolean running = false;
    private final Random random = new Random();

    public AcousticEmissionClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * 连接到服务器
     */
    public void connect() {
        try {
            group = new NioEventLoopGroup();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 可以添加编码器/解码器
                        }
                    });

            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            logger.info("客户端已连接到服务器: {}:{}", host, port);
        } catch (InterruptedException e) {
            logger.error("连接失败", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 开始发送信号数据
     */
    public void startSending() {
        running = true;
        int sensorId = 1;
        int sampleRate = 1000000;  // 1 MHz
        String location = "center-horizontal";
        int bufferSize = 4096;

        logger.info("开始发送信号数据...");

        while (running && channel.isActive()) {
            try {
                // 生成模拟信号数据
                long timestamp = System.currentTimeMillis();
                float[] samples = generateSignalSamples(bufferSize, sensorId);

                // 构建二进制数据包
                ByteBuf buffer = encodeSignalData(timestamp, sensorId, sampleRate, 
                                                   location, samples);

                // 发送数据
                channel.writeAndFlush(buffer);

                // 模拟采集间隔 (100ms)
                Thread.sleep(100);

            } catch (InterruptedException e) {
                logger.info("发送线程被中断");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("发送数据异常", e);
                break;
            }
        }

        logger.info("停止发送信号数据");
    }

    /**
     * 生成模拟信号样本
     */
    private float[] generateSignalSamples(int length, int sensorId) {
        float[] samples = new float[length];
        
        // 基础频率
        double baseFreq = 50000 + sensorId * 10000;  // 不同传感器不同频率
        double amplitude = 1.0;

        for (int i = 0; i < length; i++) {
            // 生成多频率信号 + 噪声
            double t = (double) i / 1000000.0;  // 时间 (秒)
            
            // 主信号
            double signal = amplitude * Math.sin(2 * Math.PI * baseFreq * t);
            
            // 添加谐波
            signal += 0.3 * Math.sin(2 * Math.PI * baseFreq * 2 * t);
            signal += 0.1 * Math.sin(2 * Math.PI * baseFreq * 3 * t);
            
            // 添加随机噪声
            signal += (random.nextDouble() - 0.5) * 0.2;
            
            // 随机异常脉冲 (模拟缺陷信号)
            if (random.nextDouble() < 0.001) {
                signal += random.nextDouble() * 5.0;
            }
            
            samples[i] = (float) signal;
        }

        return samples;
    }

    /**
     * 编码信号数据为二进制格式
     */
    private ByteBuf encodeSignalData(long timestamp, int sensorId, int sampleRate,
                                     String location, float[] samples) {
        byte[] locationBytes = location.getBytes();
        
        // 计算总大小
        int size = 8 + 4 + 4 + 2 + locationBytes.length + 4 + samples.length * 4;
        
        ByteBuf buffer = Unpooled.buffer(size);
        
        // 写入数据
        buffer.writeLong(timestamp);
        buffer.writeInt(sensorId);
        buffer.writeInt(sampleRate);
        buffer.writeShort(locationBytes.length);
        buffer.writeBytes(locationBytes);
        buffer.writeInt(samples.length);
        
        for (float sample : samples) {
            buffer.writeFloat(sample);
        }

        return buffer;
    }

    /**
     * 停止发送
     */
    public void stop() {
        running = false;
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        logger.info("客户端已关闭");
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9090;

        AcousticEmissionClient client = new AcousticEmissionClient(host, port);
        
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(client::stop));
        
        client.connect();
        client.startSending();
    }
}
