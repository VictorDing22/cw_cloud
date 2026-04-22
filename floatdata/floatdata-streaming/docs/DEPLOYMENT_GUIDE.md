# 完整部署指南

## 目录

1. [系统架构](#系统架构)
2. [环境准备](#环境准备)
3. [本地开发环境部署](#本地开发环境部署)
4. [Docker 容器化部署](#docker-容器化部署)
5. [分布式集群部署](#分布式集群部署)
6. [性能优化](#性能优化)
7. [故障排查](#故障排查)

## 系统架构

### 逻辑架构

```
┌─────────────────┐
│  样机采集端      │ (Netty Client)
│  - 信号生成      │
│  - 二进制编码    │
└────────┬────────┘
         │ TCP
         ▼
┌─────────────────┐
│  Netty Server   │ (接收 & 缓冲)
│  - 连接管理      │
│  - 数据解析      │
└────────┬────────┘
         │ JSON
         ▼
┌─────────────────┐
│  Kafka Broker   │ (消息队列)
│  - 主题分区      │
│  - 消息持久化    │
└────────┬────────┘
         │ 消息流
         ▼
┌─────────────────┐
│ Spark Streaming │ (流处理)
│  - 时间窗口      │
│  - 数据分段      │
│  - 并行处理      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 异常检测处理器   │ (多进程)
│  - Butterworth  │
│  - FFT 分析      │
│  - 阈值判断      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  结果存储        │
│  - Kafka 结果主题 │
│  - 数据库        │
│  - 文件系统      │
└─────────────────┘
```

### 物理架构（分布式）

```
┌──────────────────────────────────────────────────────────────┐
│                     Master Node (控制节点)                    │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Spark Master (7077)                                    │ │
│  │  Kafka Broker 1 (9092)                                  │ │
│  │  Zookeeper (2181)                                       │ │
│  │  MySQL (3306)                                           │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│  Worker Node 1   │  │  Worker Node 2   │  │  Worker Node 3   │
│  ┌────────────┐  │  │  ┌────────────┐  │  │  ┌────────────┐  │
│  │ Spark      │  │  │  │ Spark      │  │  │  │ Spark      │  │
│  │ Executor   │  │  │  │ Executor   │  │  │  │ Executor   │  │
│  │ (2 cores)  │  │  │  │ (2 cores)  │  │  │  │ (2 cores)  │  │
│  └────────────┘  │  │  └────────────┘  │  │  └────────────┘  │
│  ┌────────────┐  │  │  ┌────────────┐  │  │  ┌────────────┐  │
│  │ Netty      │  │  │  │ Netty      │  │  │  │ Netty      │  │
│  │ Server     │  │  │  │ Server     │  │  │  │ Server     │  │
│  │ (9090)     │  │  │  │ (9090)     │  │  │  │ (9090)     │  │
│  └────────────┘  │  │  └────────────┘  │  │  └────────────┘  │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

## 环境准备

### 最低要求

| 组件 | 版本 | 备注 |
|------|------|------|
| Java | 11+ | OpenJDK 或 Oracle JDK |
| Maven | 3.6+ | 构建工具 |
| Kafka | 3.0+ | 消息队列 |
| Spark | 3.4+ | 流处理框架 |
| Python | 3.8+ | 可选，用于测试脚本 |

### 推荐配置

**单机开发环境：**
- CPU: 4 核
- 内存: 8GB
- 磁盘: 50GB

**集群生产环境：**
- Master: 8 核 CPU, 16GB 内存
- Worker: 4 核 CPU, 8GB 内存 (×3)
- 网络: 千兆网络

### 安装步骤

#### 1. 安装 Java

```bash
# Windows
# 下载 JDK 11+: https://www.oracle.com/java/technologies/downloads/
# 设置环境变量 JAVA_HOME

# Linux
sudo apt-get install openjdk-11-jdk

# 验证
java -version
```

#### 2. 安装 Maven

```bash
# Windows
# 下载: https://maven.apache.org/download.cgi
# 设置环境变量 MAVEN_HOME

# Linux
sudo apt-get install maven

# 验证
mvn -version
```

#### 3. 安装 Kafka

```bash
# 下载
wget https://archive.apache.org/dist/kafka/3.6.0/kafka_2.13-3.6.0.tgz
tar -xzf kafka_2.13-3.6.0.tgz
mv kafka_2.13-3.6.0 /opt/kafka

# 设置环境变量
export KAFKA_HOME=/opt/kafka
export PATH=$PATH:$KAFKA_HOME/bin
```

#### 4. 安装 Spark

```bash
# 下载
wget https://archive.apache.org/dist/spark/spark-3.4.1/spark-3.4.1-bin-hadoop3.tgz
tar -xzf spark-3.4.1-bin-hadoop3.tgz
mv spark-3.4.1-bin-hadoop3 /opt/spark

# 设置环境变量
export SPARK_HOME=/opt/spark
export PATH=$PATH:$SPARK_HOME/bin
```

## 本地开发环境部署

### 快速启动（推荐）

#### 方式一：使用启动脚本

```bash
# Windows
start-all.bat

# Linux/Mac
chmod +x start-all.sh
./start-all.sh
```

#### 方式二：手动启动

**步骤 1：启动 Kafka**

```bash
# 启动 Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties

# 新开终端，启动 Kafka Broker
bin/kafka-server-start.sh config/server.properties

# 创建主题
bin/kafka-topics.sh --create --topic acoustic-emission-signal \
  --bootstrap-server localhost:9092 --partitions 4 --replication-factor 1

bin/kafka-topics.sh --create --topic anomaly-detection-result \
  --bootstrap-server localhost:9092 --partitions 4 --replication-factor 1
```

**步骤 2：编译项目**

```bash
cd floatdata-streaming
mvn clean package -DskipTests
```

**步骤 3：启动 Netty 服务器**

```bash
# 新开终端
java -cp target/floatdata-streaming-1.0.0.jar \
  com.floatdata.server.NettyServer
```

**步骤 4：启动 Spark 处理器**

```bash
# 新开终端
java -cp target/floatdata-streaming-1.0.0.jar \
  com.floatdata.spark.StreamProcessor
```

**步骤 5：启动采集客户端**

```bash
# 新开终端
java -cp target/floatdata-streaming-1.0.0.jar \
  com.floatdata.client.AcousticEmissionClient localhost 9090
```

### 验证部署

```bash
# 监听输入主题
kafka-console-consumer.sh --topic acoustic-emission-signal \
  --bootstrap-server localhost:9092 --from-beginning

# 监听输出主题
kafka-console-consumer.sh --topic anomaly-detection-result \
  --bootstrap-server localhost:9092 --from-beginning
```

## Docker 容器化部署

### 前置条件

```bash
# 安装 Docker
# https://docs.docker.com/get-docker/

# 安装 Docker Compose
# https://docs.docker.com/compose/install/
```

### 启动容器

```bash
# 进入项目目录
cd floatdata-streaming

# 编译项目
mvn clean package -DskipTests

# 启动所有容器
docker-compose up -d

# 查看容器状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 容器管理

```bash
# 停止容器
docker-compose down

# 重启容器
docker-compose restart

# 查看特定服务日志
docker-compose logs -f kafka
docker-compose logs -f spark-master

# 进入容器
docker-compose exec kafka bash
```

### 访问 Web UI

- Kafka UI: http://localhost:8080
- Spark Master: http://localhost:8888

## 分布式集群部署

### 集群规划

假设有 3 个节点：

| 节点 | IP | 角色 | 服务 |
|------|-----|------|------|
| Node1 | 192.168.1.10 | Master | Spark Master, Kafka Broker, Zookeeper |
| Node2 | 192.168.1.11 | Worker | Spark Worker, Netty Server |
| Node3 | 192.168.1.12 | Worker | Spark Worker, Netty Server |

### 部署步骤

#### 1. 在所有节点安装依赖

```bash
# 在每个节点上执行
sudo apt-get update
sudo apt-get install -y openjdk-11-jdk maven git

# 设置 JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
```

#### 2. 在 Master 节点配置 Kafka

```bash
# 编辑 config/server.properties
broker.id=1
listeners=PLAINTEXT://192.168.1.10:9092
advertised.listeners=PLAINTEXT://192.168.1.10:9092
zookeeper.connect=192.168.1.10:2181

# 启动
bin/kafka-server-start.sh config/server.properties &
```

#### 3. 在 Master 节点配置 Spark Master

```bash
# 编辑 conf/spark-env.sh
export SPARK_MASTER_HOST=192.168.1.10
export SPARK_MASTER_PORT=7077
export SPARK_MASTER_WEBUI_PORT=8080

# 启动
sbin/start-master.sh
```

#### 4. 在 Worker 节点配置 Spark Worker

```bash
# 编辑 conf/spark-env.sh
export SPARK_WORKER_CORES=4
export SPARK_WORKER_MEMORY=8g

# 启动
sbin/start-worker.sh spark://192.168.1.10:7077
```

#### 5. 部署应用

```bash
# 在 Master 节点上
cd floatdata-streaming
mvn clean package -DskipTests

# 使用 spark-submit 提交任务
spark-submit --class com.floatdata.spark.StreamProcessor \
  --master spark://192.168.1.10:7077 \
  --executor-memory 4g \
  --executor-cores 4 \
  --num-executors 2 \
  target/floatdata-streaming-1.0.0.jar

# 在 Worker 节点上启动 Netty 服务器
java -cp target/floatdata-streaming-1.0.0.jar \
  com.floatdata.server.NettyServer
```

#### 6. 负载均衡（可选）

```bash
# 使用 HAProxy 进行负载均衡
# 编辑 /etc/haproxy/haproxy.cfg

global
    maxconn 4096

defaults
    mode tcp
    timeout connect 5000ms
    timeout client 50000ms
    timeout server 50000ms

frontend netty_lb
    bind 0.0.0.0:9090
    default_backend netty_servers

backend netty_servers
    balance roundrobin
    server server1 192.168.1.11:9090 check
    server server2 192.168.1.12:9090 check
```

## 性能优化

### 1. Netty 优化

```properties
# application.properties
netty.server.threads=16
netty.server.backlog=1024
```

```java
// SignalHandler.java 中添加
bootstrap.option(ChannelOption.SO_RCVBUF, 65536)
         .option(ChannelOption.SO_SNDBUF, 65536)
         .childOption(ChannelOption.SO_RCVBUF, 65536)
         .childOption(ChannelOption.SO_SNDBUF, 65536);
```

### 2. Kafka 优化

```properties
# server.properties
num.network.threads=8
num.io.threads=8
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600
```

### 3. Spark 优化

```bash
spark-submit \
  --executor-memory 4g \
  --executor-cores 8 \
  --num-executors 4 \
  --conf spark.shuffle.partitions=200 \
  --conf spark.default.parallelism=200 \
  --conf spark.streaming.kafka.maxRatePerPartition=10000 \
  target/floatdata-streaming-1.0.0.jar
```

### 4. 信号处理优化

```java
// SignalFilter.java
// 使用更高效的 FFT 实现
// 减少滤波器阶数
// 使用缓存避免重复计算
```

## 故障排查

### 常见问题

#### 问题 1：Kafka 连接失败

```
错误: java.net.ConnectException: Connection refused
```

**解决方案：**

```bash
# 检查 Kafka 是否运行
jps | grep Kafka

# 检查端口是否开放
netstat -an | grep 9092

# 检查 bootstrap.servers 配置
cat application.properties | grep kafka.bootstrap.servers

# 重启 Kafka
bin/kafka-server-stop.sh
sleep 2
bin/kafka-server-start.sh config/server.properties
```

#### 问题 2：Spark 任务超时

```
错误: Task took too long to run
```

**解决方案：**

```properties
# application.properties
spark.streaming.kafka.maxRatePerPartition=5000
spark.streaming.backpressure.enabled=true
spark.streaming.backpressure.initialRate=1000
```

#### 问题 3：内存溢出

```
错误: java.lang.OutOfMemoryError: Java heap space
```

**解决方案：**

```bash
# 增加 JVM 堆内存
java -Xmx4g -Xms2g -cp target/floatdata-streaming-1.0.0.jar \
  com.floatdata.server.NettyServer

# 或在 spark-submit 中
spark-submit --executor-memory 8g ...
```

#### 问题 4：Netty 连接数过多

```
错误: Too many open files
```

**解决方案：**

```bash
# 增加文件描述符限制
ulimit -n 65536

# 或编辑 /etc/security/limits.conf
* soft nofile 65536
* hard nofile 65536
```

### 日志分析

```bash
# 查看 Netty 服务器日志
tail -f logs/netty-server.log | grep -i error

# 查看 Spark 处理日志
tail -f logs/spark-processor.log | grep -i exception

# 查看 Kafka 日志
tail -f logs/server.log | grep -i error

# 搜索特定错误
grep -r "OutOfMemory" logs/
grep -r "Connection refused" logs/
```

### 性能监控

```bash
# 监控 Netty 连接数
netstat -an | grep 9090 | wc -l

# 监控 Kafka 消息队列
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group acoustic-emission-group --describe

# 监控 Spark 任务
# 访问 http://localhost:4040

# 监控系统资源
top
free -h
df -h
```

## 总结

| 部署方式 | 适用场景 | 优点 | 缺点 |
|---------|---------|------|------|
| 本地开发 | 开发测试 | 简单快速 | 性能有限 |
| Docker | 小规模部署 | 环境一致 | 资源开销 |
| 集群 | 生产环境 | 高可用 | 复杂度高 |

选择合适的部署方式，根据实际需求进行性能调优和监控。
