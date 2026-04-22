# Flink性能优化方案 - 目标40MB/s

## 优化概述

在**必须使用Flink**的前提下，通过以下7个关键优化实现从10.60 MB/s提升到**40 MB/s**的目标：

## 核心优化策略

### 1. 提高Flink并行度 ⭐⭐⭐⭐⭐
**优化内容**：
- 自动检测CPU核心数，设置并行度为 `CPU核心数 - 1`
- 充分利用多核CPU，实现真正的并行处理

**代码实现**：
```java
int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
env.setParallelism(parallelism);
```

**预期提升**：2-4倍（取决于CPU核心数）

---

### 2. 优化Flink配置 ⭐⭐⭐⭐
**优化内容**：
- 增大网络缓冲区（64MB-128MB）
- 启用对象重用（减少对象创建开销）
- 禁用Kryo序列化，使用POJO序列化（更快）
- 设置缓冲区超时为0（批处理模式）

**代码实现**：
```java
Configuration flinkConfig = new Configuration();
flinkConfig.set(NettyShuffleEnvironmentOptions.NETWORK_BUFFER_MEMORY_MIN, MemorySize.parse("64mb"));
flinkConfig.set(NettyShuffleEnvironmentOptions.NETWORK_BUFFER_MEMORY_MAX, MemorySize.parse("128mb"));
flinkConfig.setBoolean(ExecutionOptions.OBJECT_REUSE, true);
env.getConfig().enableObjectReuse();
env.getConfig().disableForceKryo();
env.setBufferTimeout(0);
```

**预期提升**：1.3-1.5倍

---

### 3. 优化数据源 ⭐⭐⭐⭐
**优化内容**：
- 使用自定义`TdmsSampleSource`替代`fromCollection`
- 避免`fromCollection`的序列化开销
- 支持并行数据源（`ParallelSourceFunction`）
- 批量发送数据（每批2000个样本）

**代码实现**：
```java
DataStream<TdmsSample> stream = env.addSource(
    new TdmsSampleSource(data.getSamples()), 
    "tdms-source"
).setParallelism(parallelism);
```

**预期提升**：1.2-1.5倍

---

### 4. 使用Rebalance实现数据分片 ⭐⭐⭐⭐
**优化内容**：
- 使用`rebalance()`实现数据分片
- 支持多线程并行处理
- 保持数据负载均衡

**代码实现**：
```java
DataStream<FilterResult> resultStream = stream
    .rebalance() // 重新平衡数据，实现并行处理
    .process(new GenericFilterProcessFunction(...))
    .setParallelism(parallelism);
```

**预期提升**：2-4倍（取决于并行度）

---

### 5. 优化窗口清理算法 ⭐⭐⭐
**优化内容**：
- 批量清理过期数据，减少循环次数
- 降低清理阈值（从100降到50）
- 减少不必要的计算和检查

**代码实现**：
```java
// 批量清理，减少循环开销
if (window.size() > 50) {
    int removedCount = 0;
    while (!window.isEmpty() && window.peekFirst().timestamp < boundary) {
        // 批量清理逻辑
    }
}
```

**预期提升**：1.1-1.2倍

---

### 6. 优化SNR计算 ⭐⭐
**优化内容**：
- 使用乘法替代除法（缓存倒数）
- 减少重复计算
- 提前判断异常条件

**代码实现**：
```java
double invWindowSize = (windowSize > 0) ? 1.0 / windowSize : 0;
double signalPowerRaw = stats.sumOriginalSq * invWindowSize;
boolean anomaly = anomalyEnabled && windowSize > 0 && energy > anomalyThreshold;
```

**预期提升**：1.05-1.1倍

---

### 7. 优化吞吐量计算 ⭐
**优化内容**：
- 减少`System.currentTimeMillis()`调用频率
- 每1000个样本更新一次，而不是每个样本

**代码实现**：
```java
if (localProcessedCount % 1000 == 0) {
    long now = System.currentTimeMillis();
    // 计算吞吐量
}
```

**预期提升**：1.02-1.05倍

