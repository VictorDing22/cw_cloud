# yudao-module-detection 技术文档

## 1. 文档概述与目标

### 1.1 模块简介

`yudao-module-detection` 是芋道云原生微服务架构中的核心检测模块，专注于工业物联网场景下的实时数据检测与分析。该模块的核心业务价值在于：**通过 gRPC 流式接口接收 TDMS（Technical Data Management Streaming）格式的工业数据文件，利用 Apache Flink 流处理引擎和多种滤波算法，对数据进行实时滤波处理和异常检测，并将结果持久化到时序数据库 TDengine，同时通过 WebSocket 实时推送给前端展示**。

该模块解决了工业场景中高频数据流的实时处理难题，支持高达数百 MB/s 的数据吞吐量，能够快速识别设备异常，为设备健康监测和预测性维护提供技术支撑。

### 1.2 主要目标

本文档的主要目标是：**将此模块的整套实现流程、技术选型和架构设计清晰地呈现出来**，使任何一位开发者都能：
- 理解模块的整体架构和各组件的职责边界
- 掌握从数据接收到结果输出的完整业务流程
- 了解每个技术组件在流程中的具体作用和解决的问题
- 能够基于现有架构进行功能扩展和性能优化

---

## 2. 总体架构设计

### 2.1 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        客户端层                                  │
│  (gRPC Client / HTTP Client / WebSocket Client)                │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    yudao-module-detection-server                │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │  gRPC Server     │  │  HTTP Controller │  │  Netty Server│  │
│  │  (Port 9090)     │  │  (Port 48083)    │  │  (Port 9999) │  │
│  └────────┬─────────┘  └────────┬─────────┘  └──────┬───────┘  │
│           │                      │                    │          │
│           ▼                      ▼                    ▼          │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         RealTimeDetectionServiceImpl                     │  │
│  │  (gRPC Stream Handler: UploadAndDetect)                  │  │
│  └────────┬─────────────────────────────────────────────────┘  │
│           │                                                      │
│           ▼                                                      │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         FastTdmsParser                                    │  │
│  │  (Python Process: nptdms → JSON + Binary)                │  │
│  └────────┬─────────────────────────────────────────────────┘  │
│           │                                                      │
│           ▼                                                      │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         Apache Flink Local Environment                   │  │
│  │  ┌────────────────────────────────────────────────────┐  │  │
│  │  │  DataStream<TdmsSample>                            │  │  │
│  │  │         │                                           │  │  │
│  │  │         ▼                                           │  │  │
│  │  │  GenericFilterProcessFunction                       │  │  │
│  │  │  ├─ FilterStrategy (20种算法)                      │  │  │
│  │  │  ├─ Window State (滑动窗口统计)                     │  │  │
│  │  │  └─ Anomaly Detection (能量阈值)                    │  │  │
│  │  │         │                                           │  │  │
│  │  │         ▼                                           │  │  │
│  │  │  DataStream<FilterResult>                           │  │  │
│  │  └────────────────────────────────────────────────────┘  │  │
│  └────────┬───────────────────────────────────────────────────┘  │
│           │                                                      │
│           ├──────────────────┬──────────────────┐                │
│           ▼                  ▼                  ▼                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐    │
│  │  gRPC        │  │  WebSocket    │  │  (可选) TDengine │    │
│  │  Response    │  │  Broadcast    │  │  Sink            │    │
│  └──────────────┘  └──────────────┘  └──────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    外部服务层                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │  TDengine    │  │  Nacos       │  │  Python      │        │
│  │  (时序数据库) │  │  (注册中心)   │  │  (nptdms)    │        │
│  └──────────────┘  └──────────────┘  └──────────────┘        │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 架构说明

#### 2.2.1 模块定位与职责边界

`yudao-module-detection` 在整个 yudao 系统中的定位是：
- **数据检测与分析服务**：作为独立的微服务模块，通过 Nacos 注册到服务注册中心，对外提供 gRPC 和 HTTP 两种接口
- **实时流处理引擎**：基于 Flink 的流处理能力，对工业数据进行实时滤波和异常检测
- **多协议支持**：同时支持 gRPC 流式上传、HTTP 文件上传、Netty 二进制数据接收等多种数据接入方式

职责边界：
- **负责**：TDMS 文件解析、实时滤波处理、异常检测、结果推送
- **不负责**：设备管理、用户权限管理（由 `yudao-module-system` 负责）、数据可视化（由前端负责）

#### 2.2.2 核心设计模式

该模块采用了以下设计模式：

1. **MVC 分层架构**
   - **Controller 层**：`DetectionController` 处理 HTTP 请求，提供任务管理和状态查询接口
   - **Service 层**：`RealTimeDetectionServiceImpl` 实现 gRPC 服务接口，处理核心业务逻辑
   - **Logic 层**：`GenericFilterProcessFunction` 封装 Flink 处理逻辑，`FilterFactory` 实现策略模式

