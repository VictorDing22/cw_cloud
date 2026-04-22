// TDMS Data Simulator - 读取TDMS文件并模拟发送数据到Backend服务
const fs = require('fs');
const path = require('path');
const axios = require('axios');

// 配置
const config = {
    // TDMS数据目录
    dataDir: path.join(__dirname, 'floatdata', 'data'),
    
    // Backend服务地址
    backendUrl: 'http://localhost:8080',
    
    // 发送间隔（毫秒）
    sendInterval: 500,
    
    // 每次发送的采样点数
    samplesPerPacket: 1000,
    
    // 采样率
    sampleRate: 1000000,
    
    // 是否循环播放
    loop: true
};

// 可用的TDMS文件列表（模拟）
const tdmsFiles = [
    'data1.tdms',
    'data-10-left-1.tdms',
    'data-10-right-1.tdms',
    'data-15-left-1.tdms',
    'data-15-right-1.tdms',
    'data-20-left-1.tdms',
    'data-20-right-1.tdms',
    'data2023-left-2.tdms',
    'data2023-right-1.tdms'
];

// 当前选择的文件索引
let currentFileIndex = 0;
let currentSampleIndex = 0;
let isRunning = false;

// 由于Node.js没有直接读取TDMS的库，我们创建模拟数据
// 实际应用中，可以：
// 1. 使用Python脚本预先将TDMS转换为JSON
// 2. 通过子进程调用Python读取TDMS
// 3. 使用二进制格式直接解析TDMS文件

console.log('========================================');
console.log('  TDMS Data Simulator for Backend');
console.log('========================================');
console.log('');
console.log('[配置]');
console.log(`数据目录: ${config.dataDir}`);
console.log(`Backend URL: ${config.backendUrl}`);
console.log(`发送间隔: ${config.sendInterval}ms`);
console.log(`采样点数: ${config.samplesPerPacket}`);
console.log(`采样率: ${config.sampleRate} Hz`);
console.log('');

// 检查数据目录是否存在
if (!fs.existsSync(config.dataDir)) {
    console.error(`[ERROR] 数据目录不存在: ${config.dataDir}`);
    process.exit(1);
}

console.log('[可用的TDMS文件]');
tdmsFiles.forEach((file, index) => {
    const filePath = path.join(config.dataDir, file);
    if (fs.existsSync(filePath)) {
        const stats = fs.statSync(filePath);
        console.log(`${index + 1}. ${file} (${(stats.size / 1024 / 1024).toFixed(2)} MB)`);
    }
});
console.log('');

// 模拟从TDMS文件生成声发射信号数据
function generateAESignalFromFile(fileName, startIndex, count) {
    // 基于文件名生成不同特征的信号
    const fileHash = fileName.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
    const baseFreq = 100000 + (fileHash % 50000); // 100kHz - 150kHz
    const amplitude = 0.5 + (fileHash % 100) / 200; // 0.5 - 1.0
    
    const samples = [];
    const time = Date.now() / 1000;
    
    for (let i = 0; i < count; i++) {
        const t = (startIndex + i) / config.sampleRate;
        
        // 模拟典型的声发射信号：衰减正弦波 + 噪声
        const burst = Math.exp(-t * 50) * Math.sin(2 * Math.PI * baseFreq * t);
        const noise = (Math.random() - 0.5) * 0.1;
        const background = Math.sin(2 * Math.PI * 1000 * t) * 0.05; // 背景振动
        
        samples.push(amplitude * burst + noise + background);
    }
    
    return samples;
}

