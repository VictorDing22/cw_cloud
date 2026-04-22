/**
 * 统一信号分析API
 * 提供统一的数据格式，支持实时和历史数据源
 */

const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const app = express();
const PORT = 3003;  // 使用新端口避免冲突

app.use(cors());
app.use(express.json());

/**
 * 统一的信号数据格式
 */
interface UnifiedSignalData {
  type: 'signal-data',
  deviceId: string,           // 'DEVICE_001' 或 'signal-1'
  source: 'realtime' | 'history',
  originalSamples: number[],  // 原始/干净信号
  noisySamples: number[],     // 加噪信号
  filteredSamples: number[], // 滤波后信号
  currentError: number,
  statistics: {
    mseImprovement: number,
    mseBefore: number,
    mseAfter: number,
    correlationBefore: number,
    correlationAfter: number,
    snrImprovement?: number
  },
  parameters: {
    sampleRate: number,
    totalSamples: number
  }
}

/**
 * 统一的信号分析端点
 * 同时支持实时和历史数据
 */
app.post('/api/signal/analyze', async (req, res) => {
  try {
    const { source, dataSource, sampleRate = 100000, cutoffFreq, filterOrder = 6 } = req.body;
    
    if (source === 'history') {
      // 历史文件模式
      return await analyzeHistoryData(req, res);
    } else if (source === 'realtime') {
      // 实时数据模式（转发到WebSocket或处理上传的数据）
      return await analyzeRealtimeData(req, res);
    } else {
      return res.status(400).json({ error: '未知的数据源类型' });
    }
    
  } catch (error) {
    console.error('分析失败:', error);
    res.status(500).json({ error: '分析失败', details: error.message });
  }
});

/**
 * 分析历史TDMS文件
 */
async function analyzeHistoryData(req, res) {
  const { dataSource, sampleRate = 100000, cutoffFreq = 5500, filterOrder = 6 } = req.body;
  
  if (!dataSource) {
    return res.status(400).json({ error: '缺少dataSource参数' });
  }
  
  console.log(`[历史数据] 分析: ${dataSource}, cutoff: ${cutoffFreq}Hz`);
  
  // 调用Python脚本
  const python = spawn('python', [
    path.join(__dirname, 'floatdata/floatdata-streaming/tdms-folder-analyzer.py'),
    dataSource,
    sampleRate.toString(),
    cutoffFreq.toString(),
    filterOrder.toString()
  ], {
    maxBuffer: 10 * 1024 * 1024
  });
  
  let output = '';
  let errorOutput = '';
  
  python.stdout.on('data', (data) => {
    output += data.toString();
  });
  
  python.stderr.on('data', (data) => {
    errorOutput += data.toString();
  });
  
  python.on('close', (code) => {
    if (code === 0) {
      try {
        const tdmsResult = JSON.parse(output);
        
        // 转换为统一格式
        const unifiedData = {
          type: 'signal-data',
          deviceId: dataSource,
          source: 'history',
          originalSamples: tdmsResult.signals.sine || [],
          noisySamples: tdmsResult.signals.noisy || [],
          filteredSamples: tdmsResult.signals.filtered || [],
          currentError: tdmsResult.metrics.mseAfter,
          statistics: {
            mseImprovement: tdmsResult.metrics.mseImprovement,
            mseBefore: tdmsResult.metrics.mseBefore,
            mseAfter: tdmsResult.metrics.mseAfter,
            correlationBefore: tdmsResult.metrics.correlationBefore,
            correlationAfter: tdmsResult.metrics.correlation,
            snrImprovement: calculateSNRImprovement(tdmsResult.metrics)
          },
          parameters: {
            sampleRate: tdmsResult.parameters.sampleRate,
            totalSamples: tdmsResult.parameters.totalSamples
          },
          anomalies: []  // 历史数据不做异常检测
        };
        
        console.log(`[历史数据] 成功: MSE改善 ${unifiedData.statistics.mseImprovement.toFixed(2)}%`);
        res.json(unifiedData);
        
      } catch (e) {
        console.error('[历史数据] JSON解析失败:', e);
        res.status(500).json({ error: '解析失败', details: e.message });
      }
    } else {
      console.error('[历史数据] Python错误:', errorOutput);
      res.status(500).json({ error: 'Python脚本执行失败', details: errorOutput });
    }
  });
}

/**
 * 计算SNR改善
 */
function calculateSNRImprovement(metrics) {
  if (metrics.mseBefore && metrics.mseAfter && metrics.mseBefore > 0) {
    return 10 * Math.log10(metrics.mseBefore / metrics.mseAfter);
  }
  return 0;
}

/**
 * 健康检查
 */
app.get('/api/health', (req, res) => {
  res.json({
    status: 'ok',
    service: 'Unified Signal Analysis API',
    port: PORT,
    endpoints: [
      '/api/signal/analyze (POST)'
    ]
  });
});

app.listen(PORT, () => {
  console.log(`===========================================`);
  console.log(`  统一信号分析API已启动`);
  console.log(`  端口: ${PORT}`);
  console.log(`  支持: 实时数据 + 历史文件`);
  console.log(`  数据格式: 完全统一`);
  console.log(`===========================================`);
});

module.exports = app;
