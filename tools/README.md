# 工具目录

本目录包含各种开发和测试工具。

---

## 🛠️ 工具清单

### Kafka测试
**kafka-test-suite.py** - Kafka完整测试套件
```bash
python tools/kafka-test-suite.py
```
功能：
- 测试Kafka连接
- 测试消息发送
- 测试消息消费
- 综合测试报告

### TDMS数据处理
**tdms-to-json.py** - TDMS文件转JSON工具
```bash
python tools/tdms-to-json.py <tdms_file>
```
功能：
- 读取TDMS文件
- 转换为JSON格式
- 输出元数据和样本数据

### 数据模拟
**tdms-data-simulator.js** - TDMS数据模拟器  
```bash
node tools/tdms-data-simulator.js
```
功能：
- 模拟TDMS数据生成
- 用于WebSocket测试
- 支持动态速率

---

## 📖 使用示例

### 测试Kafka是否正常
```bash
cd d:\CW_Cloud-main
python tools/kafka-test-suite.py
```

### 转换TDMS文件
```bash
python tools/tdms-to-json.py floatdata/data/data-10-left-1.tdms
```

---

## 🔧 依赖要求

### Python工具
```bash
pip install kafka-python nptdms
```

### Node.js工具
```bash
npm install ws kafkajs
```

---

更新日期: 2025-12-09
