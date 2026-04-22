# yudao-module-monitor 技术文档

## 1. 文档概述与目标

### 1.1 模块简介

`yudao-module-monitor` 是芋道云原生微服务架构中的监控分析模块，专注于工业物联网场景下的 TDMS（Technical Data Management Streaming）数据回放式实时流处理和离线历史分析。该模块的核心业务价值在于：**通过 HTTP 接口接收 TDMS 格式的工业数据文件，利用 Python 的 nptdms 库解析文件，然后通过 Apache Flink 流处理引擎按照原始时间间隔"回放"数据，对数据进行实时滤波处理和异常检测，并通过 WebSocket 实时推送给前端展示；同时提供离线历史分析功能，支持多文件合并分析和流式响应**。

该模块解决了工业场景中历史数据的回放分析难题，能够模拟真实的数据采集时序，支持实时可视化展示和离线批量分析，为设备健康监测和历史数据挖掘提供技术支撑。

### 1.2 主要目标

本文档的主要目标是：**将此模块的整套实现流程、技术选型和架构设计清晰地呈现出来**，使任何一位开发者都能：
- 理解模块的整体架构和各组件的职责边界
- 掌握从文件上传到实时推送和离线分析的完整业务流程
- 了解每个技术组件在流程中的具体作用和解决的问题
- 能够基于现有架构进行功能扩展和性能优化

---

## 2. 总体架构设计

### 2.1 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        客户端层                                  │
│  (HTTP Client / WebSocket Client)                               │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    yudao-module-monitor-server                   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  MonitorUploadController                                  │  │
│  │  ├─ POST /monitor/upload          (实时回放)              │  │
│  │  ├─ POST /monitor/history/analyze (离线分析)              │  │
│  │  ├─ POST /monitor/realtime/analyze (实时分析)            │  │
│  │  └─ POST /monitor/realtime/analyze-stream (流式响应)     │  │
│  └────────┬─────────────────────────────────────────────────┘  │
│           │                                                      │
│           ├──────────────────┬──────────────────┐               │
│           ▼                  ▼                  ▼               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐    │
│  │ TdmsParsing  │  │ FlinkPlayback│  │ HistoryAnalysis  │    │
│  │ Service      │  │ Service      │  │ Service          │    │
│  └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘    │
│         │                  │                    │                │
│         ▼                  ▼                    ▼                │
│  ┌──────────────┐  ┌──────────────────────────────────────┐    │
│  │ Python       │  │ Apache Flink Local Environment      │    │
│  │ Process      │  │ ┌────────────────────────────────┐  │    │
│  │ (nptdms)     │  │ │ TdmsReplaySource               │  │    │
│  │              │  │ │ (按时间间隔回放数据)              │  │    │
│  │              │  │ │         │                      │  │    │
│  │              │  │ │         ▼                      │  │    │
│  │              │  │ │ SignalProcessFunction          │  │    │
│  │              │  │ │ ├─ Kalman/LMS Filter           │  │    │
│  │              │  │ │ ├─ 滑动窗口统计                │  │    │
│  │              │  │ │ └─ 异常检测 (残差能量)          │  │    │
│  │              │  │ │         │                      │  │    │
│  │              │  │ │         ▼                      │  │    │
│  │              │  │ │ MonitorResultSink             │  │    │
│  │              │  │ └────────────────────────────────┘  │    │
│  └──────────────┘  └──────────────┬───────────────────────┘    │
│                                    │                              │
│                                    ▼                              │
│                          ┌──────────────────┐                     │
│                          │ MonitorResultHub │                     │
│                          │ (结果分发中心)    │                     │
│                          └────────┬─────────┘                     │
│                                   │                                │
│                                   ▼                                │
│                          ┌──────────────────┐                     │
│                          │ WebSocket Handler│                     │
│                          │ (实时推送)        │                     │
│                          └──────────────────┘                     │
└──────────────────────────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    外部服务层                                    │
│  ┌──────────────┐  ┌──────────────┐                            │
│  │  Python      │  │  Nacos       │                            │
│  │  (nptdms)    │  │  (注册中心)   │                            │
│  └──────────────┘  └──────────────┘                            │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 架构说明

#### 2.2.1 模块定位与职责边界

`yudao-module-monitor` 在整个 yudao 系统中的定位是：
- **数据监控与分析服务**：作为独立的微服务模块，通过 Nacos 注册到服务注册中心，对外提供 HTTP 和 WebSocket 接口
- **回放式流处理引擎**：基于 Flink 的流处理能力，按照原始时间间隔回放历史数据，模拟实时数据采集场景
- **双模式支持**：同时支持实时回放（Flink 流处理 + WebSocket 推送）和离线分析（内存处理 + HTTP 响应）

职责边界：
- **负责**：TDMS 文件解析、回放式流处理、实时滤波、异常检测、结果推送、历史数据分析
- **不负责**：设备管理（由 `yudao-module-iot` 负责）、用户权限管理（由 `yudao-module-system` 负责）、数据持久化（当前版本不持久化到数据库）

#### 2.2.2 核心设计模式

该模块采用了以下设计模式：

1. **MVC 分层架构**
   - **Controller 层**：`MonitorUploadController` 处理 HTTP 请求，提供文件上传、实时回放、离线分析等接口
   - **Service 层**：`TdmsParsingService`、`FlinkPlaybackService`、`HistoryAnalysisService` 实现核心业务逻辑
   - **Flink 层**：`TdmsReplaySource`、`SignalProcessFunction`、`MonitorResultSink` 封装流处理逻辑