// 发送数据到Backend服务
async function sendDataToBackend(data) {
    try {
        // Backend服务期望的数据格式（根据实际API调整）
        const payload = {
            deviceId: `tdms-simulator-${currentFileIndex}`,
            deviceName: `TDMS文件: ${tdmsFiles[currentFileIndex]}`,
            timestamp: Date.now(),
            sampleRate: config.sampleRate,
            samples: data.samples,
            location: data.location || 'simulation',
            metadata: {
                fileName: data.fileName,
                sampleIndex: data.sampleIndex,
                totalSamples: data.samples.length,
                source: 'tdms-simulator'
            }
        };
        
        // 发送到Backend的数据接收端点（需要根据实际API调整）
        // 如果Backend没有数据接收API，可以通过Kafka发送
        const response = await axios.post(`${config.backendUrl}/api/data/receive`, payload, {
            timeout: 5000,
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        console.log(`[√] 发送成功: ${data.fileName}, 样本 ${data.sampleIndex}-${data.sampleIndex + data.samples.length}, 响应: ${response.status}`);
        return true;
        
    } catch (error) {
        if (error.code === 'ECONNREFUSED') {
            console.error(`[×] Backend服务未运行 (${config.backendUrl})`);
        } else if (error.response) {
            console.error(`[×] Backend响应错误: ${error.response.status} - ${error.response.statusText}`);
        } else {
            console.error(`[×] 发送失败: ${error.message}`);
        }
        return false;
    }
}

// 主循环 - 模拟从TDMS文件读取并发送数据
async function startSimulation() {
    if (isRunning) {
        console.log('[WARN] 模拟器已在运行');
        return;
    }
    
    isRunning = true;
    console.log('[INFO] 开始模拟TDMS数据流...');
    console.log('[提示] 按 Ctrl+C 停止');
    console.log('');
    
    const interval = setInterval(async () => {
        const currentFile = tdmsFiles[currentFileIndex];
        
        // 生成模拟的声发射信号数据
        const samples = generateAESignalFromFile(currentFile, currentSampleIndex, config.samplesPerPacket);
        
        const dataPacket = {
            fileName: currentFile,
            sampleIndex: currentSampleIndex,
            samples: samples,
            location: currentFile.includes('left') ? 'left' : 'right'
        };
        
        // 发送数据
        const success = await sendDataToBackend(dataPacket);
        
        if (success) {
            currentSampleIndex += config.samplesPerPacket;
            
            // 每个文件假设有100万个采样点
            const maxSamplesPerFile = 1000000;
            
            if (currentSampleIndex >= maxSamplesPerFile) {
                // 切换到下一个文件
                currentSampleIndex = 0;
                currentFileIndex = (currentFileIndex + 1) % tdmsFiles.length;
                console.log(`[INFO] 切换到文件: ${tdmsFiles[currentFileIndex]}`);
            }
        }
        
    }, config.sendInterval);
    
    // 保存interval引用以便后续清理
    process.on('SIGINT', () => {
        clearInterval(interval);
        isRunning = false;
        console.log('');
        console.log('[INFO] 模拟器已停止');
        process.exit(0);
    });
}

// 提供基于Kafka的替代方案
async function sendToKafka(data) {
    // 如果Backend通过Kafka接收数据，可以使用这个函数
    // 需要先安装 kafkajs: npm install kafkajs
    try {
        const { Kafka } = require('kafkajs');
        
        const kafka = new Kafka({
            clientId: 'tdms-simulator',
            brokers: ['localhost:9092']
        });
        
        const producer = kafka.producer();
        await producer.connect();
        
        await producer.send({
            topic: 'device-raw-data', // 或 'sample-input'
            messages: [{
                key: data.deviceId,
                value: JSON.stringify({
                    deviceId: data.deviceId,
                    timestamp: data.timestamp,
                    sampleRate: data.sampleRate,
                    samples: data.samples,
                    metadata: data.metadata
                })
            }]
        });
        
        await producer.disconnect();
        return true;
        
    } catch (error) {
        console.error('[×] Kafka发送失败:', error.message);
        return false;
    }
}

// 启动说明
console.log('[使用说明]');
console.log('');
console.log('方式1: 直接发送到Backend HTTP API');
console.log('  - 需要Backend提供数据接收API端点');
console.log('  - 默认端点: http://localhost:8080/api/data/receive');
console.log('');
console.log('方式2: 通过Kafka发送');
console.log('  - 需要Kafka运行在localhost:9092');
console.log('  - 发送到topic: device-raw-data');
console.log('  - 需要安装: npm install kafkajs');
console.log('');
console.log('[注意]');
console.log('1. 实际TDMS文件需要使用Python的npTDMS库解析');
console.log('2. 当前版本使用模拟数据（基于文件名生成特征）');
console.log('3. 可通过Python脚本预先将TDMS转换为JSON格式');
console.log('');

// 检查Backend服务是否可用
axios.get(`${config.backendUrl}/actuator/health`)
    .then(() => {
        console.log('[√] Backend服务运行正常');
        console.log('');
        console.log('[启动] 3秒后开始发送数据...');
        setTimeout(startSimulation, 3000);
    })
    .catch(() => {
        console.log('[!] Backend服务未响应，继续尝试发送...');
        console.log('');
        console.log('[启动] 3秒后开始发送数据...');
        setTimeout(startSimulation, 3000);
    });
