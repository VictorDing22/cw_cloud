"""
本地 Kalman 滤波服务 — 兼容盛老师 API 接口
POST /kalman/audio/run

性能关键路径: orjson 替代 stdlib json，避免 Pydantic 序列化开销
"""

import numpy as np
import orjson
from fastapi import FastAPI, Request
from fastapi.responses import Response

app = FastAPI(title="Kalman Filter Service")


def kalman_filter(signal: np.ndarray, q: float, r: float) -> np.ndarray:
    n = len(signal)
    out = np.empty(n, dtype=np.float64)
    x = signal[0]
    p = 1.0
    out[0] = x
    for k in range(1, n):
        p_pred = p + q
        kg = p_pred / (p_pred + r)
        x = x + kg * (signal[k] - x)
        p = (1.0 - kg) * p_pred
        out[k] = x
    return out


@app.post("/kalman/audio/run")
async def run_kalman(request: Request):
    body = await request.body()
    req = orjson.loads(body)
    sig = np.asarray(req["signal"], dtype=np.float64)
    q = req.get("process_noise_var", 1e-3)
    r = req.get("measurement_noise_var", 1e-2)
    filtered = kalman_filter(sig, q, r)
    return Response(
        content=orjson.dumps({"filtered_signal": filtered.tolist()}),
        media_type="application/json",
    )


@app.post("/filter")
async def filter_compat(request: Request):
    """兼容 filter-gateway 的 /filter 接口，让 Flink 可直接调用"""
    body = await request.body()
    req = orjson.loads(body)
    sig = np.asarray(req["signal"], dtype=np.float64)
    q = req.get("process_noise_var", 1e-3)
    r = req.get("measurement_noise_var", 1e-2)
    filtered = kalman_filter(sig, q, r)
    return Response(
        content=orjson.dumps({
            "original_signal": req["signal"],
            "filtered_signal": filtered.tolist(),
            "filter_type": req.get("filter_type", "kalman"),
            "processing_time_ms": 0,
            "sample_count": len(req["signal"]),
        }),
        media_type="application/json",
    )


@app.get("/health")
def health():
    return {"status": "healthy"}