2. **发布-订阅模式（Pub-Sub Pattern）**
   - `MonitorResultHub` 作为消息中心，管理多个 WebSocket 会话
   - Flink Sink 发布消息到 Hub，Hub 广播给所有订阅的客户端

3. **策略模式（Strategy Pattern）**
   - `FilterConfig` 配置滤波算法类型（Kalman 或 LMS）
   - `SignalProcessFunction` 根据配置选择对应的滤波算法

4. **适配器模式（Adapter Pattern）**
   - `TdmsReplaySource` 将内存中的 `List<TdmsSample>` 适配为 Flink Source
   - `MonitorResultSink` 将 Flink 流数据适配为 WebSocket 消息

#### 2.2.3 模块内外部服务交互方式

1. **HTTP REST API**
   - `MonitorUploadController` 提供 RESTful 接口：
     - `POST /monitor/upload`：上传文件并启动实时回放
     - `POST /monitor/history/analyze`：离线历史分析
     - `POST /monitor/realtime/analyze`：实时分析（同步返回）
     - `POST /monitor/realtime/analyze-stream`：实时分析（流式返回，使用 Reactor Flux）

2. **WebSocket 实时推送**
   - `MonitorWebSocketHandler` 处理 WebSocket 连接，通过 `jobId` 参数订阅特定任务
   - `MonitorResultHub` 维护任务与会话的映射关系，支持多客户端订阅同一任务

3. **Python 进程调用**
   - `TdmsParsingServiceImpl` 通过 `ProcessBuilder` 调用 Python 脚本解析 TDMS 文件
   - Python 脚本使用 `nptdms` 库读取文件，输出 JSON 格式的元数据和采样点

4. **Flink 流处理**
   - `FlinkPlaybackServiceImpl` 创建 Flink Local Environment
   - `TdmsReplaySource` 按照原始时间间隔回放数据
   - `SignalProcessFunction` 进行滤波和异常检测
   - `MonitorResultSink` 将结果推送到 WebSocket

5. **Spring Bean 注入**
   - `MonitorResultSink` 通过 `SpringUtils.getBean()` 获取 `MonitorResultHub`
   - 避免将 Spring Bean 直接作为字段，防止 Flink 序列化问题

---

## 3. 核心工作流程详解

### 3.1 流程概览

以一个典型的业务场景为例：**客户端上传一个 TDMS 文件，要求使用卡尔曼滤波算法进行实时回放分析**。

完整流程链路如下：

```
文件上传 → TDMS 解析（Python）→ 数据转换 → Flink 作业启动 → 回放数据源 
→ 滤波处理 → 异常检测 → 结果推送（WebSocket）→ 前端实时展示
```

### 3.2 步骤拆解

#### 步骤 1：文件上传与验证

**入口类/方法**：`MonitorUploadController.upload()`

**技术实现**：
- 使用 **Spring MVC** 的 `@RequestParam` 接收 `MultipartFile`
- 验证文件非空且为 `.tdms` 格式
- 接收滤波算法参数（`filterType`、`kalmanQ`、`kalmanR` 等）

**代码示例**：
```java
@PostMapping("/upload")
public CommonResult<MonitorUploadResponse> upload(
        @RequestParam(value = "file", required = false) MultipartFile file,
        @RequestParam(value = "filterType", required = false, defaultValue = "KALMAN") FilterType filterType,
        @RequestParam(value = "kalmanQ", required = false, defaultValue = "1e-5") Double kalmanQ,
        @RequestParam(value = "kalmanR", required = false, defaultValue = "0.1") Double kalmanR) {
    if (file == null || file.isEmpty()) {
        throw ServiceExceptionUtil.invalidParamException("请先上传有效 TDMS 文件");
    }
    // ... 处理逻辑
}
```

**技术作用**：
- **MultipartFile** 支持大文件上传（配置了 `max-file-size: 200MB`）
- **参数验证** 确保输入数据的有效性，提前拦截无效请求

---

#### 步骤 2：TDMS 文件解析

**入口类/方法**：`TdmsParsingServiceImpl.parse()`

**技术实现**：
- 将上传的文件保存为临时文件
- 动态生成 Python 解析脚本
- 通过 **ProcessBuilder** 调用 Python 进程执行脚本
- Python 脚本使用 **nptdms** 库读取 TDMS 文件
- 解析结果以 JSON 格式返回，包含通道元数据和采样点列表

**代码示例**：
```java
@Override
public ParsedTdmsData parse(MultipartFile file) {
    File tempFile = Files.createTempFile("monitor-", ".tdms").toFile();
    file.transferTo(tempFile);
    
    File scriptFile = writeParserScript();
    String json = runPythonParser(scriptFile, tempFile);
    
    JsonNode root = objectMapper.readTree(json);
    // 解析通道元数据
    TdmsChannelMetadata meta = new TdmsChannelMetadata();
    meta.setName(channelNode.get("name").asText());
    meta.setSampleRate(channelNode.path("sampleRate").asDouble());
    // ... 解析采样点
    return parsed;
}
```

**Python 脚本核心逻辑**：
```python
from nptdms import TdmsFile
import json

tdms = TdmsFile.read(path)
channel = tdms.groups()[0].channels()[0]
data = channel.data
times = channel.time_track()

samples = []
for t, v in zip(times, data):
    samples.append({'timestamp': int(t * 1000), 'value': float(v)})

payload = {
    'channel': {
        'name': channel.name,
        'sampleRate': sample_rate,
        'startTimestamp': start_ts,
        'endTimestamp': end_ts,
        'sampleCount': len(data)
    },
    'samples': samples
}
json.dump(payload, sys.stdout)
```

