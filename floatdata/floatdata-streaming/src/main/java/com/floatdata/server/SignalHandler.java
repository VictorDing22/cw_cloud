package com.floatdata.server;

import com.floatdata.utils.SignalData;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty Signal Handler - Receives acoustic emission signals from client
 */
public class SignalHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger logger = LoggerFactory.getLogger(SignalHandler.class);
    private final KafkaProducerWrapper kafkaProducer;
    private long receivedCount = 0;

    public SignalHandler(KafkaProducerWrapper kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        logger.info("Client connected: {}", ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        try {
            // Read binary format data: [timestamp(8)] [sensorId(4)] [sampleRate(4)] 
            //                          [locationLen(2)] [location] [samplesLen(4)] [samples...]
            
            long timestamp = msg.readLong();
            int sensorId = msg.readInt();
            int sampleRate = msg.readInt();
            
            // Read location string
            short locationLen = msg.readShort();
            byte[] locationBytes = new byte[locationLen];
            msg.readBytes(locationBytes);
            String location = new String(locationBytes);
            
            // Read sample data
            int samplesLen = msg.readInt();
            float[] samples = new float[samplesLen];
            for (int i = 0; i < samplesLen; i++) {
                samples[i] = msg.readFloat();
            }
            
            // Create signal data object
            SignalData signalData = new SignalData(timestamp, sensorId, samples, 
                                                    sampleRate, location);
            
            // Send to Kafka
            kafkaProducer.send(signalData);
            
            receivedCount++;
            if (receivedCount % 100 == 0) {
                logger.info("Received {} signal data packets", receivedCount);
            }
            
        } catch (Exception e) {
            logger.error("Error processing signal data", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Channel exception", cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.info("Client disconnected: {}, total received: {}", ctx.channel().remoteAddress(), receivedCount);
    }
}