2. **策略模式（Strategy Pattern）**
   - `FilterStrategy` 接口定义了滤波算法的统一接口
   - `FilterFactory` 根据 `FilterAlgorithm` 枚举创建对应的策略实现
   - 支持 20 种滤波算法（卡尔曼、LMS、NLMS、RLS、均值、中值、高斯、巴特沃斯、切比雪夫、FIR、IIR、维纳、小波、形态学、双边、SG平滑、粒子滤波、EKF、UKF、自适应陷波）

3. **工厂模式（Factory Pattern）**
   - `FilterFactory.create()` 方法根据算法类型创建对应的滤波器实例

4. **观察者模式（Observer Pattern）**
   - WebSocket 采用观察者模式，多个客户端订阅检测结果，服务端广播推送

#### 2.2.3 模块内外部服务交互方式

1. **gRPC 服务调用**
   - 客户端通过 gRPC 流式接口 `UploadAndDetect` 上传 TDMS 文件块
   - 服务端使用 `StreamObserver<FileChunk>` 接收流式数据，返回 `DetectionResult`

2. **HTTP REST API**
   - `DetectionController` 提供 RESTful 接口：
     - `POST /admin-api/detection/realtime/upload`：上传文件
     - `GET /admin-api/detection/realtime/task-status`：查询任务状态
     - `POST /admin-api/detection/realtime/push`：推送检测结果（供 Flink 调用）

3. **WebSocket 实时推送**
   - `DetectionWebSocketHandler` 维护客户端连接池
   - 通过 `broadcast()` 方法向所有连接的客户端推送 `FilterResult`

4. **Netty 二进制数据接收**
   - `NettyServer` 监听端口 9999，接收多通道实时数据
   - `NettyServerHandler` 解析二进制数据并放入阻塞队列，供 Flink 消费

5. **TDengine 数据持久化**
   - `TDengineSink` 使用 Flink JDBC Connector 批量写入检测结果
   - 采用超级表（STable）设计，按通道名称自动创建子表

6. **Python 进程调用**
   - `FastTdmsParser` 通过 `ProcessBuilder` 调用 Python 脚本解析 TDMS 文件
   - Python 脚本使用 `nptdms` 库读取文件，输出 JSON 元数据和二进制数据

---

## 3. 核心工作流程详解

### 3.1 流程概览

以一个典型的业务场景为例：**客户端通过 gRPC 上传一个 TDMS 文件，要求使用卡尔曼滤波算法进行实时检测分析**。

完整流程链路如下：

```
客户端上传文件 → gRPC Server 接收流式数据 → 临时文件存储 → Python 解析 TDMS 
→ Flink 流处理（滤波 + 异常检测）→ 结果收集 → gRPC 响应返回 → 临时文件清理
```

### 3.2 步骤拆解

#### 步骤 1：gRPC 流式数据接收

**入口类/方法**：`RealTimeDetectionServiceImpl.uploadAndDetect()`

**技术实现**：
- 使用 **gRPC 流式 API**（`StreamObserver<FileChunk>`）接收客户端发送的文件块
- 每个 `FileChunk` 包含：
  - `bytes content`：文件内容块
  - `string filename`：文件名（仅在第一个包发送）
  - `FilterAlgorithm algorithm`：滤波算法类型（仅在第一个包发送）

**代码示例**：
```java
@Override
public StreamObserver<FileChunk> uploadAndDetect(StreamObserver<DetectionResult> responseObserver) {
    return new StreamObserver<FileChunk>() {
        private File tempFile;
        private OutputStream outputStream;
        private String jobId;
        private FilterAlgorithm algorithm = FilterAlgorithm.LMS; // 默认算法

        @Override
        public void onNext(FileChunk chunk) {
            // 第一个包：初始化临时文件和算法参数
            if (tempFile == null) {
                jobId = UUID.randomUUID().toString();
                algorithm = chunk.getAlgorithm();
                tempFile = Files.createTempFile("grpc-detect-", chunk.getFilename()).toFile();
                outputStream = new FileOutputStream(tempFile);
            }
            // 写入文件块
            chunk.getContent().writeTo(outputStream);
        }
        // ... onError, onCompleted 处理
    };
}
```

**技术作用**：
- **gRPC 流式传输**解决了大文件上传的内存压力问题，支持边接收边处理
- **临时文件存储**避免了将整个文件加载到内存，适合处理 GB 级别的 TDMS 文件

---

#### 步骤 2：TDMS 文件解析

**入口类/方法**：`FastTdmsParser.parse(File tdmsFile)`

**技术实现**：
- 通过 **Java ProcessBuilder** 调用 Python 进程执行临时脚本
- Python 脚本使用 **nptdms** 库读取 TDMS 文件，提取通道元数据和采样数据
- 输出格式：
  - 第一行：JSON 格式的元数据（通道名、采样率、起始时间戳、样本数）
  - 后续：Big Endian 格式的 double 数组（二进制）