**技术作用**：
- **Python 进程调用** 利用了 Python 生态中成熟的 `nptdms` 库，避免了在 Java 中重新实现 TDMS 解析器
- **JSON 传输** 简化了跨语言数据交换，便于调试和错误处理
- **临时文件管理** 自动清理临时文件，防止磁盘空间耗尽

---

#### 步骤 3：Flink 作业启动

**入口类/方法**：`FlinkPlaybackServiceImpl.startJob()`

**技术实现**：
- 创建 **Flink Local Environment**（单机模式，适合回放场景）
- 配置 JVM 参数以支持 Java 模块系统（JDK 21）
- 禁用 Kryo 强制序列化，使用 POJO 序列化
- 构建 Flink 流处理拓扑：
  - Source：`TdmsReplaySource`（回放数据源）
  - Process：`SignalProcessFunction`（滤波和异常检测）
  - Sink：`MonitorResultSink`（推送到 WebSocket）

**代码示例**：
```java
@Override
public synchronized void startJob(String jobId, ParsedTdmsData data, 
                                   double anomalyThreshold, boolean anomalyEnabled, 
                                   FilterConfig filterConfig) {
    // 设置 ClassLoader 和 JVM 参数
    Thread.currentThread().setContextClassLoader(
        FlinkPlaybackServiceImpl.class.getClassLoader());
    Configuration flinkConfig = new Configuration();
    flinkConfig.setString("env.java.opts", 
        "--add-opens=java.base/java.util=ALL-UNNAMED ...");
    
    StreamExecutionEnvironment env = 
        StreamExecutionEnvironment.createLocalEnvironment(flinkConfig);
    env.setParallelism(1);
    env.getConfig().disableForceKryo();
    
    // 构建流处理拓扑
    DataStream<TdmsSample> sourceStream = env
        .addSource(new TdmsReplaySource(data.getSamples()), "tdms-replay")
        .assignTimestampsAndWatermarks(
            WatermarkStrategy.<TdmsSample>forMonotonousTimestamps()
                .withTimestampAssigner((event, ts) -> event.getTimestamp())
        );
    
    DataStream<MonitorStreamMessage> pipeline = sourceStream
        .keyBy(TdmsSample::getChannel)
        .process(new SignalProcessFunction(jobId, data.getChannel(), 
                                           5000, anomalyThreshold, 
                                           anomalyEnabled, filterConfig));
    
    pipeline.addSink(new MonitorResultSink()).name("ws-push");
    
    // 异步执行作业
    JobClient client = env.executeAsync("monitor-" + jobId);
    jobs.put(jobId, new RunningJob(data, anomalyThreshold, anomalyEnabled, filterConfig));
}
```

**技术作用**：
- **Local Environment** 适合回放场景，避免了分布式集群的复杂性
- **异步执行** 使用 `executeAsync()` 避免阻塞 HTTP 请求线程
- **作业管理** 通过 `jobs` Map 管理运行中的作业，支持停止和更新配置

---

#### 步骤 4：数据回放（TdmsReplaySource）

**入口类/方法**：`TdmsReplaySource.run()`

**技术实现**：
- 实现 Flink 的 `RichParallelSourceFunction` 接口
- 按照原始时间间隔回放数据，模拟实时数据采集
- 使用 `Thread.sleep()` 控制回放速度
- 通过 `ctx.collectWithTimestamp()` 发送带时间戳的数据

**代码示例**：
```java
@Override
public void run(SourceContext<TdmsSample> ctx) throws Exception {
    TdmsSample previous = null;
    for (TdmsSample sample : samples) {
        if (!running.get()) {
            break;
        }
        if (previous != null) {
            long gap = sample.getTimestamp() - previous.getTimestamp();
            if (gap > 0) {
                Thread.sleep(Math.min(gap, 2000)); // 避免长时间阻塞
            }
        }
        synchronized (ctx.getCheckpointLock()) {
            ctx.collectWithTimestamp(sample, sample.getTimestamp());
        }
        previous = sample;
    }
}
```

**技术作用**：
- **时间间隔回放** 保持了原始数据的时序特性，模拟真实的数据采集场景
- **时间戳分配** 通过 `collectWithTimestamp()` 为数据分配事件时间，支持基于时间的窗口操作
- **可中断设计** 通过 `running` 标志支持作业取消

---

#### 步骤 5：信号处理（滤波 + 异常检测）

**入口类/方法**：`SignalProcessFunction.processElement()`

**技术实现**：
- 实现 Flink 的 `KeyedProcessFunction` 接口
- 根据 `FilterConfig` 选择滤波算法（Kalman 或 LMS）
- 维护滑动窗口（5秒窗口）统计原始值、滤波值、残差
- 计算 SNR（信噪比）：滤波前后的信号功率与噪声功率比值（dB）
- 异常检测：基于残差能量的滑动窗口阈值检测

