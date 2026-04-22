# 📦 Kafka 安装指南

## 🎯 概述

Kafka是backend.jar和floatdata系统的核心消息中间件，用于实时数据流传输。

---

## 🚀 快速安装（推荐）

### 方式1：自动安装脚本 ⭐⭐

**最简单的方式**

```bash
# 双击运行
install-kafka.bat

# 选择下载方式
1. 自动下载（推荐，需要网络）
2. 手动下载

# 等待安装完成（约5-10分钟）
```

安装完成后会自动：
- ✅ 下载Kafka 3.6.0
- ✅ 解压到kafka目录
- ✅ 配置Zookeeper和Kafka
- ✅ 创建启动/停止脚本

---

### 方式2：手动安装

#### 1. 下载Kafka

**下载地址**：
```
https://archive.apache.org/dist/kafka/3.6.0/kafka_2.13-3.6.0.tgz
```

或访问官网：https://kafka.apache.org/downloads

#### 2. 解压

将下载的文件解压到项目根目录：
```
e:\Code\CW_Cloud\kafka_2.13-3.6.0
```

#### 3. 重命名

将文件夹重命名为 `kafka`：
```
e:\Code\CW_Cloud\kafka
```

#### 4. 创建数据目录

```bash
mkdir kafka\data\zookeeper
mkdir kafka\data\kafka-logs
```

#### 5. 修改配置

**Zookeeper配置** (`kafka\config\zookeeper.properties`)：
```properties
dataDir=../data/zookeeper
clientPort=2181
maxClientCnxns=0
admin.enableServer=false
```

**Kafka配置** (`kafka\config\server.properties`)：
```properties
broker.id=0
listeners=PLAINTEXT://localhost:9092
advertised.listeners=PLAINTEXT://localhost:9092
log.dirs=../data/kafka-logs
zookeeper.connect=localhost:2181
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
```

---

## ▶️ 启动Kafka

### 使用启动脚本（推荐）

```bash
# 双击运行
start-kafka.bat
```

会自动启动：
1. Zookeeper（端口2181）
2. Kafka（端口9092）

### 手动启动

```bash
# 1. 启动Zookeeper
cd kafka
bin\windows\zookeeper-server-start.bat config\zookeeper.properties

# 2. 新开一个命令行窗口，启动Kafka
cd kafka
bin\windows\kafka-server-start.bat config\server.properties
```

---

## ⏸️ 停止Kafka

### 使用停止脚本（推荐）

```bash
# 双击运行
stop-kafka.bat
```

### 手动停止

```bash
# 1. 停止Kafka
cd kafka
bin\windows\kafka-server-stop.bat

# 2. 停止Zookeeper
bin\windows\zookeeper-server-stop.bat
```

---

## 🔧 创建必要的主题

backend.jar和floatdata需要以下Kafka主题：

```bash
cd kafka

# 1. 原始数据主题（设备发送）
bin\windows\kafka-topics.bat --create ^
  --topic device-raw-data ^
  --bootstrap-server localhost:9092 ^
  --partitions 3 ^
  --replication-factor 1

# 2. 滤波后数据主题（backend.jar输出）
bin\windows\kafka-topics.bat --create ^
  --topic device-filtered-data ^
  --bootstrap-server localhost:9092 ^
  --partitions 3 ^
  --replication-factor 1

# 3. 异常检测结果主题（floatdata输出）
bin\windows\kafka-topics.bat --create ^
  --topic anomaly-results ^
  --bootstrap-server localhost:9092 ^
  --partitions 1 ^
  --replication-factor 1
```

---

## ✅ 验证安装

### 1. 检查端口

```bash
# 双击运行
check-status.bat
```

应该显示：
```
[✅] Zookeeper (2181) 运行中
[✅] Kafka     (9092) 运行中
```

### 2. 查看主题列表

```bash
cd kafka
bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
```

应该看到创建的主题。