**代码示例**：
```java
public static FastParsedData parse(File tdmsFile) throws IOException {
    File scriptFile = createPythonScript();
    ProcessBuilder pb = new ProcessBuilder("python", "-X", "utf8",
            scriptFile.getAbsolutePath(), tdmsFile.getAbsolutePath());
    Process process = pb.start();
    
    // 1. 读取 JSON 元数据
    String metaJson = readLine(process.getInputStream());
    JsonNode meta = objectMapper.readTree(metaJson);
    
    // 2. 读取二进制 double 数组
    DataInputStream dis = new DataInputStream(process.getInputStream());
    for (int i = 0; i < count; i++) {
        double value = dis.readDouble(); // Big Endian
        long ts = startTimestamp + (long) (i * 1000.0 / sampleRate);
        samples.add(new TdmsSample(ts, value, channelName));
    }
    return result;
}
```

**Python 脚本核心逻辑**：
```python
from nptdms import TdmsFile
import numpy as np
import json

tdms = TdmsFile.read(path)
channel = tdms.groups()[0].channels()[0]
data = channel.data.astype(np.float64)

# 输出元数据 JSON
meta = {
    'name': channel.name,
    'sampleRate': 1.0 / sample_rate,
    'startTimestamp': start_ts,
    'count': len(data)
}
sys.stdout.write(json.dumps(meta) + '\n')

# 输出二进制数据（Big Endian）
data.astype('>f8').tofile(sys.stdout.buffer)
```

**技术作用**：
- **Python 进程调用**利用了 Python 生态中成熟的 `nptdms` 库，避免了在 Java 中重新实现 TDMS 解析器
- **二进制传输**提升了数据传输效率，避免了 JSON 序列化的开销
- **Big Endian 格式**确保了跨平台兼容性

---

#### 步骤 3：Flink 流处理（滤波 + 异常检测）

**入口类/方法**：`RealTimeDetectionServiceImpl.onCompleted()` → Flink Local Environment

**技术实现**：
- 创建 **Flink Local Environment**（单机模式，适合文件批处理场景）
- 将解析后的 `List<TdmsSample>` 转换为 `DataStream<TdmsSample>`
- 使用 `GenericFilterProcessFunction` 进行流处理

**代码示例**：
```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
env.setParallelism(1); // 保证时序处理

DataStream<TdmsSample> stream = env.fromCollection(data.getSamples());

DataStream<FilterResult> resultStream = stream
    .keyBy(s -> "default_channel")
    .process(new GenericFilterProcessFunction(500, 3.0, true, algorithm))
    .filter(result -> result.isAnomaly()); // 仅收集异常点
```

**技术作用**：
- **Flink 流处理引擎**提供了高性能的流式计算能力，支持状态管理和窗口操作
- **Local Environment**适合批处理场景，避免了分布式集群的复杂性
- **KeyedStream**保证了同一通道的数据按顺序处理

---

#### 步骤 4：滤波算法处理

**入口类/方法**：`GenericFilterProcessFunction.processElement()`

**技术实现**：
- 使用 **策略模式**调用对应的滤波算法（由 `FilterFactory.create()` 创建）
- 维护 **滑动窗口状态**（使用 Flink `ValueState`）：
  - `Deque<WindowEntry>`：窗口内的原始值和滤波值
  - `WindowStats`：窗口统计信息（原始值平方和、滤波值平方和、噪声平方和）
- 计算 **SNR（信噪比）**：滤波前后的信号功率与噪声功率比值（dB）

**代码示例**：
```java
@Override
public void processElement(TdmsSample sample, Context ctx, Collector<FilterResult> out) {
    // 1. 滤波处理
    double filtered = strategy.filter(sample.getValue(), sample.getTimestamp());
    
    // 2. 更新滑动窗口
    WindowEntry entry = new WindowEntry(sample.getTimestamp(), sample.getValue(), filtered);
    window.addLast(entry);
    
    // 3. 清理过期数据
    long boundary = sample.getTimestamp() - windowMs;
    while (!window.isEmpty() && window.peekFirst().timestamp < boundary) {
        window.pollFirst();
    }
    
    // 4. 计算 SNR
    double signalPowerRaw = stats.sumOriginalSq / window.size();
    double noisePower = stats.sumNoiseSq / window.size();
    double snrBefore = 10 * Math.log10(signalPowerRaw / noisePower);
    
    // 5. 异常检测（基于能量阈值）
    double energy = signalPowerRaw;
    boolean anomaly = energy > anomalyThreshold && window.size() > 0;
}
```

**滤波算法示例（卡尔曼滤波）**：
```java
public static class KalmanFilter implements FilterStrategy {
    protected double x = 0; // 状态估计
    protected double p = 1.0; // 误差协方差
    protected final double q = 0.00001; // 过程噪声
    protected final double r = 0.1; // 测量噪声

    @Override
    public double filter(double measurement, long timestamp) {
        // 预测
        double pPred = p + q;
        // 更新
        double k = pPred / (pPred + r); // 卡尔曼增益
        x = x + k * (measurement - x);
        p = (1 - k) * pPred;
        return x;
    }
}
```

