# FloatData作为Backend监控服务的模拟数据源

## 📋 概述

将`floatdata`目录中的TDMS声发射数据文件作为Backend滤波服务的模拟数据源，实现真实数据的实时监控演示。

---

## 🎯 目标

**现状**: Backend服务当前使用随机生成的模拟数据  
**目标**: 使用真实的TDMS声发射数据文件作为数据源

---

## 📂 数据文件说明

### TDMS文件位置
```
d:\CW_Cloud-main\floatdata\data\
```

### 可用文件
- `data1.tdms` (100 MB) - 通用声发射数据
- `data-10-left-*.tdms` - 10mm位置，左侧传感器
- `data-10-right-*.tdms` - 10mm位置，右侧传感器
- `data-15-left-*.tdms` - 15mm位置，左侧传感器
- `data-15-right-*.tdms` - 15mm位置，右侧传感器
- `data-20-left-*.tdms` - 20mm位置，左侧传感器
- `data-20-right-*.tdms` - 20mm位置，右侧传感器
- `data2023-*.tdms` - 2023年采集数据

**总文件数**: 约50个  
**总大小**: 约2.5 GB

---

## 🔧 实现方案

### 方案1: TDMS → JSON → Backend (推荐)

#### 步骤1: 转换TDMS为JSON

```bash
# 安装Python依赖
pip install npTDMS numpy

# 运行转换脚本
python tdms-to-json.py
```

**转换说明**:
- 每个TDMS文件转换为一个或多个JSON文件
- 每个JSON文件最多10万个采样点
- 输出目录: `floatdata\json\`

#### 步骤2: 启动TDMS模拟器

```batch
# 方式1: 使用批处理脚本
START-TDMS-SIMULATOR.bat

# 方式2: 直接运行Node.js脚本
npm install axios
node tdms-data-simulator.js
```

**模拟器功能**:
- 读取TDMS数据（当前为模拟生成）
- 每500ms发送一批数据（1000个采样点）
- 自动循环播放所有文件
- 发送到Backend的HTTP API

---

### 方案2: 通过Kafka发送数据

#### 架构
```
TDMS文件 → Python读取器 → Kafka → Backend → WebSocket → 前端
```

#### 实现

```python
# tdms-kafka-producer.py
from nptdms import TdmsFile
from kafka import KafkaProducer
import json

producer = KafkaProducer(
    bootstrap_servers=['localhost:9092'],
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)

# 读取TDMS并发送到Kafka
tdms_file = TdmsFile.read('floatdata/data/data1.tdms')
for group in tdms_file.groups():
    for channel in group.channels():
        data = channel[:]
        # 分批发送
        for i in range(0, len(data), 1000):
            batch = data[i:i+1000]
            message = {
                'deviceId': 'tdms-simulator',
                'timestamp': int(time.time() * 1000),
                'sampleRate': 1000000,
                'samples': batch.tolist()
            }
            producer.send('sample-input', value=message)
```

启动:
```bash
# 1. 启动Kafka
KAFKA-START.bat

# 2. 启动Backend
START-BACKEND.bat

# 3. 运行Python生产者
python tdms-kafka-producer.py
```

---

### 方案3: 修改Backend直接读取TDMS

**需要Backend源码支持**，在Backend Java项目中：

#### 添加依赖
```xml
<dependency>
    <groupId>io.github.ni</groupId>
    <artifactId>tdms</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### 实现数据读取器
```java
public class TdmsDataSource {
    public List<Double> readTdmsFile(String filePath) {
        // 读取TDMS文件
        TdmsFile tdmsFile = new TdmsFile(filePath);
        // 提取数据
        // ...
    }
}
```

---

## 🚀 快速开始

### 使用现有模拟器（当前方案）

```batch
# 1. 确保Backend运行
netstat -ano | findstr ":8080"

# 2. 启动TDMS模拟器
START-TDMS-SIMULATOR.bat

# 3. 查看实时监控
访问: http://localhost:3000
进入: 实时监控 → Backend滤波服务
```

**注意**: 当前版本使用基于文件名的模拟数据，不是真实TDMS数据。

---

### 使用真实TDMS数据

#### 选项A: 转换后使用

```bash
# 1. 转换TDMS为JSON
python tdms-to-json.py

# 2. 修改 tdms-data-simulator.js 读取JSON文件
#    (需要修改代码以读取真实JSON数据)

# 3. 启动模拟器
START-TDMS-SIMULATOR.bat
```

#### 选项B: 通过Kafka

```bash
# 1. 安装Python依赖
pip install npTDMS kafka-python

# 2. 启动Kafka
KAFKA-START.bat

# 3. 创建并运行Kafka生产者
python tdms-kafka-producer.py

# 4. Backend自动从Kafka接收数据
```

