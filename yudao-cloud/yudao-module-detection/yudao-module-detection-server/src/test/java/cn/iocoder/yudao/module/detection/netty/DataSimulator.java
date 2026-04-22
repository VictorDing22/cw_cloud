package cn.iocoder.yudao.module.detection.netty;

import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * 数据模拟器：向 Netty 接入层发送多通道模拟数据
 */
public class DataSimulator {

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 9999;
        
        System.out.println("连接到 Netty Ingestion Server: " + host + ":" + port);
        
        try (Socket socket = new Socket(host, port);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            
            Random random = new Random();
            String[] channels = {"Channel-A", "Channel-B", "Channel-C"};
            
            while (true) {
                for (String channel : channels) {
                    long ts = System.currentTimeMillis();
                    double val = Math.sin(ts / 1000.0) + random.nextGaussian() * 0.1;
                    
                    // 模拟异常 (每 100 次产生一个较大的峰值)
                    if (random.nextInt(100) == 0) {
                        val += 5.0;
                        System.out.println("产生异常数据 -> " + channel + ": " + val);
                    }

                    byte[] nameBytes = channel.getBytes(StandardCharsets.UTF_8);
                    
                    // Protocol: [4-len] [8-ts] [4-nameLen] [N-name] [8-val]
                    dos.writeInt(8 + 4 + nameBytes.length + 8);
                    dos.writeLong(ts);
                    dos.writeInt(nameBytes.length);
                    dos.write(nameBytes);
                    dos.writeDouble(val);
                }
                
                dos.flush();
                Thread.sleep(10); // 100Hz
            }
        }
    }
}
