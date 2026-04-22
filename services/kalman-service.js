// 卡尔曼滤波与异常检测主服务
const { Kafka } = require('kafkajs');
const { deviceManager, initDatabase, saveResults } = require('./kalman-filter-processor');

const kafka = new Kafka({
    clientId: 'kalman-service',
    brokers: ['localhost:9092']
});

const consumer = kafka.consumer({ groupId: 'kalman-filter-group' });
const producer = kafka.producer();

let messageCount = 0;
let anomalyCount = 0;

async function startKalmanService() {
    console.log('🔬 启动卡尔曼滤波与异常检测服务...');
    
    try {
        // 初始化数据库
        await initDatabase();
        
        // 连接Kafka
        await consumer.connect();
        await producer.connect();
        console.log('✅ Kafka连接成功');
        
        // 订阅原始数据主题
        await consumer.subscribe({ topic: 'acoustic-emission-signal' });
        console.log('📊 已订阅主题: acoustic-emission-signal');
        
        console.log('🎯 开始处理实时信号数据...');
        console.log('⏱️  保持时序: ✅');
        console.log('🔄 卡尔曼滤波: ✅');  
        console.log('🚨 异常检测: ✅');
        console.log('📱 多设备支持: ✅');
        console.log('');
        
        // 处理消息
        await consumer.run({
            eachMessage: async ({ topic, partition, message }) => {
                try {
                    const data = JSON.parse(message.value.toString());
                    messageCount++;
                    
                    // 提取数据
                    const deviceId = data.sensorId || 'unknown';
                    const samples = data.samples || [];
                    const timestamp = data.timestamp || Date.now();
                    const sequence = parseInt(message.offset); // 使用Kafka offset作为序列号
                    
                    if (samples.length === 0) return;
                    
                    // 使用设备管理器处理信号（保持时序）
                    const results = deviceManager.processSignal(deviceId, samples, timestamp, sequence);
                    
                    if (results.length > 0) {
                        // 统计异常
                        const anomalies = results.filter(r => r.anomaly);
                        anomalyCount += anomalies.length;
                        
                        // 准备输出数据
                        const outputData = {
                            deviceId: deviceId,
                            timestamp: timestamp,
                            sequence: sequence,
                            sampleRate: data.sampleRate,
                            location: data.location,
                            originalSamples: samples.slice(0, 100), // 前100个原始样本
                            filteredSamples: results.slice(0, 100).map(r => r.filtered), // 滤波结果
                            residuals: results.slice(0, 100).map(r => r.residual), // 残差
                            uncertainties: results.slice(0, 100).map(r => r.uncertainty), // 不确定性
                            anomalies: anomalies.map(r => ({
                                timestamp: r.timestamp,
                                sequence: r.sequence,
                                type: r.anomaly.type,
                                score: r.anomaly.score,
                                alertLevel: r.anomaly.alertLevel,
                                description: r.anomaly.description
                            })),
                            statistics: {
                                processedSamples: results.length,
                                anomalyCount: anomalies.length,
                                avgResidual: results.reduce((sum, r) => sum + Math.abs(r.residual), 0) / results.length,
                                avgUncertainty: results.reduce((sum, r) => sum + r.uncertainty, 0) / results.length
                            },
                            processingTime: Date.now() - timestamp
                        };
                        
                        // 发送到输出主题
                        await producer.send({
                            topic: 'device-filtered-data',
                            key: deviceId.toString(),
                            value: JSON.stringify(outputData)
                        });
                        
                        // 保存异常到数据库
                        if (anomalies.length > 0) {
                            await saveResults(deviceId, results);
                        }
                        
                        // 定期输出统计信息
                        if (messageCount % 10 === 0) {
                            const deviceStats = deviceManager.getDeviceStats();
                            console.log(`📊 处理统计 [消息${messageCount}]:`);
                            console.log(`   📈 总异常: ${anomalyCount}`);
                            
                            Object.entries(deviceStats).forEach(([deviceId, stats]) => {
                                console.log(`   📱 设备${deviceId}: 序列${stats.lastSequence}, 处理${stats.processedCount}点, 状态[${stats.filterState[0].toFixed(3)}, ${stats.filterState[1].toFixed(3)}]`);
                            });
                            
                            if (anomalies.length > 0) {
                                console.log(`   🚨 本批异常: ${anomalies.length}个`);
                                anomalies.forEach(r => {
                                    console.log(`      ⚠️  ${r.anomaly.type} (${r.anomaly.alertLevel}) - ${r.anomaly.description}`);
                                });
                            }
                            console.log('');
                        }
                    }
                    
                } catch (error) {
                    console.error('❌ 处理消息失败:', error.message);
                }
            }
        });
        
    } catch (error) {
        console.error('❌ 卡尔曼服务启动失败:', error.message);
    }
}

// 优雅关闭
process.on('SIGINT', async () => {
    console.log('\n🛑 正在关闭卡尔曼滤波服务...');
    
    const deviceStats = deviceManager.getDeviceStats();
    console.log('\n📊 最终统计:');
    console.log(`   📈 总处理消息: ${messageCount}`);
    console.log(`   🚨 总检测异常: ${anomalyCount}`);
    
    Object.entries(deviceStats).forEach(([deviceId, stats]) => {
        console.log(`   📱 设备${deviceId}: 处理了${stats.processedCount}个采样点`);
    });
    
    try {
        await consumer.disconnect();
        await producer.disconnect();
        console.log('✅ Kafka连接已关闭');
    } catch (error) {
        console.error('❌ 关闭连接失败:', error.message);
    }
    
    console.log('👋 卡尔曼滤波服务已关闭');
    process.exit(0);
});

// 启动服务
startKalmanService();