---

## 性能提升计算

### 理论提升
假设4核CPU（并行度=3）：
- 并行度提升：3倍
- Flink配置优化：1.4倍
- 数据源优化：1.3倍
- 窗口优化：1.1倍
- SNR优化：1.05倍
- 吞吐量优化：1.02倍

**总提升** = 3 × 1.4 × 1.3 × 1.1 × 1.05 × 1.02 ≈ **6.3倍**

### 实际预期
考虑到实际场景中的开销和瓶颈：
- **保守估计**：3-4倍 → **30-40 MB/s** ✅
- **理想情况**：4-5倍 → **40-50 MB/s** ✅

---

## 关键优化点说明

### 为什么并行度最重要？
1. **充分利用多核CPU**：单线程只能使用1个核心，多线程可以使用所有核心
2. **线性扩展**：理论上并行度=N，速度提升N倍
3. **Flink天然支持**：Flink的并行处理机制非常成熟

### 为什么需要rebalance？
- `keyBy`在单channel场景下不会真正分区
- `rebalance()`强制数据分片，确保并行处理
- 每个并行任务处理数据的一部分

### 为什么对象重用很重要？
- 减少GC压力：不创建新对象，减少垃圾回收
- 提升性能：对象创建和销毁有开销
- 批处理场景：不需要保持对象状态

---

## 使用建议

### 1. CPU核心数要求
- **最低**：2核（并行度=1，提升有限）
- **推荐**：4核以上（并行度=3，提升明显）
- **最佳**：8核以上（并行度=7，接近线性扩展）

### 2. 内存要求
- 网络缓冲区：64-128MB
- 每个并行任务：约100-200MB
- **总内存**：建议至少2GB可用内存

### 3. 监控指标
建议监控以下指标：
```java
log.info("Flink并行度: {}", parallelism);
log.info("处理耗时: {} ms", processEnd - parseEnd);
log.info("吞吐量: {} MB/s", throughput);
```

---

## 注意事项

### ⚠️ 时序处理
- 使用`rebalance()`会打乱数据顺序
- 如果算法**严格要求时序**，可能需要调整：
  - 方案A：降低并行度到2-3
  - 方案B：使用`keyBy` + 时间窗口
  - 方案C：在算法内部处理时序问题

### ⚠️ 状态管理
- 每个并行任务有独立的状态
- 窗口状态不会跨任务共享
- 异常计数需要合并（当前代码已处理）

### ⚠️ 内存使用
- 并行度越高，内存使用越多
- 如果内存不足，降低并行度

---

## 测试验证

### 测试步骤
1. 运行优化后的代码
2. 检查日志中的并行度设置
3. 对比优化前后的吞吐量
4. 监控CPU使用率（应该接近100%）

### 预期结果
- **CPU使用率**：从25% → 80-90%
- **处理速度**：从10.60 MB/s → 35-45 MB/s
- **内存使用**：增加20-30%（并行处理开销）

---

## 进一步优化方向

如果达到40MB/s后还需要更高性能：

1. **使用Flink批处理API**（DataSet API）
   - 更适合批处理场景
   - 性能可能进一步提升10-20%

2. **优化滤波算法本身**
   - 某些算法（如MedianFilter）可以进一步优化
   - 使用更高效的数值计算库

3. **使用Flink集群模式**
   - 分布式处理，支持更大规模
   - 需要Flink集群环境

4. **JVM调优**
   - 增大堆内存
   - 使用G1GC
   - 调整JVM参数

---

## 总结

通过以上7个优化，在**保留Flink架构**的前提下，预期可以实现：
- **处理速度**：从10.60 MB/s → **35-45 MB/s** ✅
- **CPU利用率**：从25% → 80-90%
- **代码改动**：最小化，保持架构不变

**关键成功因素**：
1. ✅ 提高并行度（最重要）
2. ✅ 优化Flink配置
3. ✅ 使用高效数据源
4. ✅ 数据分片并行处理

如果您的CPU核心数较多（8核以上），甚至可能超过50 MB/s！