---

## 📊 数据流架构

### 当前架构
```
随机生成器 → Backend → WebSocket → 前端
```

### 目标架构
```
TDMS文件 → 模拟器 → Backend → WebSocket → 前端
                 ↓
               MySQL存储
```

### 完整架构（可选Kafka）
```
TDMS文件 → Python读取器 → Kafka → Backend → WebSocket → 前端
                                    ↓            ↓
                                  异常检测      MySQL
```

---

## 🔍 Backend API说明

### 当前需要确认的Backend API端点

Backend服务（端口8080）应该提供以下API：

#### 1. 数据接收端点
```
POST http://localhost:8080/api/data/receive
Content-Type: application/json

{
  "deviceId": "string",
  "timestamp": 1234567890,
  "sampleRate": 1000000,
  "samples": [0.1, 0.2, 0.3, ...],
  "metadata": {}
}
```

#### 2. 健康检查
```
GET http://localhost:8080/actuator/health
```

#### 3. 滤波结果查询
```
GET http://localhost:8080/api/filter/results?deviceId=xxx
```

**如果Backend没有这些API**，需要：
1. 检查Backend的实际API文档
2. 或使用Kafka作为中间件
3. 或修改Backend源码添加API

---

## 📝 配置说明

### tdms-data-simulator.js 配置

```javascript
const config = {
    // TDMS数据目录
    dataDir: 'd:\\CW_Cloud-main\\floatdata\\data',
    
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
```

### WebSocket Bridge 配置

WebSocket服务会自动接收Backend处理后的数据，无需额外配置。

---

## ✅ 验证步骤

### 1. 验证TDMS文件
```bash
cd d:\CW_Cloud-main\floatdata\data
dir *.tdms
```

### 2. 验证Backend运行
```bash
netstat -ano | findstr ":8080"
curl http://localhost:8080/actuator/health
```

### 3. 验证数据流
```bash
# 启动模拟器后，在浏览器开发者工具Console查看：
# WebSocket消息应该包含真实的声发射数据特征
```

### 4. 验证前端显示
```
访问: http://localhost:3000/realtime/filter
应该看到: 实时更新的信号波形
```

---

## 🐛 故障排查

### 问题1: Backend没有数据接收API
**解决**:
- 使用Kafka方案（Backend已支持Kafka）
- 修改Backend源码添加HTTP API
- 联系Backend开发者确认API文档

### 问题2: TDMS文件读取失败
**解决**:
```bash
# 检查Python环境
python --version

# 安装依赖
pip install npTDMS numpy

# 测试读取
python -c "from nptdms import TdmsFile; print('OK')"
```

### 问题3: 数据发送失败
**解决**:
```bash
# 检查Backend日志
# 查看Backend控制台窗口的错误信息

# 检查网络连接
curl -X POST http://localhost:8080/api/data/receive \
  -H "Content-Type: application/json" \
  -d '{"test": "data"}'
```

---

## 📖 相关文档

- `tdms-data-simulator.js` - TDMS数据模拟器脚本
- `tdms-to-json.py` - TDMS转JSON工具
- `START-TDMS-SIMULATOR.bat` - 启动脚本
- `websocket-bridge.js` - WebSocket桥接服务
- `实时监控-问题已解决.txt` - 实时监控配置说明

---

## 🎯 下一步计划

### Phase 1: 基础模拟 ✓
- [x] 创建TDMS模拟器框架
- [x] 配置Backend连接
- [x] 编写启动脚本

### Phase 2: 真实数据支持
- [ ] 完成TDMS到JSON转换
- [ ] 修改模拟器读取真实JSON数据
- [ ] 测试数据完整性

### Phase 3: 生产部署
- [ ] 优化数据发送性能
- [ ] 添加断点续传功能
- [ ] 实现多文件并行播放
- [ ] 添加数据质量监控

---

## 💡 最佳实践

1. **小批量测试**: 先转换1-2个小文件测试
2. **性能监控**: 关注CPU和内存使用
3. **错误处理**: 记录发送失败的数据包
4. **数据验证**: 定期检查前端显示是否正常
5. **备份数据**: 转换前备份原始TDMS文件

---

## 🆘 技术支持

如遇到问题：
1. 查看Backend控制台日志
2. 查看WebSocket Bridge日志
3. 检查浏览器开发者工具Console
4. 验证服务端口是否正常监听

---

**当前状态**: 框架已就绪，等待连接真实TDMS数据  
**推荐方案**: 先使用方案2（Kafka），因为Backend已支持Kafka输入
