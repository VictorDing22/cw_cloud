// WebSocket Bridge - 从Kafka读取数据并通过WebSocket推送到前端
const WebSocket = require('ws');
const { Kafka } = require('kafkajs');
const mysql = require('mysql2/promise');

const kafka = new Kafka({
    clientId: 'websocket-bridge',
    brokers: ['127.0.0.1:9092']
});

const consumer = kafka.consumer({ groupId: 'websocket-bridge-group' });

// MySQL连接配置
const dbConfig = {
    host: 'localhost',
    port: 3306,
    user: 'root',
    password: process.env.MYSQL_PASSWORD || '123456',  // 从环境变量获取或使用默认值
    database: 'ruoyi_vue_pro',
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0
};

let dbPool = null;

// 初始化数据库连接池
async function initDatabase() {
    try {
        dbPool = mysql.createPool(dbConfig);
        console.log('数据库连接池初始化成功');
        
        // 测试连接
        const connection = await dbPool.getConnection();
        await connection.ping();
        connection.release();
        console.log('数据库连接测试成功');
    } catch (error) {
        console.error('数据库连接失败:', error.message);
        console.log('继续运行，但数据不会入库');
    }
}

// 创建HTTP服务器用于WebSocket
const http = require('http');
const PORT = 8081;

const server = http.createServer();
const wss = new WebSocket.Server({ 
    server: server,
    path: '/realtime'
});

// 处理端口占用错误
server.on('error', (err) => {
    if (err.code === 'EADDRINUSE') {
        console.error(`端口 ${PORT} 被占用，等待 2 秒后重试...`);
        setTimeout(() => {
            server.close();
            server.listen(PORT);
        }, 2000);
    } else {
        console.error('服务器错误:', err);
        process.exit(1);
    }
});

server.listen(PORT, () => {
    console.log(`WebSocket Bridge 启动在端口 ${PORT}`);
    console.log(`WebSocket URL: ws://localhost:${PORT}/realtime`);
});
// 创建WebSocket服务器 (使用 8083 端口，避免与 Gateway 冲突)
const wss = new WebSocket.Server({ 
    port: 8083,
    path: '/realtime'
});

console.log('WebSocket Bridge 启动在端口 8083');
console.log('WebSocket URL: ws://localhost:8083/realtime');

// 存储所有连接的客户端
const clients = new Set();

// 心跳检测间隔 (30秒)
const HEARTBEAT_INTERVAL = 30000;

wss.on('connection', function connection(ws, request) {
    console.log('新的WebSocket连接:', request.socket.remoteAddress);
    clients.add(ws);
    
    // 标记连接为活跃
    ws.isAlive = true;
    
    // 发送欢迎消息
    ws.send(JSON.stringify({
        type: 'welcome',
        message: '已连接到实时数据流',
        timestamp: Date.now()
    }));
    
    // 处理 pong 响应
    ws.on('pong', function() {
        ws.isAlive = true;
    });
    
    ws.on('message', function incoming(message) {
        const msgStr = message.toString();
        // 处理心跳 ping
        if (msgStr === 'ping') {
            ws.send('pong');
            return;
        }
        console.log('收到消息:', msgStr);
        
        // 回显消息
        ws.send(JSON.stringify({
            type: 'echo',
            data: msgStr,
            timestamp: Date.now()
        }));
    });
    
    ws.on('close', function() {
        console.log('WebSocket连接关闭');
        clients.delete(ws);
    });
    
    ws.on('error', function(error) {
        console.error('WebSocket错误:', error);
        clients.delete(ws);
    });
});

// 心跳检测定时器 - 清理死连接
const heartbeatInterval = setInterval(() => {
    wss.clients.forEach((ws) => {
        if (ws.isAlive === false) {
            console.log('清理无响应的连接');
            clients.delete(ws);
            return ws.terminate();
        }
        ws.isAlive = false;
        ws.ping();
    });
}, HEARTBEAT_INTERVAL);

// 广播数据给所有连接的客户端
function broadcastToClients(data) {
    const message = JSON.stringify(data);
    clients.forEach(client => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(message);
        }
    });
}

