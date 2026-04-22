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

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-connection high-throughput sender that uses multiple parallel connections
 * to maximize data sending rate. Each connection runs in its own thread.
 */
public class MultiProcessDataSender {
    private static final Logger logger = LoggerFactory.getLogger(MultiProcessDataSender.class);

    private final String host;
    private final int port;
    private final int numConnections;
    private final int sensorIdBase;
    private final String location;
    private final int sampleRate;
    private final int chunkSize;
    private final long targetRatePerConnection;
    private final int reportIntervalSeconds;
    private final float[] samples;

    private final List<SenderWorker> workers = new ArrayList<>();
    private final AtomicLong totalSentSamples = new AtomicLong(0);
    private volatile boolean running = false;

    public MultiProcessDataSender(String host, int port, int numConnections,
                                   int sensorIdBase, String location, int sampleRate,
                                   int chunkSize, long totalTargetRate,
                                   int reportIntervalSeconds, float[] samples) {
        if (numConnections <= 0) {
            throw new IllegalArgumentException("numConnections must be > 0");
        }
        if (samples == null || samples.length == 0) {
            throw new IllegalArgumentException("Sample array must not be empty");
        }
        this.host = host;
        this.port = port;
        this.numConnections = numConnections;
        this.sensorIdBase = sensorIdBase;
        this.location = location;
        this.sampleRate = sampleRate;
        this.chunkSize = chunkSize;
        this.targetRatePerConnection = totalTargetRate / numConnections;
        this.reportIntervalSeconds = reportIntervalSeconds;
        this.samples = samples;
    }

    public void start() throws InterruptedException {
        logger.info("Starting MultiProcessDataSender with {} connections", numConnections);
        logger.info("Target rate per connection: {} samples/sec", targetRatePerConnection);
        logger.info("Total target rate: {} samples/sec", targetRatePerConnection * numConnections);

        CountDownLatch startLatch = new CountDownLatch(numConnections);
        running = true;

        // Create and start worker threads
        for (int i = 0; i < numConnections; i++) {
            int sensorId = sensorIdBase + i;
            SenderWorker worker = new SenderWorker(i, sensorId, startLatch);
            workers.add(worker);
            new Thread(worker, "SenderWorker-" + i).start();
        }

        // Wait for all workers to connect
        startLatch.await();
        logger.info("All {} connections established", numConnections);

        // Start reporting thread
        startReportingThread();
    }

    public void stop() {
        running = false;
        logger.info("Stopping all workers...");
        for (SenderWorker worker : workers) {
            worker.stop();
        }
        workers.clear();
    }