**技术作用**：
- **策略模式**使得算法切换无需修改核心处理逻辑，符合开闭原则
- **滑动窗口**实现了时间窗口内的统计计算，支持实时 SNR 计算
- **Flink State**保证了窗口状态在故障恢复时的持久化（虽然 Local Environment 模式下未启用 Checkpoint）

---

#### 步骤 5：异常检测

**入口类/方法**：`GenericFilterProcessFunction.processElement()` 中的异常检测逻辑

**技术实现**：
- 基于 **能量阈值**进行异常检测：`energy > anomalyThreshold`
- 能量计算：`energy = signalPowerRaw = sumOriginalSq / windowSize`
- 阈值配置：默认 `anomalyThreshold = 3.0`（3-sigma 原则）

**代码示例**：
```java
double energy = signalPowerRaw;
boolean anomaly = anomalyEnabled && energy > anomalyThreshold && windowSize > 0;
if (anomaly) {
    anomalyCount++;
}
```

**技术作用**：
- **能量阈值检测**简单高效，适合实时场景
- **3-sigma 原则**基于统计学原理，能够识别超出正常范围的数据点

---

#### 步骤 6：结果收集与返回

**入口类/方法**：`RealTimeDetectionServiceImpl.onCompleted()` 中的结果收集

**技术实现**：
- 使用 **Flink `executeAndCollect()`** 方法同步收集流处理结果
- 仅收集异常点（通过 `.filter(result -> result.isAnomaly())` 过滤）
- 构建 `DetectionResult` 响应，包含：
  - `job_id`：任务 ID
  - `sample_count`：总样本数
  - `anomaly_count`：异常点数量
  - `processing_time_ms`：处理耗时
  - `throughput_mbps`：吞吐量（MB/s）
  - `anomalies`：异常事件列表

**代码示例**：
```java
List<AnomalyEvent> anomalies = new ArrayList<>();
try (CloseableIterator<FilterResult> iterator = resultStream.executeAndCollect()) {
    while (iterator.hasNext()) {
        FilterResult result = iterator.next();
        anomalyCount++;
        anomalies.add(AnomalyEvent.newBuilder()
            .setTimestamp(result.getTimestamp())
            .setValue(result.getOriginalValue())
            .setEnergy(result.getEnergy())
            .build());
    }
}

double throughput = (fileBytes / 1024.0 / 1024.0) / (totalTimeMs / 1000.0);

DetectionResult response = DetectionResult.newBuilder()
    .setJobId(jobId)
    .setSampleCount(data.getSamples().size())
    .setAnomalyCount(anomalyCount)
    .setProcessingTimeMs(totalTimeMs)
    .setThroughputMbps(throughput)
    .addAllAnomalies(anomalies)
    .build();

responseObserver.onNext(response);
responseObserver.onCompleted();
```

**技术作用**：
- **executeAndCollect()** 提供了同步收集流处理结果的便捷方式
- **仅收集异常点**减少了内存占用和网络传输开销
- **吞吐量计算**帮助评估系统性能

---

#### 步骤 7：资源清理

**入口类/方法**：`RealTimeDetectionServiceImpl.cleanup()`

**技术实现**：
- 关闭文件输出流
- 删除临时文件

**代码示例**：
```java
private void cleanup() {
    try {
        if (outputStream != null) outputStream.close();
        if (tempFile != null && tempFile.exists()) tempFile.delete();
    } catch (IOException e) {
        // ignore
    }
}
```

**技术作用**：
- **资源清理**防止临时文件堆积，避免磁盘空间耗尽

---

## 4. 技术实现与组件角色

### 4.1 gRPC 服务层

#### 4.1.1 RealTimeDetectionServiceImpl

**类路径**：`cn.iocoder.yudao.module.detection.service.RealTimeDetectionServiceImpl`

**职责**：
- 实现 gRPC 服务接口 `DetectionServiceGrpc.DetectionServiceImplBase`
- 处理流式文件上传和实时检测请求

**关键技术**：
- **gRPC StreamObserver**：处理双向流式通信
- **临时文件管理**：使用 `Files.createTempFile()` 创建临时文件
- **Flink Local Environment**：在服务内部启动 Flink 作业进行流处理

**解决的问题**：
- 大文件上传的内存压力：通过流式传输和临时文件避免内存溢出
- 实时处理需求：使用 Flink 流处理引擎实现低延迟的数据处理

---

### 4.2 TDMS 解析层

#### 4.2.1 FastTdmsParser

**类路径**：`cn.iocoder.yudao.module.detection.util.FastTdmsParser`

**职责**：
- 调用 Python 进程解析 TDMS 文件
- 将解析结果转换为 Java 对象 `FastParsedData`

**关键技术**：
- **ProcessBuilder**：创建和管理 Python 子进程
- **JSON 解析**：使用 Jackson `ObjectMapper` 解析元数据
- **二进制数据读取**：使用 `DataInputStream` 读取 Big Endian double 数组