// 连接到Kafka并消费数据  
async function startKafkaConsumer() {
    // 尝试连接真实的Kafka滤波数据流
    try {
        await consumer.connect();
        console.log('已连接到Kafka');
        
        await consumer.subscribe({ topic: 'sample-output' });
        console.log('已订阅主题: sample-output (backend.jar滤波后数据)');
        
        await consumer.run({
            eachMessage: async ({ topic, partition, message }) => {
                try {
                    const data = JSON.parse(message.value.toString());
                    
                    // 处理卡尔曼滤波结果数据
                    const processedData = {
                        type: 'signal-data',
                        deviceId: data.deviceId,
                        timestamp: data.timestamp,
                        sequence: data.sequence,
                        sampleRate: data.sampleRate,
                        sampleCount: data.sampleCount,
                        sampleCount: data.sampleCount || data.originalSamples?.length || 0,
                        location: data.location,
                        
                        // 原始和滤波后数据
                        originalSamples: data.originalSamples || [],
                        filteredSamples: data.filteredSamples || [],
                        residuals: data.residuals || [],
                        uncertainties: data.uncertainties || [],
                        
                        // SNR 信噪比数据
                        snrBefore: data.snrBefore,
                        snrAfter: data.snrAfter,
                        snrImprovement: data.snrImprovement,
                        snrBefore: data.snrBefore || 0,
                        snrAfter: data.snrAfter || 0,
                        snrImprovement: data.snrImprovement || 0,
                        
                        // 当前误差
                        currentError: data.currentError || 0,
                        
                        // 异常检测结果
                        anomalies: data.anomalies || [],
                        
                        // 统计信息
                        statistics: data.statistics || {
                            processedSamples: 0,
                            anomalyCount: 0,
                            avgResidual: 0,
                            avgUncertainty: 0
                        },
                        
                        // 滤波器信息
                        filterType: data.filterType,
                        currentError: data.currentError,
                        
                        processingTime: data.processingTime,
                        processingTimeMs: data.processingTimeMs || data.processingTime,
                        filterType: data.filterType || 'LMS',
                        mode: data.mode || 'real-data',
                        kafkaOffset: message.offset,
                        receivedAt: Date.now()
                    };
                    
                    broadcastToClients(processedData);
                    
                } catch (error) {
                    console.error('处理Kafka消息失败:', error);
                }
            },
        });
        
    } catch (error) {
        console.error('Kafka连接失败:', error);
        console.log('启动模拟数据模式...');
        startSimulationMode();
    }
    
    // 注释掉：不再同时启动模拟数据，只使用真实Kafka数据
    // startSimulationMode();
}

// 模拟数据模式
function startSimulationMode() {
    // 模拟真实TDMS数据格式
    const tdmsFiles = ['data-10-left-1.tdms', 'data-10-left-2.tdms', 'data-10-left-3.tdms'];
    let fileIndex = 0;
    
    setInterval(() => {
        const time = Date.now() / 1000;
        // 增加样本数到5000，模拟真实TDMS数据
        const sampleCount = 5000;
        const originalSamples = Array.from({length: sampleCount}, (_, i) => {
            // 模拟真实的声发射信号：衰减正弦波 + 噪声
            const t = i / 2000000; // 2MHz采样率
            const freq = 100000; // 100kHz基频
            const burst = Math.exp(-t * 50) * Math.sin(2 * Math.PI * freq * t);
            const noise = (Math.random() - 0.5) * 0.1;
            return burst + noise;
        });
        const filteredSamples = originalSamples.map(v => v * 0.85 + (Math.random() - 0.5) * 0.02);
        
        // 轮换TDMS文件名
        fileIndex = (fileIndex + 1) % tdmsFiles.length;
        
        // 动态速率：在目标速率附近波动，模拟真实处理速度变化
        const baseRate = 2000000;
        const fluctuation = 0.15; // 15%波动
        const dynamicRate = Math.floor(baseRate * (1 + (Math.random() - 0.5) * fluctuation));
        
        // 样本数也随机变化
        const dynamicSampleCount = Math.floor(sampleCount * (0.9 + Math.random() * 0.2));
        
        const mockData = {
            type: 'signal-data',
            deviceId: `tdms-${tdmsFiles[fileIndex]}-_unnamedTask<${7+fileIndex}>-Dev2/ai1`,
            timestamp: Date.now(),
            sampleRate: dynamicRate,  // 动态波动的速率
            location: 'TDMS-Simulator',
            originalSamples: originalSamples.slice(0, dynamicSampleCount),
            filteredSamples: filteredSamples.slice(0, dynamicSampleCount),
            currentError: Math.abs(originalSamples[0] - filteredSamples[0]),
            statistics: {
                min: Math.min(...originalSamples),
                max: Math.max(...originalSamples),
                avg: originalSamples.reduce((a, b) => a + b, 0) / originalSamples.length,
                rms: Math.sqrt(originalSamples.reduce((sum, val) => sum + val * val, 0) / originalSamples.length),
                processedSamples: dynamicSampleCount
            },
            mode: 'real-format-dynamic',  // 动态模式
            receivedAt: Date.now()
        };
        
        // 广播给客户端
        broadcastToClients(mockData);
        
        // 数据入库（异步，不阻塞实时推送）
        saveToDatabase(mockData).catch(err => {
            console.error('数据入库失败:', err.message);
        });
        
    }, 500); // 每500ms发送一次数据
}

