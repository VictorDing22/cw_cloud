# 启动脚本

## 核心脚本（推荐使用）

| 脚本 | 功能 |
|------|------|
| `start.bat` | 一键启动所有服务 |
| `stop.bat` | 一键停止所有服务 |
| `status.bat` | 检查系统状态 |

## 使用方法

```bash
# 启动系统
scripts\start.bat

# 停止系统
scripts\stop.bat

# 检查状态
scripts\status.bat
```

## 启动顺序

1. 基础设施：MySQL → Redis → Nacos → Kafka
2. 后端服务：Gateway → System → Infra
3. 数据处理：Backend滤波 → TDMS API
4. 前端服务：Vue3

## 端口说明

| 服务 | 端口 |
|------|------|
| MySQL | 3306 |
| Redis | 6379 |
| Nacos | 8848 |
| Kafka | 9092 |
| Gateway | 48080 |
| System | 48081 |
| Infra | 48082 |
| 前端 | 80 |

## 故障排查

1. 运行 `status.bat` 查看哪些服务未启动
2. 检查对应服务的日志窗口
3. 确保端口未被占用
4. 重新运行 `start.bat`