**解决的问题**：
- TDMS 格式解析：利用 Python 生态的成熟库，避免重复造轮子
- 跨语言通信：通过标准输入输出和二进制格式实现高效数据传输

---

### 4.3 Flink 流处理层

#### 4.3.1 GenericFilterProcessFunction

**类路径**：`cn.iocoder.yudao.module.detection.logic.GenericFilterProcessFunction`

**职责**：
- 实现 Flink `KeyedProcessFunction`，对每个数据样本进行滤波和异常检测
- 维护滑动窗口状态，计算 SNR 和能量指标

**关键技术**：
- **Flink State**：使用 `ValueState` 存储窗口状态和统计信息
- **滑动窗口算法**：基于时间戳的窗口清理逻辑
- **SNR 计算**：信号功率与噪声功率的比值（dB）

**解决的问题**：
- 实时统计计算：通过滑动窗口实现时间窗口内的实时统计
- 状态管理：Flink State 保证了状态的一致性和可恢复性

**关键代码**：
```java
// 滑动窗口清理
long boundary = sample.getTimestamp() - windowMs;
while (!window.isEmpty() && window.peekFirst().timestamp < boundary) {
    WindowEntry removed = window.pollFirst();
    // 更新统计信息
    stats.sumOriginalSq -= removed.original * removed.original;
    stats.sumFilteredSq -= removed.filtered * removed.filtered;
    stats.sumNoiseSq -= (removed.original - removed.filtered) * (removed.original - removed.filtered);
}
```

---

### 4.4 滤波算法层

#### 4.4.1 FilterFactory

**类路径**：`cn.iocoder.yudao.module.detection.logic.filter.FilterFactory`

**职责**：
- 根据 `FilterAlgorithm` 枚举创建对应的滤波策略实例
- 实现 20 种滤波算法

**关键技术**：
- **策略模式**：`FilterStrategy` 接口定义统一接口
- **工厂模式**：`create()` 方法根据算法类型创建实例

**支持的算法**：
1. **自适应滤波**：卡尔曼（Kalman）、LMS、NLMS、RLS、粒子滤波（Particle）、EKF、UKF
2. **频域滤波**：巴特沃斯（Butterworth）、切比雪夫（Chebyshev）、FIR、IIR
3. **空域滤波**：均值（Mean）、中值（Median）、高斯（Gaussian）、双边（Bilateral）
4. **变换域滤波**：小波（Wavelet）、维纳（Wiener）、形态学（Morphology）
5. **高级滤波**：SG平滑（Savitzky-Golay）、自适应陷波（Adaptive Notch）

**解决的问题**：
- 算法可扩展性：新增算法只需实现 `FilterStrategy` 接口并在工厂中注册
- 算法切换灵活性：运行时根据请求参数选择不同算法

---

### 4.5 WebSocket 推送层

#### 4.5.1 DetectionWebSocketHandler

**类路径**：`cn.iocoder.yudao.module.detection.websocket.DetectionWebSocketHandler`

**职责**：
- 管理 WebSocket 客户端连接
- 广播检测结果给所有订阅的客户端

**关键技术**：
- **Spring WebSocket**：使用 `TextWebSocketHandler` 处理 WebSocket 消息
- **连接池管理**：使用 `ConcurrentHashMap` 存储客户端会话
- **JSON 序列化**：使用 Jackson `ObjectMapper` 序列化 `FilterResult`

**解决的问题**：
- 实时数据推送：前端无需轮询，服务端主动推送检测结果
- 多客户端支持：通过连接池管理多个客户端连接

**关键代码**：
```java
public void broadcast(FilterResult result) {
    String json = OBJECT_MAPPER.writeValueAsString(result);
    TextMessage message = new TextMessage(json);
    
    for (WebSocketSession session : SESSIONS.values()) {
        if (session.isOpen()) {
            session.sendMessage(message);
        }
    }
}
```

---

### 4.6 Netty 数据接入层

#### 4.6.1 NettyServer

**类路径**：`cn.iocoder.yudao.module.detection.netty.NettyServer`

**职责**：
- 启动两个 Netty 服务器：
  - **Ingestion Server**（端口 9999）：接收多通道实时数据
  - **Data Provider Server**（端口 9998）：为 Flink 提供数据源

**关键技术**：
- **Netty NIO**：使用 `NioEventLoopGroup` 实现异步非阻塞 I/O
- **LengthFieldBasedFrameDecoder**：处理基于长度字段的帧解码
- **多线程启动**：使用独立线程启动两个服务器

**解决的问题**：
- 高性能数据接收：Netty 提供了比传统 BIO 更高的并发处理能力
- 多协议支持：支持二进制数据接收，适用于工业设备直接接入

---

#### 4.6.2 NettyServerHandler

**类路径**：`cn.iocoder.yudao.module.detection.netty.NettyServerHandler`