// 数据入库函数
async function saveToDatabase(data) {
    if (!dbPool) return;
    
    try {
        const connection = await dbPool.getConnection();
        
        try {
            // 插入滤波结果记录
            const filterRecord = await connection.execute(`
                INSERT INTO filter_result_record (
                    device_id, device_name, session_id,
                    original_samples, filtered_samples, sample_rate, sample_count,
                    filter_type, learning_rate, snr_improvement,
                    original_rms, filtered_rms, processing_time,
                    creator
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            `, [
                data.deviceId,
                data.deviceId === 'sensor-simulation' ? '模拟声发射传感器' : data.deviceId,
                'websocket-bridge-session',
                JSON.stringify(data.originalSamples),
                JSON.stringify(data.filteredSamples),
                data.sampleRate || 1000000,
                data.originalSamples?.length || 0,
                'LMS',
                0.01,
                calculateSNRImprovement(data.originalSamples, data.filteredSamples),
                data.statistics?.rms || 0,
                calculateRMS(data.filteredSamples),
                Math.floor(Math.random() * 10) + 5, // 模拟处理时间5-15ms
                'websocket-bridge'
            ]);
            
            // 更新设备实时状态
            await connection.execute(`
                INSERT INTO device_realtime_status (
                    device_id, device_name, connection_status, last_heartbeat,
                    total_packets, current_snr, data_quality_score
                ) VALUES (?, ?, ?, NOW(), 1, ?, ?)
                ON DUPLICATE KEY UPDATE
                    connection_status = VALUES(connection_status),
                    last_heartbeat = NOW(),
                    total_packets = total_packets + 1,
                    current_snr = VALUES(current_snr),
                    packets_per_second = (total_packets + 1) / TIMESTAMPDIFF(SECOND, session_start_time, NOW()),
                    update_time = NOW()
            `, [
                data.deviceId,
                data.deviceId === 'sensor-simulation' ? '模拟声发射传感器' : data.deviceId,
                'ONLINE',
                calculateSNRImprovement(data.originalSamples, data.filteredSamples),
                95 + Math.random() * 5 // 模拟数据质量分数 95-100
            ]);
            
            // 随机生成异常检测记录 (5%概率)
            if (Math.random() < 0.05) {
                await connection.execute(`
                    INSERT INTO anomaly_detection_record (
                        device_id, device_name, filter_record_id,
                        anomaly_type, anomaly_score, alert_level,
                        detection_algorithm, description, creator
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                `, [
                    data.deviceId,
                    data.deviceId === 'sensor-simulation' ? '模拟声发射传感器' : data.deviceId,
                    filterRecord[0].insertId,
                    Math.random() > 0.5 ? 'FREQUENCY' : 'AMPLITUDE',
                    0.3 + Math.random() * 0.4, // 异常分数 0.3-0.7
                    Math.random() > 0.7 ? 'WARN' : 'INFO',
                    'Statistical Analysis',
                    '检测到轻微信号异常，建议关注',
                    'websocket-bridge'
                ]);
            }
            
        } finally {
            connection.release();
        }
        
    } catch (error) {
        console.error('数据库操作错误:', error.message);
    }
}

// 计算SNR改善值
function calculateSNRImprovement(original, filtered) {
    if (!original || !filtered || original.length === 0) return 0;
    
    const originalPower = original.reduce((sum, val) => sum + val * val, 0) / original.length;
    const filteredPower = filtered.reduce((sum, val) => sum + val * val, 0) / filtered.length;
    
    if (filteredPower === 0) return 0;
    return 10 * Math.log10(originalPower / filteredPower);
}

// 计算RMS
function calculateRMS(samples) {
    if (!samples || samples.length === 0) return 0;
    const sumSquares = samples.reduce((sum, val) => sum + val * val, 0);
    return Math.sqrt(sumSquares / samples.length);
}

// 启动数据库和Kafka消费者
initDatabase();
startKafkaConsumer();

// 优雅关闭
process.on('SIGINT', async () => {
    console.log('正在关闭WebSocket Bridge...');
    clearInterval(heartbeatInterval);
    await consumer.disconnect();
    wss.close();
    server.close();
    process.exit(0);
});

process.on('SIGTERM', async () => {
    console.log('收到 SIGTERM，正在关闭...');
    clearInterval(heartbeatInterval);
    await consumer.disconnect();
    wss.close();
    server.close();
    process.exit(0);
});
