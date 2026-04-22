// 卡尔曼滤波与异常检测处理器
const { Kafka } = require('kafkajs');
const mysql = require('mysql2/promise');

const kafka = new Kafka({
    clientId: 'kalman-processor',
    brokers: ['localhost:9092']
});

const consumer = kafka.consumer({ groupId: 'kalman-filter-group' });
const producer = kafka.producer();

// MySQL连接配置
const dbConfig = {
    host: 'localhost',
    port: 3306,
    user: 'root',
    password: '20041102',
    database: 'ruoyi-vue-pro'
};

let dbPool = null;

// 卡尔曼滤波器类
class KalmanFilter {
    constructor(processNoise = 0.01, measurementNoise = 0.1) {
        // 状态变量 [位置, 速度]
        this.x = [0, 0]; // 状态向量
        this.P = [[1, 0], [0, 1]]; // 协方差矩阵
        this.F = [[1, 1], [0, 1]]; // 状态转移矩阵
        this.H = [[1, 0]]; // 观测矩阵
        this.Q = [[processNoise, 0], [0, processNoise]]; // 过程噪声
        this.R = [[measurementNoise]]; // 测量噪声
        
        this.initialized = false;
        this.sequence = 0; // 序列号，保证时序
    }
    
    // 预测步骤
    predict() {
        // x = F * x
        const newX = [
            this.F[0][0] * this.x[0] + this.F[0][1] * this.x[1],
            this.F[1][0] * this.x[0] + this.F[1][1] * this.x[1]
        ];
        
        // P = F * P * F' + Q
        const FP = this.matrixMultiply(this.F, this.P);
        const FPFT = this.matrixMultiply(FP, this.transpose(this.F));
        this.P = this.matrixAdd(FPFT, this.Q);
        
        this.x = newX;
    }
    
    // 更新步骤
    update(measurement, timestamp, sequence) {
        if (!this.initialized) {
            this.x[0] = measurement;
            this.initialized = true;
            this.sequence = sequence;
            return {
                filtered: measurement,
                residual: 0,
                uncertainty: Math.sqrt(this.P[0][0]),
                sequence: sequence,
                timestamp: timestamp
            };
        }
        
        // 确保时序处理
        if (sequence <= this.sequence) {
            console.warn(`⚠️ 时序错误: 当前=${sequence}, 期望>${this.sequence}`);
            return null;
        }
        
        // y = z - H * x (残差)
        const predicted = this.H[0][0] * this.x[0] + this.H[0][1] * this.x[1];
        const residual = measurement - predicted;
        
        // S = H * P * H' + R (残差协方差)
        const HP = this.matrixMultiply(this.H, this.P);
        const HPHT = this.matrixMultiply(HP, this.transpose(this.H));
        const S = this.matrixAdd(HPHT, this.R);
        
        // K = P * H' * S^-1 (卡尔曼增益)
        const PHT = this.matrixMultiply(this.P, this.transpose(this.H));
        const K = this.matrixMultiply(PHT, this.matrixInverse(S));
        
        // x = x + K * y
        this.x[0] += K[0][0] * residual;
        this.x[1] += K[1][0] * residual;
        
        // P = (I - K * H) * P
        const KH = this.matrixMultiply(K, this.H);
        const I_KH = this.matrixSubtract([[1, 0], [0, 1]], KH);
        this.P = this.matrixMultiply(I_KH, this.P);
        
        this.sequence = sequence;
        
        return {
            filtered: this.x[0],
            velocity: this.x[1],
            residual: residual,
            uncertainty: Math.sqrt(this.P[0][0]),
            kalmanGain: K[0][0],
            sequence: sequence,
            timestamp: timestamp
        };
    }
    
    // 矩阵运算辅助函数
    matrixMultiply(A, B) {
        const rows = A.length;
        const cols = B[0].length;
        const inner = B.length;
        const result = Array(rows).fill().map(() => Array(cols).fill(0));
        
        for (let i = 0; i < rows; i++) {
            for (let j = 0; j < cols; j++) {
                for (let k = 0; k < inner; k++) {
                    result[i][j] += A[i][k] * B[k][j];
                }
            }
        }
        return result;
    }
    
    matrixAdd(A, B) {
        return A.map((row, i) => row.map((val, j) => val + B[i][j]));
    }
    
    matrixSubtract(A, B) {
        return A.map((row, i) => row.map((val, j) => val - B[i][j]));
    }
    