**职责**：
- 解析二进制数据包（时间戳 + 通道名 + 数值）
- 将解析后的 `TdmsSample` 放入阻塞队列

**关键技术**：
- **ByteBuf 操作**：使用 Netty 的 `ByteBuf` 进行二进制数据读取
- **阻塞队列**：`LinkedBlockingQueue` 作为 Flink 数据源的中转

**数据格式**：
```
[4字节长度][8字节时间戳][4字节通道名长度][N字节通道名][8字节数值]
```

**解决的问题**：
- 数据缓冲：通过队列缓冲数据，避免数据丢失
- Flink 集成：队列可以作为 Flink Source 的数据源

---

### 4.7 数据持久化层

#### 4.7.1 TDengineSink

**类路径**：`cn.iocoder.yudao.module.detection.sink.TDengineSink`

**职责**：
- 将 `FilterResult` 批量写入 TDengine 时序数据库

**关键技术**：
- **Flink JDBC Connector**：使用 `JdbcSink` 实现数据库写入
- **批量写入**：配置 `batchSize=1000` 和 `batchIntervalMs=200` 提升写入性能
- **超级表设计**：使用 TDengine 的超级表（STable）机制，按通道自动创建子表

**SQL 示例**：
```sql
CREATE STABLE detection_results (
    ts TIMESTAMP,
    raw_val DOUBLE,
    filtered_val DOUBLE,
    is_anomaly TINYINT
) TAGS (channel_name BINARY(64));
```

**解决的问题**：
- 时序数据存储：TDengine 专为时序数据优化，写入和查询性能优异
- 数据分区：通过超级表机制实现按通道自动分区

---

### 4.8 HTTP 接口层

#### 4.8.1 DetectionController

**类路径**：`cn.iocoder.yudao.module.detection.controller.admin.DetectionController`

**职责**：
- 提供 HTTP RESTful 接口：
  - 文件上传接口
  - 任务状态查询接口
  - 检测结果推送接口（供 Flink 调用）

**关键技术**：
- **Spring MVC**：使用 `@RestController` 和 `@RequestMapping` 定义接口
- **MultipartFile**：处理文件上传
- **异步处理**：使用 `Thread` 模拟异步任务处理

**解决的问题**：
- HTTP 协议支持：为不支持 gRPC 的客户端提供 HTTP 接口
- 任务管理：提供任务状态查询功能，便于前端展示进度

---

## 5. 数据模型与持久化

### 5.1 实体类 (Entity)

#### 5.1.1 TdmsSample

**类路径**：`cn.iocoder.yudao.module.detection.api.dto.TdmsSample`

**字段说明**：
```java
public class TdmsSample implements Serializable {
    private long timestamp;    // 时间戳（毫秒）
    private double value;      // 采样值
    private String channel;    // 通道名称
}
```

**作用**：表示 TDMS 文件中的一个采样点，是 Flink 流处理的基本数据单元。

---

#### 5.1.2 FilterResult

**类路径**：`cn.iocoder.yudao.module.detection.logic.dto.FilterResult`

**字段说明**：
```java
public class FilterResult implements Serializable {
    private long timestamp;           // 时间戳
    private String channel;          // 通道名称
    private double originalValue;    // 原始值
    private double filteredValue;    // 滤波后的值
    private boolean anomaly;         // 是否异常
    private double energy;           // 能量值
    private double snrBeforeDb;      // 滤波前 SNR (dB)
    private double snrAfterDb;       // 滤波后 SNR (dB)
    private double snrDeltaDb;       // SNR 改善量 (dB)
    private double throughputKps;    // 吞吐量 (千样本/秒)
    private double throughputMbps;   // 吞吐量 (MB/s)
    private long anomalyCount;        // 异常计数
}
```

**作用**：表示滤波处理后的结果，包含原始值、滤波值、异常状态和统计信息。

---

#### 5.1.3 DetectionTaskVO

**类路径**：`cn.iocoder.yudao.module.detection.controller.admin.vo.DetectionTaskVO`

**字段说明**：
```java
public class DetectionTaskVO {
    private String id;              // 任务 ID
    private String filename;       // 文件名
    private String algorithm;      // 算法名称
    private String status;          // 状态 (PENDING/PROCESSING/COMPLETED/FAILED)
    private Integer progress;      // 进度 (0-100)
    private String size;           // 文件大小（格式化字符串）
    private Long sizeBytes;        // 文件大小（字节）
    private Long startTime;        // 开始时间
    private Long lastUpdateTime;   // 最后更新时间
    private String speed;          // 处理速度 (MB/s)
}
```

**作用**：用于 HTTP 接口返回任务状态信息。

---

### 5.2 数据表结构

#### 5.2.1 TDengine 超级表

**表名**：`detection_results`

**结构**：
```sql
CREATE STABLE detection_results (
    ts TIMESTAMP,           -- 时间戳
    raw_val DOUBLE,         -- 原始值
    filtered_val DOUBLE,    -- 滤波后的值
    is_anomaly TINYINT      -- 是否异常 (1: 异常, 0: 正常)
) TAGS (
    channel_name BINARY(64) -- 通道名称（标签）
);
```

