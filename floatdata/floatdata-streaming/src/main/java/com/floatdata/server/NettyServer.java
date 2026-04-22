package com.floatdata.server;

import com.floatdata.utils.ConfigLoader;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty 服务器 - 接收样机的实时信号数据
 * 
 * 启动方式:
 * NettyServer server = new NettyServer();
 * server.start();
 */
public class NettyServer {
    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
    private final String host;
    private final int port;
    private final int threads;
    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private KafkaProducerWrapper kafkaProducer;

    public NettyServer() {
        this.host = ConfigLoader.getString("netty.server.host", "0.0.0.0");
        this.port = ConfigLoader.getInt("netty.server.port", 9090);
        this.threads = ConfigLoader.getInt("netty.server.threads", 8);
    }

    public void start() {
        try {
            kafkaProducer = new KafkaProducerWrapper();
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup(threads);

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new SignalHandler(kafkaProducer));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            serverChannel = bootstrap.bind(host, port).sync().channel();
            logger.info("Netty 服务器启动成功: {}:{}", host, port);
            
            serverChannel.closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error("Netty 服务器启动失败", e);
            Thread.currentThread().interrupt();
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        logger.info("关闭 Netty 服务器...");
        if (kafkaProducer != null) {
            kafkaProducer.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        NettyServer server = new NettyServer();
        server.start();
    }
}