**代码示例**：
```java
@Override
public void processElement(TdmsSample sample, Context ctx, 
                          Collector<MonitorStreamMessage> out) {
    double original = sample.getValue();
    double filtered = applyFilter(original);  // 滤波处理
    double residual = original - filtered;   // 残差
    
    // 更新滑动窗口
    WindowEntry entry = new WindowEntry(sample.getTimestamp(), 
                                        original, filtered, residual);
    window.addLast(entry);
    cleanWindow(sample.getTimestamp());
    
    // 计算 SNR
    double signalPowerRaw = 0.0;
    double signalPowerFiltered = 0.0;
    double noisePower = 0.0;
    for (WindowEntry e : window) {
        signalPowerRaw += e.original * e.original;
        signalPowerFiltered += e.filtered * e.filtered;
        noisePower += e.residual * e.residual;
    }
    int windowSize = window.size();
    if (windowSize > 0) {
        signalPowerRaw /= windowSize;
        signalPowerFiltered /= windowSize;
        noisePower /= windowSize;
    }
    double snrBefore = toDb(signalPowerRaw, noisePower);
    double snrAfter = toDb(signalPowerFiltered, noisePower);
    
    // 异常检测：残差能量阈值
    double energy = 0.0;
    for (WindowEntry e : window) {
        energy += e.residual * e.residual;
    }
    boolean anomaly = anomalyEnabled && energy > anomalyThreshold && windowSize > 0;
    
    // 构建消息并输出
    MonitorStreamMessage message = MonitorStreamMessage.builder()
        .jobId(jobId)
        .timestamp(sample.getTimestamp())
        .originalValue(original)
        .filteredValue(filtered)
        .anomaly(anomaly)
        .energy(energy)
        .snrBeforeDb(snrBefore)
        .snrAfterDb(snrAfter)
        .build();
    out.collect(message);
}
```

**滤波算法实现（卡尔曼滤波）**：
```java
private double applyFilter(double x) {
    if (filterConfig.getType() == FilterType.KALMAN) {
        return kalman.filter(x);
    }
    // LMS 滤波逻辑
    // ...
}

// Kalman1DFilter 实现
public double filter(double z) {
    if (!initialized) {
        // 初始化阶段：取前 N 点均值
        initSum += z;
        initCount++;
        x = initSum / initCount;
        if (initCount >= x0N) {
            initialized = true;
        }
        return x;
    }
    // 预测
    p = p + q;
    // 更新
    double k = p / (p + r);  // 卡尔曼增益
    x = x + k * (z - x);
    p = (1 - k) * p;
    return x;
}
```

**技术作用**：
- **滑动窗口** 实现了时间窗口内的统计计算，支持实时 SNR 计算
- **残差能量检测** 基于滤波后的残差信号进行异常检测，更能反映被滤掉的异常/噪声
- **Flink State** 使用 `ValueState` 存储处理计数和开始时间，支持状态管理

---

#### 步骤 6：结果推送（MonitorResultSink）

**入口类/方法**：`MonitorResultSink.invoke()`

**技术实现**：
- 实现 Flink 的 `RichSinkFunction` 接口
- 使用 `transient` 字段避免序列化 Spring Bean
- 在 `open()` 生命周期中通过 `SpringUtils.getBean()` 获取 `MonitorResultHub`
- 调用 `hub.send()` 推送消息

**代码示例**：
```java
public class MonitorResultSink extends RichSinkFunction<MonitorStreamMessage> {
    private transient MonitorResultHub hub;  // transient 避免序列化
    
    @Override
    public void open(Configuration parameters) {
        // 在 TaskManager 运行时从 Spring 容器获取 Bean
        this.hub = SpringUtils.getBean(MonitorResultHub.class);
    }
    
    @Override
    public void invoke(MonitorStreamMessage value, Context context) {
        if (hub != null) {
            hub.send(value);
        }
    }
}
```

**技术作用**：
- **transient 字段** 避免了 Flink 序列化 Spring Bean 的问题
- **懒加载 Bean** 在运行时获取，解决了 Flink 算子与 Spring 容器的集成问题

---

#### 步骤 7：WebSocket 广播（MonitorResultHub）

**入口类/方法**：`MonitorResultHub.send()`

**技术实现**：
- 使用 `ConcurrentHashMap` 维护 `jobId -> Set<WebSocketSession>` 的映射
- 将 `MonitorStreamMessage` 序列化为 JSON
- 遍历所有订阅该任务的 WebSocket 会话，发送消息

**代码示例**：
```java
@Component
public class MonitorResultHub {
    private final Map<String, Set<WebSocketSession>> jobSessions = 
        new ConcurrentHashMap<>();
    
    public void send(MonitorStreamMessage message) {
        Set<WebSocketSession> sessions = jobSessions.get(message.getJobId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload = JsonUtils.toJsonString(message);
        sessions.forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(payload));
                } catch (IOException e) {
                    log.warn("推送实时数据失败: {}", e.getMessage());
                }
            }
        });
    }
}
```

**技术作用**：
- **多客户端支持** 通过 Map 管理多个会话，支持多客户端订阅同一任务
- **JSON 序列化** 使用统一的 JSON 格式，便于前端解析
- **异常处理** 捕获发送异常，避免影响其他客户端

---

#### 步骤 8：WebSocket 连接管理（MonitorWebSocketHandler）

**入口类/方法**：`MonitorWebSocketHandler.afterConnectionEstablished()`

**技术实现**：
- 实现 Spring WebSocket 的 `TextWebSocketHandler`
- 从 WebSocket URI 的查询参数中提取 `jobId`
- 调用 `MonitorResultHub.register()` 注册会话

