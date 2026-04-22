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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * High throughput sender that replays real TDMS samples (exported to binary float32)
 * over the Netty protocol at a configurable rate.
 */
public class HighRateDataSender {
    private static final Logger logger = LoggerFactory.getLogger(HighRateDataSender.class);

    private final String host;
    private final int port;
    private final int sensorId;
    private final String location;
    private final int sampleRate;
    private final int chunkSize;
    private final long targetRate; // samples per second
    private final int reportIntervalSeconds;
    private final float[] samples;

    private EventLoopGroup group;
    private Channel channel;
    private volatile boolean running = false;
    private int cursor = 0;

    public HighRateDataSender(String host, int port, int sensorId, String location,
                              int sampleRate, int chunkSize, long targetRate,
                              int reportIntervalSeconds, float[] samples) {
        if (samples == null || samples.length == 0) {
            throw new IllegalArgumentException("Sample array must not be empty");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be > 0");
        }
        if (targetRate <= 0) {
            throw new IllegalArgumentException("targetRate must be > 0");
        }
        this.host = host;
        this.port = port;
        this.sensorId = sensorId;
        this.location = location;
        this.sampleRate = sampleRate;
        this.chunkSize = chunkSize;
        this.targetRate = targetRate;
        this.reportIntervalSeconds = reportIntervalSeconds;
        this.samples = samples;
    }

    public void start() throws InterruptedException {
        group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // No additional handlers required (raw ByteBuf)
                    }
                });

        ChannelFuture future = bootstrap.connect(host, port).sync();
        channel = future.channel();
        running = true;
        logger.info("HighRateDataSender connected to {}:{}", host, port);
        sendLoop();
    }

    private void sendLoop() {
        final byte[] locationBytes = location.getBytes();
        final int headerSize = 8 + 4 + 4 + 2 + locationBytes.length + 4;
        final long nanosPerSample = (long) (1_000_000_000.0 / targetRate);

        long sentSamples = 0;
        long start = System.nanoTime();
        long lastReport = start;
        long lastReportSamples = 0;

        try {
            while (running && channel.isActive()) {
                long timestamp = System.currentTimeMillis();
                ByteBuf buffer = Unpooled.buffer(headerSize + chunkSize * 4);
                buffer.writeLong(timestamp);
                buffer.writeInt(sensorId);
                buffer.writeInt(sampleRate);
                buffer.writeShort(locationBytes.length);
                buffer.writeBytes(locationBytes);
                buffer.writeInt(chunkSize);

                writeSamples(buffer, chunkSize);

                channel.writeAndFlush(buffer).syncUninterruptibly();
                sentSamples += chunkSize;

                long expectedElapsed = sentSamples * nanosPerSample;
                long actualElapsed = System.nanoTime() - start;
                if (expectedElapsed > actualElapsed) {
                    long sleepNanos = expectedElapsed - actualElapsed;
                    if (sleepNanos > 0) {
                        TimeUnit.NANOSECONDS.sleep(Math.min(sleepNanos, 5_000_000)); // cap sleep to 5ms
                    }
                }

                long now = System.nanoTime();
                if ((now - lastReport) >= reportIntervalSeconds * 1_000_000_000L) {
                    long intervalSamples = sentSamples - lastReportSamples;
                    double rate = intervalSamples / ((now - lastReport) / 1_000_000_000.0);
                    logger.info("Sent {} samples total | {} samples/sec", sentSamples, rate);
                    lastReport = now;
                    lastReportSamples = sentSamples;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Sender interrupted");
        } catch (Exception e) {
            logger.error("Error during send loop", e);
        } finally {
            stop();
        }
    }

    private void writeSamples(ByteBuf buffer, int count) {
        int remaining = count;
        while (remaining > 0) {
            int available = samples.length - cursor;
            int toCopy = Math.min(available, remaining);
            for (int i = 0; i < toCopy; i++) {
                buffer.writeFloat(samples[cursor++]);
            }
            if (cursor >= samples.length) {
                cursor = 0;
            }
            remaining -= toCopy;
        }
    }

    public void stop() {
        running = false;
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        logger.info("HighRateDataSender shutdown");
    }

    private static float[] loadSamples(Path path, int maxSamples) throws IOException {
        try (FileChannel channel = new FileInputStream(path.toFile()).getChannel()) {
            long fileSize = channel.size();
            long totalFloats = fileSize / 4;
            if (maxSamples > 0) {
                totalFloats = Math.min(totalFloats, maxSamples);
            }
            if (totalFloats > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Too many samples in file: " + totalFloats);
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect((int) (totalFloats * 4));
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            int read;
            while (buffer.hasRemaining() && (read = channel.read(buffer)) != -1) {
                if (read == 0) {
                    Thread.yield();
                }
            }
            buffer.flip();
            float[] samples = new float[(int) totalFloats];
            buffer.asFloatBuffer().get(samples);
            return samples;
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                int idx = arg.indexOf('=');
                if (idx > 2) {
                    map.put(arg.substring(2, idx), arg.substring(idx + 1));
                } else {
                    map.put(arg.substring(2), "true");
                }
            }
        }
        return map;
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        String host = options.getOrDefault("host", "localhost");
        int port = Integer.parseInt(options.getOrDefault("port", "9090"));
        int sensorId = Integer.parseInt(options.getOrDefault("sensorId", "900"));
        String location = options.getOrDefault("location", "high-rate-java");
        int sampleRate = Integer.parseInt(options.getOrDefault("sampleRate", "1000000"));
        int chunkSize = Integer.parseInt(options.getOrDefault("chunkSize", "2000"));
        long targetRate = Long.parseLong(options.getOrDefault("targetRate", "2000000"));
        int reportInterval = Integer.parseInt(options.getOrDefault("reportInterval", "2"));
        Path dataFile = new File(options.getOrDefault("dataFile", "tdms-export.bin")).toPath();
        int maxSamples = Integer.parseInt(options.getOrDefault("maxSamples", "6000000"));

        logger.info("Loading samples from {} ...", dataFile.toAbsolutePath());
        float[] samples = loadSamples(dataFile, maxSamples);
        logger.info("Loaded {} samples", samples.length);

        HighRateDataSender sender = new HighRateDataSender(
                host, port, sensorId, location, sampleRate,
                chunkSize, targetRate, reportInterval, samples
        );

        Runtime.getRuntime().addShutdownHook(new Thread(sender::stop));
        sender.start();
    }
}