    transpose(matrix) {
        return matrix[0].map((_, i) => matrix.map(row => row[i]));
    }
    
    matrixInverse(matrix) {
        // 简化：假设是1x1矩阵
        if (matrix.length === 1 && matrix[0].length === 1) {
            return [[1 / matrix[0][0]]];
        }
        // 对于2x2矩阵的逆矩阵
        const det = matrix[0][0] * matrix[1][1] - matrix[0][1] * matrix[1][0];
        return [
            [matrix[1][1] / det, -matrix[0][1] / det],
            [-matrix[1][0] / det, matrix[0][0] / det]
        ];
    }
}

// 异常检测器
class AnomalyDetector {
    constructor() {
        this.window = []; // 滑动窗口
        this.windowSize = 50; // 窗口大小
        this.threshold = 3; // 异常阈值 (3-sigma)
        this.consecutiveCount = 0; // 连续异常计数
        this.consecutiveThreshold = 3; // 连续异常阈值
    }
    
    detect(filterResult) {
        if (!filterResult) return null;
        
        const { filtered, residual, uncertainty, timestamp, sequence } = filterResult;
        
        // 添加到滑动窗口
        this.window.push({ filtered, residual, timestamp, sequence });
        if (this.window.length > this.windowSize) {
            this.window.shift();
        }
        
        // 需要足够的历史数据
        if (this.window.length < 20) {
            return null;
        }
        
        // 计算残差的统计特性
        const residuals = this.window.map(w => Math.abs(w.residual));
        const mean = residuals.reduce((a, b) => a + b, 0) / residuals.length;
        const variance = residuals.reduce((a, b) => a + Math.pow(b - mean, 2), 0) / residuals.length;
        const std = Math.sqrt(variance);
        
        // 异常检测逻辑
        const currentResidual = Math.abs(residual);
        const zScore = (currentResidual - mean) / (std + 1e-10);
        
        let anomalyType = null;
        let anomalyScore = 0;
        let alertLevel = 'INFO';
        
        // 1. 残差异常 (卡尔曼滤波器拟合差)
        if (zScore > this.threshold) {
            anomalyType = 'RESIDUAL_ANOMALY';
            anomalyScore = Math.min(zScore / this.threshold, 1.0);
            this.consecutiveCount++;
        }
        // 2. 数值突变异常
        else if (this.window.length >= 2) {
            const prevValue = this.window[this.window.length - 2].filtered;
            const jump = Math.abs(filtered - prevValue);
            const avgJump = this.calculateAverageJump();
            
            if (jump > avgJump * 4) {
                anomalyType = 'SUDDEN_CHANGE';
                anomalyScore = Math.min(jump / (avgJump * 4), 1.0);
                this.consecutiveCount++;
            }
        }
        // 3. 不确定性异常
        else if (uncertainty > mean * 2) {
            anomalyType = 'UNCERTAINTY_HIGH';
            anomalyScore = Math.min(uncertainty / (mean * 2), 1.0);
            this.consecutiveCount++;
        }
        else {
            this.consecutiveCount = 0;
        }
        
        // 确定告警级别
        if (anomalyType) {
            if (this.consecutiveCount >= this.consecutiveThreshold) {
                alertLevel = 'ERROR';
            } else if (anomalyScore > 0.7) {
                alertLevel = 'WARN';
            } else {
                alertLevel = 'INFO';
            }
            
            return {
                type: anomalyType,
                score: anomalyScore,
                alertLevel: alertLevel,
                timestamp: timestamp,
                sequence: sequence,
                zScore: zScore,
                consecutiveCount: this.consecutiveCount,
                residual: residual,
                uncertainty: uncertainty,
                description: this.generateDescription(anomalyType, anomalyScore),
                detectionTime: Date.now()
            };
        }
        
        return null;
    }
    
    calculateAverageJump() {
        if (this.window.length < 2) return 1;
        
        const jumps = [];
        for (let i = 1; i < this.window.length; i++) {
            jumps.push(Math.abs(this.window[i].filtered - this.window[i-1].filtered));
        }
        
        return jumps.reduce((a, b) => a + b, 0) / jumps.length;
    }
    