**代码示例**：
```java
@Component
public class MonitorWebSocketHandler extends TextWebSocketHandler {
    private final MonitorResultHub resultHub;
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, String> params = UriComponentsBuilder
            .fromUri(session.getUri())
            .build()
            .getQueryParams()
            .toSingleValueMap();
        String jobId = params.get("jobId");
        
        if (jobId == null || jobId.isEmpty()) {
            closeWithReason(session, "缺少 jobId 参数");
            return;
        }
        
        resultHub.register(jobId, session);
    }
}
```

**技术作用**：
- **参数验证** 确保连接时提供有效的 `jobId`，提前拦截无效连接
- **连接管理** 在连接建立和关闭时自动注册和注销，保证资源正确释放

---

## 4. 技术实现与组件角色

### 4.1 HTTP 接口层

#### 4.1.1 MonitorUploadController

**类路径**：`cn.iocoder.yudao.module.monitor.controller.admin.MonitorUploadController`

**职责**：
- 处理文件上传请求
- 提供实时回放、离线分析、流式响应等接口
- 参数验证和异常处理

**关键技术**：
- **Spring MVC**：使用 `@RestController` 和 `@RequestMapping` 定义接口
- **MultipartFile**：处理文件上传
- **Reactor Flux**：提供流式响应支持（`analyzeRealtimeStream`）

**解决的问题**：
- **大文件上传**：支持 200MB 以内的文件上传
- **流式响应**：使用 Reactor Flux 支持大结果集的流式传输，避免内存溢出

**关键代码**：
```java
@PostMapping(value = "/realtime/analyze-stream", 
             produces = MediaType.APPLICATION_NDJSON_VALUE)
public Flux<String> analyzeRealtimeStream(@RequestParam("file") MultipartFile file) {
    HistoryAnalysisResult result = historyAnalysisService.analyze(...);
    
    return Flux.concat(
        Flux.just(metadataJson + "\n"),
        Flux.fromIterable(points)
            .buffer(1000)
            .delayElements(Duration.ofMillis(10))
            .map(batch -> mapper.writeValueAsString(batch) + "\n")
    );
}
```

---

### 4.2 TDMS 解析层

#### 4.2.1 TdmsParsingServiceImpl

**类路径**：`cn.iocoder.yudao.module.monitor.service.impl.TdmsParsingServiceImpl`

**职责**：
- 调用 Python 进程解析 TDMS 文件
- 将解析结果转换为 Java 对象 `ParsedTdmsData`

**关键技术**：
- **ProcessBuilder**：创建和管理 Python 子进程
- **JSON 解析**：使用 Jackson `ObjectMapper` 解析 Python 输出
- **临时文件管理**：使用 Hutool `FileUtil` 自动清理临时文件

**解决的问题**：
- **TDMS 格式解析**：利用 Python 生态的成熟库，避免重复造轮子
- **跨语言通信**：通过标准输入输出和 JSON 格式实现高效数据传输
- **资源清理**：自动清理临时文件，防止磁盘空间耗尽

---

### 4.3 Flink 流处理层

#### 4.3.1 FlinkPlaybackServiceImpl

**类路径**：`cn.iocoder.yudao.module.monitor.service.impl.FlinkPlaybackServiceImpl`

**职责**：
- 管理 Flink 作业的生命周期（启动、停止、更新配置）
- 创建和配置 Flink Local Environment
- 构建流处理拓扑

**关键技术**：
- **Flink Local Environment**：单机模式的流处理环境
- **异步执行**：使用 `executeAsync()` 避免阻塞
- **作业管理**：使用 `ConcurrentHashMap` 管理运行中的作业

**解决的问题**：
- **作业隔离**：每个任务独立的 Flink 作业，互不干扰
- **动态配置**：支持运行时更新滤波算法和异常检测阈值
- **资源管理**：正确释放作业资源，防止内存泄漏

---

#### 4.3.2 TdmsReplaySource

**类路径**：`cn.iocoder.yudao.module.monitor.flink.TdmsReplaySource`

**职责**：
- 将内存中的 `List<TdmsSample>` 适配为 Flink Source
- 按照原始时间间隔回放数据

**关键技术**：
- **RichParallelSourceFunction**：Flink Source 函数接口
- **时间戳分配**：使用 `collectWithTimestamp()` 分配事件时间
- **可中断设计**：通过 `AtomicBoolean` 支持作业取消

**解决的问题**：
- **时序保持**：按照原始时间间隔回放，保持数据的时序特性
- **实时模拟**：模拟真实的数据采集场景，便于前端实时可视化

---

#### 4.3.3 SignalProcessFunction

**类路径**：`cn.iocoder.yudao.module.monitor.flink.SignalProcessFunction`

**职责**：
- 对每个数据样本进行滤波处理
- 维护滑动窗口，计算 SNR 和能量指标
- 进行异常检测

**关键技术**：
- **KeyedProcessFunction**：Flink 处理函数接口
- **滑动窗口算法**：基于时间戳的窗口清理逻辑
- **Flink State**：使用 `ValueState` 存储处理计数和开始时间

**解决的问题**：
- **实时统计计算**：通过滑动窗口实现时间窗口内的实时统计
- **状态管理**：Flink State 保证了状态的一致性和可恢复性（虽然 Local Environment 模式下未启用 Checkpoint）

---

### 4.4 滤波算法层

#### 4.4.1 Kalman1DFilter

**类路径**：`cn.iocoder.yudao.module.monitor.filter.Kalman1DFilter`

**职责**：
- 实现标准的一维卡尔曼滤波算法
- 支持可配置的过程噪声 Q、观测噪声 R、初始协方差 P0

