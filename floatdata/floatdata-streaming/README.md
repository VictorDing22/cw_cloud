# FloatData Streaming System

实时声发射信号处理与异常检测系统

## 快速开始

### 运行系统
`powershell
# 启动系统
.\start.ps1

# 监控消息
.\monitor.ps1

# 停止系统
.\stop.bat
`

### 高级用法
`powershell
# 使用真实 TDMS 数据
.\start.ps1 -DataSource tdms

# 使用壁画数据
.\start.ps1 -DataSource wallpainting

# 调试模式（显示窗口）
.\start.ps1 -ShowWindows
`

## 核心特性

- 高性能数据采集（Netty）
- 可靠消息队列（Kafka）
- 分布式流处理（Spark）
- 信号处理（Butterworth + FFT）
- 异常检测算法

## 性能指标

- 数据吞吐量: ~65,000 samples/sec
- 端到端延迟: ~2.1-2.3 seconds
- 内存占用: ~4.2 GB

## 文档

- [脚本使用指南](SCRIPTS_GUIDE.md) - 详细的脚本使用说明
- [系统架构](docs/ARCHITECTURE.md) - 架构设计文档
- [部署指南](docs/DEPLOYMENT_GUIDE.md) - 部署说明
- [优化总结](OPTIMIZATION_SUMMARY.md) - 优化记录

## 技术栈

- Java 23
- Netty 4.1
- Apache Kafka 3.6
- Apache Spark 3.4

## 许可证

MIT License