    generateDescription(type, score) {
        const scorePercent = Math.round(score * 100);
        
        switch (type) {
            case 'RESIDUAL_ANOMALY':
                return `卡尔曼滤波器残差异常，置信度${scorePercent}%，信号偏离预测模型`;
            case 'SUDDEN_CHANGE':
                return `信号突变检测，置信度${scorePercent}%，数值发生剧烈跳变`;
            case 'UNCERTAINTY_HIGH':
                return `滤波不确定性过高，置信度${scorePercent}%，信号质量下降`;
            default:
                return `未知异常类型，置信度${scorePercent}%`;
        }
    }
}

// 设备管理器 - 支持多设备并行处理
class DeviceManager {
    constructor() {
        this.devices = new Map(); // deviceId -> {filter, detector, lastSequence}
    }
    
    getOrCreateDevice(deviceId) {
        if (!this.devices.has(deviceId)) {
            this.devices.set(deviceId, {
                filter: new KalmanFilter(0.01, 0.1),
                detector: new AnomalyDetector(),
                lastSequence: -1,
                processedCount: 0
            });
            console.log(`📱 创建新设备处理器: ${deviceId}`);
        }
        return this.devices.get(deviceId);
    }
    
    processSignal(deviceId, samples, timestamp, baseSequence) {
        const device = this.getOrCreateDevice(deviceId);
        const results = [];
        
        // 按时序处理每个采样点
        samples.forEach((sample, index) => {
            const sequence = baseSequence * 1000 + index; // 确保全局唯一序列
            
            // 时序检查
            if (sequence <= device.lastSequence) {
                console.warn(`⚠️ ${deviceId} 时序错误: ${sequence} <= ${device.lastSequence}`);
                return;
            }
            
            const sampleTimestamp = timestamp + index; // 每个采样点的时间戳
            
            // 卡尔曼滤波
            const filterResult = device.filter.update(sample, sampleTimestamp, sequence);
            if (!filterResult) return;
            
            // 异常检测
            const anomaly = device.detector.detect(filterResult);
            
            results.push({
                sequence: sequence,
                timestamp: sampleTimestamp,
                original: sample,
                filtered: filterResult.filtered,
                residual: filterResult.residual,
                uncertainty: filterResult.uncertainty,
                anomaly: anomaly
            });
            
            device.lastSequence = sequence;
            device.processedCount++;
        });
        
        return results;
    }
    
    getDeviceStats() {
        const stats = {};
        this.devices.forEach((device, deviceId) => {
            stats[deviceId] = {
                processedCount: device.processedCount,
                lastSequence: device.lastSequence,
                filterState: device.filter.x,
                uncertainty: Math.sqrt(device.filter.P[0][0])
            };
        });
        return stats;
    }
}

const deviceManager = new DeviceManager();

// 数据库操作
async function initDatabase() {
    try {
        dbPool = mysql.createPool(dbConfig);
        console.log('✅ 数据库连接池初始化成功');
    } catch (error) {
        console.error('❌ 数据库连接失败:', error.message);
    }
}

async function saveResults(deviceId, results) {
    if (!dbPool || results.length === 0) return;
    
    try {
        const connection = await dbPool.getConnection();
        
        // 保存滤波结果
        for (const result of results) {
            if (result.anomaly) {
                // 保存异常记录
                await connection.execute(`
                    INSERT INTO anomaly_detection_record (
                        device_id, device_name, anomaly_type, anomaly_score, alert_level,
                        threshold_value, detected_value, detection_algorithm, description,
                        context_data, creator, create_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FROM_UNIXTIME(?))
                `, [
                    deviceId,
                    `设备${deviceId}`,
                    result.anomaly.type,
                    result.anomaly.score,
                    result.anomaly.alertLevel,
                    3.0, // 阈值
                    result.residual,
                    'Kalman_Filter_3Sigma',
                    result.anomaly.description,
                    JSON.stringify({
                        sequence: result.sequence,
                        zScore: result.anomaly.zScore,
                        consecutiveCount: result.anomaly.consecutiveCount,
                        uncertainty: result.uncertainty
                    }),
                    'kalman-processor',
                    result.timestamp / 1000
                ]);
                
                console.log(`🚨 ${deviceId} 异常检测: ${result.anomaly.type} (${result.anomaly.alertLevel})`);
            }
        }
        
        connection.release();
    } catch (error) {
        console.error('❌ 数据库保存失败:', error.message);
    }
}

module.exports = {
    KalmanFilter,
    AnomalyDetector,
    DeviceManager,
    initDatabase,
    saveResults,
    deviceManager
};