**关键技术**：
- **卡尔曼滤波算法**：预测-更新两步骤
- **初始化策略**：取前 N 点均值作为初始状态

**算法原理**：
```
预测：x = x
     P = P + Q
更新：K = P / (P + R)  (卡尔曼增益)
     x = x + K * (z - x)
     P = (1 - K) * P
```

**解决的问题**：
- **噪声抑制**：通过卡尔曼滤波有效抑制测量噪声
- **自适应调整**：根据观测误差动态调整估计值

---

### 4.5 WebSocket 推送层

#### 4.5.1 MonitorResultHub

**类路径**：`cn.iocoder.yudao.module.monitor.service.MonitorResultHub`

**职责**：
- 管理 WebSocket 会话与任务的映射关系
- 广播检测结果给所有订阅的客户端

**关键技术**：
- **ConcurrentHashMap**：线程安全的 Map，支持并发访问
- **Set 集合**：使用 `Collections.newSetFromMap()` 创建线程安全的 Set
- **JSON 序列化**：使用 `JsonUtils` 序列化消息

**解决的问题**：
- **多客户端支持**：支持多个客户端订阅同一任务
- **线程安全**：使用并发集合保证线程安全

---

#### 4.5.2 MonitorWebSocketHandler

**类路径**：`cn.iocoder.yudao.module.monitor.config.MonitorWebSocketHandler`

**职责**：
- 处理 WebSocket 连接的建立和关闭
- 从 URI 参数中提取 `jobId` 并注册会话

**关键技术**：
- **Spring WebSocket**：使用 `TextWebSocketHandler` 处理 WebSocket 消息
- **URI 解析**：使用 `UriComponentsBuilder` 解析查询参数

**解决的问题**：
- **连接管理**：自动管理连接的注册和注销
- **参数验证**：确保连接时提供有效的 `jobId`

---

### 4.6 离线分析层

#### 4.6.1 HistoryAnalysisServiceImpl

**类路径**：`cn.iocoder.yudao.module.monitor.service.impl.HistoryAnalysisServiceImpl`

**职责**：
- 对 TDMS 文件进行离线分析
- 支持多文件合并分析
- 返回完整的分析结果（包含所有采样点）

**关键技术**：
- **内存处理**：直接在 JVM 内存中处理数据，不使用 Flink
- **下采样**：限制输出点数（最多 50,000 点），避免 JSON 过大
- **百分位数计算**：使用 Apache Commons Math3 计算 95% 分位数作为阈值基准

**解决的问题**：
- **批量分析**：支持一次性分析多个文件，合并结果
- **性能优化**：通过下采样和异常点保留策略，平衡数据完整性和性能

**关键代码**：
```java
// 自适应下采样
int downSampleStep = Math.max(1, n / MAX_OUTPUT_POINTS);

// 阈值计算：95% 分位数 * factor
Percentile percentile = new Percentile(95.0);
double base = percentile.evaluate(energyArray);
double threshold = base * thresholdFactor;

// 输出策略：均匀下采样 + 异常点保留
boolean shouldEmit = (i % downSampleStep == 0) || isAnomaly;
```

---

## 5. 数据模型与持久化

### 5.1 实体类 (Entity/DTO)

#### 5.1.1 TdmsSample

**类路径**：`cn.iocoder.yudao.module.monitor.api.dto.TdmsSample`

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

#### 5.1.2 TdmsChannelMetadata

**类路径**：`cn.iocoder.yudao.module.monitor.api.dto.TdmsChannelMetadata`

**字段说明**：
```java
public class TdmsChannelMetadata implements Serializable {
    private String name;              // 通道名称
    private String unit;              // 单位
    private Double sampleRate;       // 采样率（Hz）
    private Long startTimestamp;     // 起始时间戳（毫秒）
    private Long endTimestamp;       // 结束时间戳（毫秒）
    private Long sampleCount;        // 样本数量
}
```

**作用**：表示 TDMS 通道的元数据信息。

---

#### 5.1.3 MonitorStreamMessage

**类路径**：`cn.iocoder.yudao.module.monitor.api.dto.MonitorStreamMessage`

**字段说明**：
```java
public class MonitorStreamMessage implements Serializable {
    private String jobId;            // 任务 ID
    private long timestamp;          // 时间戳
    private double originalValue;    // 原始值
    private double filteredValue;   // 滤波后的值
    private boolean anomaly;        // 是否异常
    private double energy;          // 能量值（残差能量）
    private double snrBeforeDb;     // 滤波前 SNR (dB)
    private double snrAfterDb;      // 滤波后 SNR (dB)
    private double snrDeltaDb;      // SNR 改善量 (dB)
    private double throughputKps;   // 吞吐量 (千样本/秒)
    private double processingDelayMs; // 处理延迟（毫秒）
    private long anomalyCount;      // 异常计数
    private TdmsChannelMetadata channel; // 通道元数据
}
```

**作用**：表示推送到前端的实时处理结果，包含原始值、滤波值、异常状态和统计信息。

**序列化要求**：
- 必须实现 `Serializable` 接口
- 提供无参构造函数（`@NoArgsConstructor`）
- 所有字段有 getter/setter（`@Data`）
- 避免使用不可变集合

---

#### 5.1.4 HistoryAnalysisResult

**类路径**：`cn.iocoder.yudao.module.monitor.api.dto.HistoryAnalysisResult`

