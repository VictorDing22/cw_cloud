package cn.iocoder.yudao.module.detection.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Netty 接入层：用于多通道数据接收
 */
@Slf4j
@Component
public class NettyServer {

    @Value("${detection.netty.port:9999}")
    private int port;

    @Value("${detection.netty.data-port:9998}")
    private int dataPort;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private EventLoopGroup dataBossGroup;
    private EventLoopGroup dataWorkerGroup;

    @PostConstruct
    public void start() {
        // 1. Ingestion Server (Ingests data from simulators)
        new Thread(() -> {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4));
                                ch.pipeline().addLast(new NettyServerHandler());
                            }
                        });

                log.info("Netty Ingestion Server 启动在端口: {}", port);
                ChannelFuture f = b.bind(port).sync();
                f.channel().closeFuture().sync();
            } catch (Exception e) {
                log.error("Netty Ingestion Server 启动失败", e);
            } finally {
                stopIngestion();
            }
        }, "netty-ingestion-thread").start();

        // 2. Data Provider Server (Serves data to Flink)
        new Thread(() -> {
            dataBossGroup = new NioEventLoopGroup(1);
            dataWorkerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(dataBossGroup, dataWorkerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new NettyDataProviderHandler());
                            }
                        });

                log.info("Netty Data Provider Server 启动在端口: {}", dataPort);
                ChannelFuture f = b.bind(dataPort).sync();
                f.channel().closeFuture().sync();
            } catch (Exception e) {
                log.error("Netty Data Provider Server 启动失败", e);
            } finally {
                stopDataProvider();
            }
        }, "netty-provider-thread").start();
    }

    @PreDestroy
    public void stop() {
        stopIngestion();
        stopDataProvider();
    }

    private void stopIngestion() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        log.info("Netty Ingestion Server 已停止");
    }

    private void stopDataProvider() {
        if (dataBossGroup != null) dataBossGroup.shutdownGracefully();
        if (dataWorkerGroup != null) dataWorkerGroup.shutdownGracefully();
        log.info("Netty Data Provider Server 已停止");
    }
}
