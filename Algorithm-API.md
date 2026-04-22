# 1、卡尔曼滤波微服务接口调用示例
示例
- 基础地址：`http://49.235.44.231:8000`
## 单通道信号
- 路径：`POST /kalman/audio/run`
- 入参字段：
  - `signal`: `[float]` 声音样本序列
  - `model`: `"level" | "constant_velocity"`（默认 `level`）
  - `dt`: 采样周期（秒），默认 `1.0`
  - `process_noise_var`: 过程噪声方差（默认 `1e-3`）
  - `measurement_noise_var`: 观测噪声方差（默认 `1e-2`）
- 返回字段：`filtered_signal`、`states`、`covariances`

示例（curl）：
```bash
curl -X POST http://localhost:8000/kalman/audio/run \
  -H "Content-Type: application/json" \
  -d '{
    "signal": [0.2, 0.9, -0.8, 0.7, -0.6],
    "model": "level",
    "process_noise_var": 1e-4,
    "measurement_noise_var": 1e-2
  }'
```
## 多通道信号
- 路径：`POST /kalman/audio/run_multichannel`
- 入参字段：
  - `signals`: `[[float]]` 形状 `[T, C]`，每列为一个通道
  - 其它参数同单通道端点
- 返回字段：`filtered_signals`（二维数组，每个子数组为对应通道的滤波结果）

示例（curl）：
```bash
curl -X POST http://localhost:8000/kalman/audio/run_multichannel \
  -H "Content-Type: application/json" \
  -d '{
    "signals": [[0.1,0.2,0.3,0.4],[0.05,0.1,0.2,0.3]],
    "model": "level",
    "process_noise_var": 1e-4,
    "measurement_noise_var": 1e-2
  }'
```
---
# RLS（递归最小二乘）接口调用示例

## 单通道信号（RLS）
- 路径：`POST /rls/audio/run`
- 入参字段：
  - `signal`: `[float]` 声音样本序列
  - `model`: `"level" | "trend"`（默认 `level`）
  - `dt`: 采样周期（秒），仅 `trend` 模型生效，默认 `1.0`
  - `forgetting_factor`: `(0,1]`，默认 `0.99`
  - `delta`: 初始协方差放大系数（`P0 = delta·I`），默认 `1000.0`
  - 可选：`x0`, `P0`
- 返回字段：`filtered_signal`、`weights`、`covariances`

示例（curl）：
```bash
curl -X POST http://49.235.44.231:8001/rls/audio/run \
  -H "Content-Type: application/json" \
  -d '{
    "signal": [0.2, 0.9, -0.8, 0.7, -0.6],
    "model": "level",
    "forgetting_factor": 0.99,
    "delta": 1000.0
  }'
```

## 多通道信号（RLS）
- 路径：`POST /rls/audio/run_multichannel`
- 入参字段：
  - `signals`: `[[float]]` 形状 `[T, C]`，每列为一个通道
  - 其它参数同单通道端点
- 返回字段：`filtered_signals`（二维数组，每个子数组为对应通道的滤波结果）

示例（curl）：
```bash
curl -X POST http://49.235.44.231:8001/rls/audio/run_multichannel \
  -H "Content-Type: application/json" \
  -d '{
    "signals": [[0.1,0.2,0.3,0.4],[0.05,0.1,0.2,0.3]],
    "model": "level",
    "forgetting_factor": 0.99,
    "delta": 1000.0
  }'
```
---
# LS（最小二乘）接口调用示例
## 单通道信号（LS）
- 路径：`POST /ls/audio/run`
- 入参字段：
  - `signal`: `[float]` 声音样本序列
  - `model`: `"level" | "trend"`（默认 `level`）
  - `dt`: 采样周期（秒），仅 `trend` 模型生效，默认 `1.0`
  - `ridge_alpha`: `>=0`，岭回归系数（默认 `0.0`）
- 返回字段：`filtered_signal`、`weights`

示例（curl）：
```bash
curl -X POST http://49.235.44.231:8002/ls/audio/run \
  -H "Content-Type: application/json" \
  -d '{
    "signal": [0.2, 0.9, -0.8, 0.7, -0.6],
    "model": "trend",
    "dt": 0.001,
    "ridge_alpha": 0.0
  }'
```
## 多通道信号（LS）
- 路径：`POST /ls/audio/run_multichannel`
- 入参字段：
  - `signals`: `[[float]]` 形状 `[T, C]`，每列为一个通道
  - 其它参数同单通道端点
- 返回字段：`filtered_signals`（二维数组，每个子数组为对应通道的滤波结果）

示例（curl）：
```bash
curl -X POST http://49.235.44.231:8002/ls/audio/run_multichannel \
  -H "Content-Type: application/json" \
  -d '{
    "signals": [[0.1,0.2,0.3,0.4],[0.05,0.1,0.2,0.3]],
    "model": "level",
    "ridge_alpha": 0.0
  }'
```