**字段说明**：
```java
public class HistoryAnalysisResult implements Serializable {
    private TdmsChannelMetadata channel;  // 通道元数据
    private List<Point> points;           // 采样点列表
    private long anomalyCount;            // 异常点数量
    
    @Data
    public static class Point implements Serializable {
        private double timestamp;         // 相对时间戳（秒）
        private double rawValue;         // 原始值
        private double filteredValue;    // 滤波后的值
        private double residualValue;    // 残差值
        private boolean isAnomaly;       // 是否异常
        private String anomalyType;      // 异常类型
        private String channelName;      // 通道名称
    }
}
```

**作用**：表示离线历史分析的结果，包含所有采样点和异常统计信息。

---

#### 5.1.5 FilterConfig

**类路径**：`cn.iocoder.yudao.module.monitor.api.dto.FilterConfig`

**字段说明**：
```java
public class FilterConfig implements Serializable {
    private FilterType type = FilterType.KALMAN;  // 滤波器类型
    private double kalmanQ = 1e-5;                // 过程噪声协方差 Q
    private double kalmanR = 0.1;                  // 观测噪声协方差 R
    private double kalmanP0 = 1.0;                // 初始估计误差协方差 P0
    private int kalmanX0N = 10;                   // 初始状态取前 N 点均值
}
```

**作用**：配置滤波算法的参数，支持 Kalman 和 LMS 两种算法。

---

### 5.2 数据表结构

**注意**：`yudao-module-monitor` 模块**当前版本不进行数据持久化**，所有数据都在内存中处理。原因：
1. **实时回放场景**：数据来自 TDMS 文件，处理完成后不需要持久化
2. **离线分析场景**：分析结果直接返回给前端，不存储到数据库
3. **简化架构**：避免引入数据库依赖，降低系统复杂度

**未来扩展**：如果需要持久化功能，可以考虑：
- 使用 TDengine 存储检测结果（参考 `yudao-module-detection` 模块）
- 使用 MySQL 存储任务元数据和配置信息
- 使用 Redis 缓存热点数据

---

### 5.3 数据访问层 (Mapper)

该模块**未使用 MyBatis Mapper**，原因：
1. **无数据库操作**：当前版本不进行数据持久化
2. **内存处理**：所有数据都在内存中处理，无需数据库查询

**未来扩展**：如果需要数据库功能，可以：
- 创建对应的 Mapper 接口使用 MyBatis-Plus
- 或使用 TDengine JDBC Driver 直接查询时序数据

---

## 6. 配置与依赖

### 6.1 主要配置项

#### 6.1.1 application.yaml

**文件路径**：`yudao-module-monitor-server/src/main/resources/application.yaml`

**关键配置**：
```yaml
server:
  port: 48090  # HTTP 服务端口

spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 200MB      # 最大文件大小
      max-request-size: 200MB  # 最大请求大小

yudao:
  websocket:
    enable: true
    path: /monitor/ws          # WebSocket 路径
    sender-type: local
```

**配置说明**：
- **max-file-size**：支持上传最大 200MB 的 TDMS 文件
- **websocket.path**：WebSocket 连接路径，客户端通过 `/admin-api/monitor/ws?jobId=xxx` 连接

---

#### 6.1.2 application-local.yaml

**文件路径**：`yudao-module-monitor-server/src/main/resources/application-local.yaml`

**关键配置**：
```yaml
spring:
  cloud:
    nacos:
      server-addr: 127.0.0.1:8848  # Nacos 服务器地址
      discovery:
        namespace: local
      config:
        namespace: local

spring:
  datasource:
    dynamic:
      primary: master
      datasource:
        master:
          url: jdbc:mysql://127.0.0.1:13306/ruoyi-vue-pro
          username: root
          password: 20041102
```

**配置说明**：
- **Nacos**：服务注册与发现（虽然当前版本未使用服务间调用）
- **数据库**：虽然模块不进行数据持久化，但配置了数据源以支持框架要求

---

### 6.2 外部依赖

#### 6.2.1 核心框架依赖

| 依赖 | 版本 | 作用 |
|------|------|------|
| Spring Boot | 2.7.18 | 基础框架 |
| Spring Cloud Alibaba | 2021.0.4.0 | 微服务框架 |
| Apache Flink | 1.18.1 | 流处理引擎 |
| Reactor Core | - | 响应式流支持（Flux） |
| Apache Commons Math3 | 3.6.1 | 百分位数计算 |
| Commons IO | 2.16.1 | 文件操作工具 |

---

#### 6.2.2 外部服务依赖

1. **Python 环境**
   - **作用**：执行 TDMS 文件解析脚本
   - **依赖库**：`nptdms`、`numpy`（Python 脚本中使用）
   - **使用场景**：`TdmsParsingServiceImpl` 调用 Python 进程解析文件

2. **Nacos 注册中心**
   - **作用**：服务注册与发现
   - **配置**：`spring.cloud.nacos.discovery.server-addr: 127.0.0.1:8848`
   - **使用场景**：微服务间调用时通过 Nacos 发现服务地址（当前版本未使用）

---

#### 6.2.3 模块间依赖

1. **yudao-module-monitor-api**
   - **作用**：定义 DTO 和接口
   - **依赖关系**：biz 模块依赖 api 模块

2. **yudao-module-system-api**
   - **作用**：系统模块 API（用户、权限等）
   - **使用场景**：可能用于权限验证（当前版本未使用）

3. **yudao-spring-boot-starter-web**
   - **作用**：提供 HTTP 接口支持
   - **使用场景**：`MonitorUploadController` 提供 RESTful 接口