### 3. 测试消息发送和接收

**发送消息**：
```bash
cd kafka
bin\windows\kafka-console-producer.bat --topic test --bootstrap-server localhost:9092
```
输入消息，按回车发送。

**接收消息**（新开窗口）：
```bash
cd kafka
bin\windows\kafka-console-consumer.bat --topic test --from-beginning --bootstrap-server localhost:9092
```
应该能看到之前发送的消息。

按`Ctrl+C`退出。

---

## 📊 系统集成

### 在start-all.bat中的作用

Kafka在启动流程中的位置：

```
1. MySQL ✅
2. Redis ✅
3. Nacos ✅
4. Kafka ✅  ← 这里
5. CW_Cloud微服务 ✅
6. backend.jar ✅  ← 依赖Kafka
7. floatdata ✅  ← 依赖Kafka
8. 前端 ✅
```

### 数据流

```
设备采集
  ↓
device-raw-data (Kafka主题)
  ↓
backend.jar (实时滤波)
  ↓
device-filtered-data (Kafka主题)
  ↓
floatdata (异常检测)
  ↓
anomaly-results (Kafka主题)
  ↓
前端展示
```

---

## ⚠️ 常见问题

### 问题1：端口被占用

**错误**：`Address already in use`

**解决**：
```bash
# 检查端口占用
netstat -ano | findstr ":9092"
netstat -ano | findstr ":2181"

# 终止进程（记下PID后）
taskkill /F /PID <PID>
```

### 问题2：下载速度慢

**解决方案**：
1. 使用手动下载方式
2. 从国内镜像下载：
   - 清华镜像：https://mirrors.tuna.tsinghua.edu.cn/apache/kafka/
   - 阿里镜像：https://mirrors.aliyun.com/apache/kafka/

### 问题3：启动失败

**检查**：
1. Java是否已安装（`java -version`）
2. 端口是否被占用
3. 配置文件路径是否正确
4. 数据目录是否存在

### 问题4：主题创建失败

**原因**：Kafka未完全启动

**解决**：
1. 等待10-15秒后再创建主题
2. 检查Kafka是否正常运行

---

## 🔄 卸载Kafka

```bash
# 1. 停止服务
stop-kafka.bat

# 2. 删除目录
rmdir /s /q kafka

# 3. 删除启动脚本（可选）
del start-kafka.bat
del stop-kafka.bat
```

---

## 📚 进阶配置

### 调整内存

编辑 `kafka\bin\windows\kafka-server-start.bat`，修改：

```batch
set KAFKA_HEAP_OPTS=-Xmx1G -Xms1G
```

### 调整日志级别

编辑 `kafka\config\log4j.properties`：

```properties
log4j.rootLogger=INFO, stdout, kafkaAppender
```

### 配置持久化

数据已配置为持久化到 `kafka\data` 目录：
- Zookeeper数据：`kafka\data\zookeeper`
- Kafka日志：`kafka\data\kafka-logs`

---

## 🔗 相关资源

- **官方文档**：https://kafka.apache.org/documentation/
- **快速开始**：https://kafka.apache.org/quickstart
- **配置参考**：https://kafka.apache.org/documentation/#configuration
- **中文教程**：https://kafka.apachecn.org/

---

## ✅ 安装后检查清单

- [ ] Kafka目录存在（`e:\Code\CW_Cloud\kafka`）
- [ ] 启动脚本已创建（`start-kafka.bat`）
- [ ] 停止脚本已创建（`stop-kafka.bat`）
- [ ] Kafka能成功启动
- [ ] Zookeeper端口2181可访问
- [ ] Kafka端口9092可访问
- [ ] 主题创建成功
- [ ] 测试消息发送/接收成功
- [ ] `check-status.bat`显示Kafka运行中

---

**创建时间**：2025-11-24  
**Kafka版本**：3.6.0  
**适用系统**：Windows 10/11