    private void startReportingThread() {
        Thread reportThread = new Thread(() -> {
            long lastReport = System.currentTimeMillis();
            long lastSamples = 0;

            while (running) {
                try {
                    Thread.sleep(reportIntervalSeconds * 1000L);

                    long currentTime = System.currentTimeMillis();
                    long currentSamples = totalSentSamples.get();
                    long elapsed = currentTime - lastReport;
                    long sentDiff = currentSamples - lastSamples;

                    if (elapsed > 0) {
                        double rate = (sentDiff * 1000.0) / elapsed;
                        logger.info("Sent {} samples total | {} samples/sec",
                                currentSamples, String.format("%.2f", rate));
                    }

                    lastReport = currentTime;
                    lastSamples = currentSamples;
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "ReportingThread");
        reportThread.setDaemon(true);
        reportThread.start();
    }

    /**
     * Worker class that handles a single connection
     */
    private class SenderWorker implements Runnable {
        private final int workerId;
        private final int sensorId;
        private final CountDownLatch startLatch;
        private EventLoopGroup group;
        private Channel channel;
        private volatile boolean workerRunning = false;
        private int cursor = 0;

        public SenderWorker(int workerId, int sensorId, CountDownLatch startLatch) {
            this.workerId = workerId;
            this.sensorId = sensorId;
            this.startLatch = startLatch;
        }

        @Override
        public void run() {
            try {
                connect();
                sendLoop();
            } catch (Exception e) {
                logger.error("Worker {} error: {}", workerId, e.getMessage(), e);
            } finally {
                cleanup();
            }
        }

        private void connect() throws InterruptedException {
            group = new NioEventLoopGroup(1); // One thread per worker
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // No handlers needed for raw ByteBuf
                        }
                    });

            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            workerRunning = true;
            logger.debug("Worker {} connected (sensorId={})", workerId, sensorId);
            startLatch.countDown();
        }

        private void sendLoop() {
            final byte[] locationBytes = location.getBytes();
            final long nanosPerSample = (long) (1_000_000_000.0 / targetRatePerConnection);

            long sentSamples = 0;
            long nextSendTime = System.nanoTime();

            try {
                while (running && workerRunning && channel.isActive()) {
                    // Build packet
                    ByteBuf buf = buildPacket(locationBytes);

                    // Send
                    channel.writeAndFlush(buf);
                    sentSamples += chunkSize;
                    totalSentSamples.addAndGet(chunkSize);

                    // Rate control
                    nextSendTime += nanosPerSample * chunkSize;
                    long now = System.nanoTime();
                    long sleepNanos = nextSendTime - now;

                    if (sleepNanos > 0) {
                        if (sleepNanos > 1_000_000) { // > 1ms
                            TimeUnit.NANOSECONDS.sleep(sleepNanos);
                        } else {
                            // Busy wait for sub-millisecond precision
                            while (System.nanoTime() < nextSendTime) {
                                Thread.yield();
                            }
                        }
                    } else if (sleepNanos < -100_000_000) { // Behind by >100ms
                        // Reset timing if too far behind
                        nextSendTime = System.nanoTime();
                    }

                    // Advance cursor
                    cursor += chunkSize;
                    if (cursor + chunkSize > samples.length) {
                        cursor = 0; // Loop
                    }
                }
            } catch (Exception e) {
                logger.error("Worker {} send error: {}", workerId, e.getMessage());
            }

            logger.debug("Worker {} stopped, sent {} samples", workerId, sentSamples);
        }

        private ByteBuf buildPacket(byte[] locationBytes) {
            final int headerSize = 8 + 4 + 4 + 2 + locationBytes.length + 4;
            final int packetSize = headerSize + chunkSize * 4;

            ByteBuf buf = Unpooled.buffer(packetSize);
            buf.writeLong(System.currentTimeMillis());
            buf.writeInt(sensorId);
            buf.writeInt(sampleRate);
            buf.writeShort(locationBytes.length);
            buf.writeBytes(locationBytes);
            buf.writeInt(chunkSize);

            // Write samples
            for (int i = 0; i < chunkSize; i++) {
                int idx = (cursor + i) % samples.length;
                buf.writeFloat(samples[idx]);
            }

            return buf;
        }

        public void stop() {
            workerRunning = false;
            cleanup();
        }

        private void cleanup() {
            if (channel != null && channel.isOpen()) {
                try {
                    channel.close().sync();
                } catch (Exception e) {
                    logger.debug("Error closing channel: {}", e.getMessage());
                }
            }
            if (group != null) {
                group.shutdownGracefully();
            }
        }
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        // Parse arguments
        String dataFile = "tdms-export.bin";
        String host = "localhost";
        int port = 9090;
        int numConnections = 4;
        int chunkSize = 2000;
        long targetRate = 2_000_000; // 2M samples/sec total
        int maxSamples = 60_000_000; // 60M samples = 30 seconds at 2M/s
        int reportInterval = 2;

        for (String arg : args) {
            if (arg.startsWith("--dataFile=")) {
                dataFile = arg.substring("--dataFile=".length());
            } else if (arg.startsWith("--host=")) {
                host = arg.substring("--host=".length());
            } else if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.startsWith("--numConnections=")) {
                numConnections = Integer.parseInt(arg.substring("--numConnections=".length()));
            } else if (arg.startsWith("--chunkSize=")) {
                chunkSize = Integer.parseInt(arg.substring("--chunkSize=".length()));
            } else if (arg.startsWith("--targetRate=")) {
                targetRate = Long.parseLong(arg.substring("--targetRate=".length()));
            } else if (arg.startsWith("--maxSamples=")) {
                maxSamples = Integer.parseInt(arg.substring("--maxSamples=".length()));
            } else if (arg.startsWith("--reportInterval=")) {
                reportInterval = Integer.parseInt(arg.substring("--reportInterval=".length()));
            }
        }

        logger.info("============================================================");
        logger.info("Multi-Process High-Rate Data Sender");
        logger.info("============================================================");
        logger.info("Data file: {}", dataFile);
        logger.info("Target: {}:{}", host, port);
        logger.info("Connections: {}", numConnections);
        logger.info("Chunk size: {} samples", chunkSize);
        logger.info("Target rate: {} samples/sec TOTAL", targetRate);
        logger.info("Target per connection: {} samples/sec", targetRate / numConnections);
        logger.info("Max samples: {}", maxSamples);
        logger.info("============================================================");

        // Load samples
        float[] samples = loadSamples(dataFile, maxSamples);
        if (samples == null || samples.length == 0) {
            logger.error("Failed to load samples from {}", dataFile);
            System.exit(1);
        }

        logger.info("Loaded {} samples ({} MB)",
                samples.length, (samples.length * 4) / (1024 * 1024));

        // Create and start sender
        MultiProcessDataSender sender = new MultiProcessDataSender(
                host, port, numConnections, 1, "center", 1_000_000,
                chunkSize, targetRate, reportInterval, samples
        );

        try {
            sender.start();

            // Keep running until interrupted
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.info("Interrupted, stopping...");
        } finally {
            sender.stop();
        }
    }

    private static float[] loadSamples(String dataFile, int maxSamples) {
        try (FileInputStream fis = new FileInputStream(dataFile);
             FileChannel fc = fis.getChannel()) {

            long fileSize = fc.size();
            long maxBytes = (long) maxSamples * 4;
            int bytesToRead = (int) Math.min(fileSize, maxBytes);
            int numSamples = bytesToRead / 4;

            ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            fc.read(buffer);
            buffer.flip();

            float[] samples = new float[numSamples];
            for (int i = 0; i < numSamples; i++) {
                samples[i] = buffer.getFloat();
            }

            logger.info("Loaded {} samples from {}", numSamples, dataFile);
            return samples;

        } catch (IOException e) {
            logger.error("Error loading samples: {}", e.getMessage(), e);
            return null;
        }
    }
}