**设计说明**：
- **超级表（STable）**：TDengine 的特殊表结构，用于定义表结构和标签
- **子表自动创建**：Flink 写入时会自动为每个通道创建子表，例如 `d_channel_001`
- **标签分区**：通过 `channel_name` 标签实现按通道分区，提升查询性能

**插入示例**：
```sql
INSERT INTO d_channel_001 USING detection_results TAGS ('channel_001') 
VALUES (1699123456000, 1.23, 1.20, 0);
```

---

### 5.3 数据访问层 (Mapper)

该模块**未使用 MyBatis Mapper**，原因：
1. **Flink JDBC Connector**：直接使用 Flink 的 `JdbcSink` 进行数据库写入，无需 Mapper
2. **gRPC 服务**：gRPC 服务层直接调用 Flink 处理逻辑，不涉及数据库查询
3. **HTTP 接口**：`DetectionController` 中的查询接口目前是模拟数据，未真正查询数据库

**未来扩展**：如果需要查询历史数据，可以：
- 使用 TDengine JDBC Driver 直接查询
- 或创建对应的 Mapper 接口使用 MyBatis-Plus

---

## 6. 配置与依赖

### 6.1 主要配置项

#### 6.1.1 application.yaml

**文件路径**：`yudao-module-detection-server/src/main/resources/application.yaml`

**关键配置**：
```yaml
server:
  port: 48083  # HTTP 服务端口

grpc:
  server:
    port: 9090  # gRPC 服务端口

yudao:
  detection:
    netty-port: 9999           # Netty 数据接收端口
    data-port: 9998            # Netty 数据提供端口
    flink-parallelism: 1       # Flink 并行度
    filter-window-ms: 5000     # 滤波窗口大小（毫秒）
    anomaly-threshold: 3.0     # 异常检测阈值（Sigma）
    tdengine-batch-size: 1000  # TDengine 批量写入大小
    tdengine-batch-interval: 200  # TDengine 批量写入间隔（毫秒）
    tdengine-url: jdbc:TAOS://tdengine-service:6030/yudao_detection
```

**配置说明**：
- **netty-port**：Netty 数据接收服务器端口，用于接收多通道实时数据
- **data-port**：Netty 数据提供服务器端口，供 Flink 作业连接获取数据
- **flink-parallelism**：Flink 作业并行度，设置为 1 保证时序处理
- **filter-window-ms**：滑动窗口大小，影响 SNR 计算的统计范围
- **anomaly-threshold**：异常检测阈值，基于能量值的 3-sigma 原则
- **tdengine-batch-size**：批量写入大小，提升写入性能
- **tdengine-batch-interval**：批量写入间隔，平衡延迟和吞吐量

---

#### 6.1.2 DetectionProperties

**类路径**：`cn.iocoder.yudao.module.detection.framework.DetectionProperties`

**作用**：使用 `@ConfigurationProperties` 绑定配置项，提供类型安全的配置访问。

**代码示例**：
```java
@Data
@Component
@ConfigurationProperties(prefix = "yudao.detection")
public class DetectionProperties {
    private Integer nettyPort = 9999;
    private Integer dataPort = 9998;
    private Integer flinkParallelism = 1;
    private Long filterWindowMs = 5000L;
    private Double anomalyThreshold = 3.0;
    private Integer tdengineBatchSize = 1000;
    private Integer tdengineBatchInterval = 200;
}
```

---

### 6.2 外部依赖

#### 6.2.1 核心框架依赖

| 依赖 | 版本 | 作用 |
|------|------|------|
| Spring Boot | 2.7.18 | 基础框架 |
| Spring Cloud Alibaba | 2021.0.4.0 | 微服务框架 |
| gRPC Spring Boot Starter | 2.14.0.RELEASE | gRPC 服务支持 |
| Apache Flink | 1.18.1 | 流处理引擎 |
| Netty | 4.1.107.Final | 高性能网络框架 |
| TDengine JDBC Driver | 3.0.2 | TDengine 数据库驱动 |

---

#### 6.2.2 外部服务依赖

1. **Nacos 注册中心**
   - **作用**：服务注册与发现
   - **配置**：`spring.cloud.nacos.discovery.server-addr: 127.0.0.1:8848`
   - **使用场景**：微服务间调用时通过 Nacos 发现服务地址

2. **TDengine 时序数据库**
   - **作用**：存储检测结果数据
   - **连接信息**：`jdbc:TAOS://tdengine-service:6030/yudao_detection`
   - **使用场景**：Flink Sink 批量写入检测结果

3. **Python 环境**
   - **作用**：执行 TDMS 文件解析脚本
   - **依赖库**：`nptdms`、`numpy`
   - **使用场景**：`FastTdmsParser` 调用 Python 进程解析文件

---

#### 6.2.3 模块间依赖