4. **yudao-spring-boot-starter-websocket**
   - **作用**：提供 WebSocket 支持
   - **使用场景**：`MonitorWebSocketHandler` 处理 WebSocket 连接

---

## 7. 总结

### 7.1 设计优点

1. **回放式流处理**
   - **时序保持**：按照原始时间间隔回放数据，保持数据的时序特性
   - **实时模拟**：模拟真实的数据采集场景，便于前端实时可视化
   - **灵活控制**：支持暂停、恢复、停止等操作

2. **双模式支持**
   - **实时回放**：使用 Flink 流处理，通过 WebSocket 实时推送
   - **离线分析**：直接在内存中处理，返回完整结果
   - **流式响应**：使用 Reactor Flux 支持大结果集的流式传输

3. **可扩展性**
   - **策略模式**：滤波算法易于扩展，新增算法只需实现接口
   - **配置化**：滤波参数和异常检测阈值可配置
   - **多客户端支持**：支持多个客户端订阅同一任务

4. **健壮性**
   - **资源管理**：临时文件自动清理，防止磁盘空间耗尽
   - **异常处理**：完善的异常捕获和日志记录
   - **序列化兼容**：正确处理 Flink 序列化与 Spring Bean 的集成问题

5. **性能优化**
   - **下采样**：离线分析时限制输出点数，避免 JSON 过大
   - **异常点保留**：下采样时保留所有异常点，保证告警信息不丢失
   - **异步执行**：Flink 作业异步执行，不阻塞 HTTP 请求

---

### 7.2 未来扩展方向

1. **数据持久化**
   - 集成 TDengine 存储检测结果，支持历史数据查询
   - 使用 MySQL 存储任务元数据和配置信息
   - 使用 Redis 缓存热点数据

2. **更多滤波算法**
   - 支持更多滤波算法（LMS、NLMS、RLS、小波等）
   - 支持算法组合和自适应选择

3. **分布式 Flink 集群**
   - 迁移到 Flink 集群模式，支持更大规模的数据处理
   - 支持 Checkpoint 和 Savepoint，实现故障恢复
   - 支持动态扩缩容，应对流量波动

4. **实时数据源支持**
   - 支持从 Kafka、Pulsar 等消息队列读取实时数据
   - 支持直接从工业设备采集数据（Modbus、OPC UA 等协议）

5. **机器学习集成**
   - 集成 TensorFlow/PyTorch 模型进行异常检测
   - 支持模型在线训练和更新
   - 实现自适应阈值调整

6. **可视化增强**
   - 提供更丰富的实时数据可视化图表
   - 支持历史数据回放和分析
   - 提供异常事件关联分析

7. **性能优化**
   - 使用 Flink 的异步 I/O 提升数据库写入性能
   - 优化 Python 进程调用，考虑使用进程池复用
   - 支持数据压缩和批量传输

8. **监控与运维**
   - 集成 Prometheus + Grafana 进行指标监控
   - 提供详细的性能指标（吞吐量、延迟、错误率）
   - 支持分布式链路追踪（Jaeger/Zipkin）

---

## 附录：关键代码位置索引

| 组件 | 类路径 | 文件路径 |
|------|--------|----------|
| HTTP 控制器 | `MonitorUploadController` | `yudao-module-monitor-biz/src/main/java/cn/iocoder/yudao/module/monitor/controller/admin/MonitorUploadController.java` |
| TDMS 解析服务 | `TdmsParsingServiceImpl` | `yudao-module-monitor-biz/src/main/java/cn/iocoder/yudao/module/monitor/service/impl/TdmsParsingServiceImpl.java` |
| Flink 回放服务 | `FlinkPlaybackServiceImpl` | `yudao-module-monitor-biz/src/main/java/cn/iocoder/yudao/module/monitor/service/impl/FlinkPlaybackServiceImpl.java` |
| 历史分析服务 | `HistoryAnalysisServiceImpl` | `yudao-module-monitor-biz/src/main/java/cn/iocoder/yudao/module/monitor/service/impl/HistoryAnalysisServiceImpl.java` |
| Flink 回放源 | `TdmsReplaySource` | `yudao-module-monitor-biz/src/main/java/cn/iocoder/yudao/module/monitor/flink/TdmsReplaySource.java` |
| 信号处理函数 | `SignalProcessFunction` | `yudao-module-monitor-biz/src/main/java/cn/iocoder/yudao/module/monitor/flink/SignalProcessFunction.java` |
| Flink 结果 Sink | `MonitorResultSink` | `yudao-module-monitor-biz/src/main/java/cn/iocoder/yudao/module/monitor/flink/MonitorResultSink.java` |
| 结果分发中心 | `MonitorResultHub` | `yudao-module-monitor-biz/src/main/java/cn/iocoder/yudao/module/monitor/service/MonitorResultHub.java` |
| WebSocket 处理器 | `MonitorWebSocketHandler` | `yudao-module-monitor-biz/src/main/java/cn/iocoder/yudao/module/monitor/config/MonitorWebSocketHandler.java` |
| 卡尔曼滤波器 | `Kalman1DFilter` | `yudao-module-monitor-biz/src/main/java/cn/iocoder/yudao/module/monitor/filter/Kalman1DFilter.java` |
| 配置文件 | `application.yaml` | `yudao-module-monitor-server/src/main/resources/application.yaml` |

---

**文档版本**：v1.0  
**最后更新**：2024年  
**维护者**：yudao 开发团队