1. **yudao-module-detection-api**
   - **作用**：定义 gRPC 接口和 DTO
   - **依赖关系**：server 模块依赖 api 模块

2. **yudao-spring-boot-starter-web**
   - **作用**：提供 HTTP 接口支持
   - **使用场景**：`DetectionController` 提供 RESTful 接口

3. **yudao-spring-boot-starter-websocket**
   - **作用**：提供 WebSocket 支持
   - **使用场景**：`DetectionWebSocketHandler` 推送实时数据

4. **yudao-spring-boot-starter-security**
   - **作用**：提供安全认证支持
   - **使用场景**：接口权限控制（当前使用 `@PermitAll` 允许匿名访问）

---

## 7. 总结

### 7.1 设计优点

1. **高性能架构**
   - **gRPC 流式传输**：支持大文件边接收边处理，避免内存溢出
   - **Flink 流处理**：利用 Flink 的高性能流计算能力，支持实时处理
   - **Netty 异步 I/O**：提供高并发的数据接收能力
   - **批量写入**：TDengine 批量写入提升持久化性能

2. **可扩展性**
   - **策略模式**：滤波算法易于扩展，新增算法只需实现接口
   - **工厂模式**：算法创建逻辑集中管理，便于维护
   - **多协议支持**：同时支持 gRPC、HTTP、WebSocket、Netty，适应不同场景

3. **健壮性**
   - **资源管理**：临时文件自动清理，防止磁盘空间耗尽
   - **异常处理**：完善的异常捕获和日志记录
   - **状态管理**：Flink State 支持故障恢复（分布式模式下）

4. **实时性**
   - **流式处理**：数据到达即处理，低延迟
   - **WebSocket 推送**：检测结果实时推送给前端
   - **滑动窗口**：支持时间窗口内的实时统计计算

---

### 7.2 未来扩展方向

1. **分布式 Flink 集群**
   - 当前使用 Local Environment，未来可迁移到 Flink 集群模式
   - 支持 Checkpoint 和 Savepoint，实现故障恢复
   - 支持动态扩缩容，应对流量波动

2. **更多数据源支持**
   - 支持 Kafka、Pulsar 等消息队列作为数据源
   - 支持直接从工业设备采集数据（Modbus、OPC UA 等协议）

3. **机器学习集成**
   - 集成 TensorFlow/PyTorch 模型进行异常检测
   - 支持模型在线训练和更新
   - 实现自适应阈值调整

4. **可视化增强**
   - 提供更丰富的实时数据可视化图表
   - 支持历史数据回放和分析
   - 提供异常事件关联分析

5. **性能优化**
   - 使用 Flink 的异步 I/O 提升数据库写入性能
   - 引入 Redis 缓存热点数据
   - 优化 Python 进程调用，考虑使用进程池复用

6. **监控与运维**
   - 集成 Prometheus + Grafana 进行指标监控
   - 提供详细的性能指标（吞吐量、延迟、错误率）
   - 支持分布式链路追踪（Jaeger/Zipkin）

---

## 附录：关键代码位置索引

| 组件 | 类路径 | 文件路径 |
|------|--------|----------|
| gRPC 服务实现 | `RealTimeDetectionServiceImpl` | `yudao-module-detection-server/src/main/java/cn/iocoder/yudao/module/detection/service/RealTimeDetectionServiceImpl.java` |
| TDMS 解析器 | `FastTdmsParser` | `yudao-module-detection-server/src/main/java/cn/iocoder/yudao/module/detection/util/FastTdmsParser.java` |
| Flink 处理函数 | `GenericFilterProcessFunction` | `yudao-module-detection-server/src/main/java/cn/iocoder/yudao/module/detection/logic/GenericFilterProcessFunction.java` |
| 滤波工厂 | `FilterFactory` | `yudao-module-detection-server/src/main/java/cn/iocoder/yudao/module/detection/logic/filter/FilterFactory.java` |
| WebSocket 处理器 | `DetectionWebSocketHandler` | `yudao-module-detection-server/src/main/java/cn/iocoder/yudao/module/detection/websocket/DetectionWebSocketHandler.java` |
| Netty 服务器 | `NettyServer` | `yudao-module-detection-server/src/main/java/cn/iocoder/yudao/module/detection/netty/NettyServer.java` |
| TDengine Sink | `TDengineSink` | `yudao-module-detection-server/src/main/java/cn/iocoder/yudao/module/detection/sink/TDengineSink.java` |
| HTTP 控制器 | `DetectionController` | `yudao-module-detection-server/src/main/java/cn/iocoder/yudao/module/detection/controller/admin/DetectionController.java` |
| gRPC 接口定义 | `detection.proto` | `yudao-module-detection-api/src/main/proto/detection.proto` |
| 配置文件 | `application.yaml` | `yudao-module-detection-server/src/main/resources/application.yaml` |

---

**文档版本**：v1.0  
**最后更新**：2024年  
**维护者**：yudao 开发团队
