"""
滤波网关服务 - 调用盛老师的滤波微服务 API
支持 Kalman、RLS、LS 三种滤波算法
"""

import os
import json
import time
import asyncio
import aiohttp
from typing import List, Dict, Any, Optional
from dataclasses import dataclass
from enum import Enum

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from kafka import KafkaConsumer, KafkaProducer
import uvicorn


# ============== 配置 ==============

class FilterType(str, Enum):
    KALMAN = "kalman"
    RLS = "rls"
    LS = "ls"


# 滤波服务地址
FILTER_SERVICES = {
    FilterType.KALMAN: os.getenv("KALMAN_SERVICE_URL", "http://49.235.44.231:8000"),
    FilterType.RLS: os.getenv("RLS_SERVICE_URL", "http://49.235.44.231:8001"),
    FilterType.LS: os.getenv("LS_SERVICE_URL", "http://49.235.44.231:8002"),
}

KAFKA_BROKERS = os.getenv("KAFKA_BROKERS", "localhost:9092")
INPUT_TOPIC = os.getenv("INPUT_TOPIC", "sample-input")
OUTPUT_TOPIC = os.getenv("OUTPUT_TOPIC", "sample-output")


# ============== 数据模型 ==============

class FilterRequest(BaseModel):
    signal: List[float]
    filter_type: FilterType = FilterType.KALMAN
    # Kalman 参数
    model: str = "level"
    process_noise_var: float = 1e-3
    measurement_noise_var: float = 1e-2
    # RLS 参数
    forgetting_factor: float = 0.99
    delta: float = 1000.0
    # LS 参数
    ridge_alpha: float = 0.0
    dt: float = 1.0


class FilterResponse(BaseModel):
    original_signal: List[float]
    filtered_signal: List[float]
    filter_type: str
    processing_time_ms: float
    sample_count: int


class BatchFilterRequest(BaseModel):
    signals: List[List[float]]
    filter_type: FilterType = FilterType.KALMAN


# ============== 滤波客户端 ==============

class FilterClient:
    """异步滤波服务客户端"""
    
    def __init__(self):
        self.session: Optional[aiohttp.ClientSession] = None
        self.stats = {
            "total_requests": 0,
            "total_samples": 0,
            "total_time_ms": 0,
            "errors": 0,
        }
    
    async def init(self):
        timeout = aiohttp.ClientTimeout(total=30)
        self.session = aiohttp.ClientSession(timeout=timeout)
    
    async def close(self):
        if self.session:
            await self.session.close()
    
    async def filter_signal(
        self,
        signal: List[float],
        filter_type: FilterType,
        **params
    ) -> Dict[str, Any]:
        """调用滤波服务"""
        if not self.session:
            await self.init()
        
        base_url = FILTER_SERVICES[filter_type]
        
        start_time = time.perf_counter()
        
        try:
            if filter_type == FilterType.KALMAN:
                url = f"{base_url}/kalman/audio/run"
                payload = {
                    "signal": signal,
                    "model": params.get("model", "level"),
                    "process_noise_var": params.get("process_noise_var", 1e-3),
                    "measurement_noise_var": params.get("measurement_noise_var", 1e-2),
                }
            elif filter_type == FilterType.RLS:
                url = f"{base_url}/rls/audio/run"
                payload = {
                    "signal": signal,
                    "model": params.get("model", "level"),
                    "forgetting_factor": params.get("forgetting_factor", 0.99),
                    "delta": params.get("delta", 1000.0),
                }
            elif filter_type == FilterType.LS:
                url = f"{base_url}/ls/audio/run"
                payload = {
                    "signal": signal,
                    "model": params.get("model", "level"),
                    "ridge_alpha": params.get("ridge_alpha", 0.0),
                    "dt": params.get("dt", 1.0),
                }
            else:
                raise ValueError(f"Unknown filter type: {filter_type}")
            
            async with self.session.post(url, json=payload) as resp:
                if resp.status != 200:
                    error_text = await resp.text()
                    raise HTTPException(status_code=resp.status, detail=error_text)
                
                result = await resp.json()
                
                elapsed_ms = (time.perf_counter() - start_time) * 1000
                
                # 更新统计
                self.stats["total_requests"] += 1
                self.stats["total_samples"] += len(signal)
                self.stats["total_time_ms"] += elapsed_ms
                
                return {
                    "filtered_signal": result.get("filtered_signal", []),
                    "states": result.get("states"),
                    "weights": result.get("weights"),
                    "covariances": result.get("covariances"),
                    "processing_time_ms": elapsed_ms,
                }
                
        except aiohttp.ClientError as e:
            self.stats["errors"] += 1
            raise HTTPException(status_code=503, detail=f"Filter service unavailable: {e}")
    
    async def filter_multichannel(
        self,
        signals: List[List[float]],
        filter_type: FilterType,
        **params
    ) -> Dict[str, Any]:
        """多通道滤波"""
        if not self.session:
            await self.init()
        
        base_url = FILTER_SERVICES[filter_type]
        
        start_time = time.perf_counter()
        
        try:
            if filter_type == FilterType.KALMAN:
                url = f"{base_url}/kalman/audio/run_multichannel"
            elif filter_type == FilterType.RLS:
                url = f"{base_url}/rls/audio/run_multichannel"
            elif filter_type == FilterType.LS:
                url = f"{base_url}/ls/audio/run_multichannel"
            else:
                raise ValueError(f"Unknown filter type: {filter_type}")
            
            payload = {
                "signals": signals,
                "model": params.get("model", "level"),
            }
            
            if filter_type == FilterType.KALMAN:
                payload["process_noise_var"] = params.get("process_noise_var", 1e-3)
                payload["measurement_noise_var"] = params.get("measurement_noise_var", 1e-2)
            elif filter_type == FilterType.RLS:
                payload["forgetting_factor"] = params.get("forgetting_factor", 0.99)
                payload["delta"] = params.get("delta", 1000.0)
            elif filter_type == FilterType.LS:
                payload["ridge_alpha"] = params.get("ridge_alpha", 0.0)
            
            async with self.session.post(url, json=payload) as resp:
                if resp.status != 200:
                    error_text = await resp.text()
                    raise HTTPException(status_code=resp.status, detail=error_text)
                
                result = await resp.json()
                elapsed_ms = (time.perf_counter() - start_time) * 1000
                
                return {
                    "filtered_signals": result.get("filtered_signals", []),
                    "processing_time_ms": elapsed_ms,
                }
                
        except aiohttp.ClientError as e:
            self.stats["errors"] += 1
            raise HTTPException(status_code=503, detail=f"Filter service unavailable: {e}")


# ============== FastAPI 应用 ==============

app = FastAPI(
    title="滤波网关服务",
    description="集成 Kalman/RLS/LS 滤波微服务的统一网关",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

filter_client = FilterClient()


@app.on_event("startup")
async def startup():
    await filter_client.init()
    print("滤波网关服务已启动")
    print(f"Kalman 服务: {FILTER_SERVICES[FilterType.KALMAN]}")
    print(f"RLS 服务: {FILTER_SERVICES[FilterType.RLS]}")
    print(f"LS 服务: {FILTER_SERVICES[FilterType.LS]}")


@app.on_event("shutdown")
async def shutdown():
    await filter_client.close()


@app.get("/health")
async def health():
    return {"status": "healthy", "stats": filter_client.stats}


@app.post("/filter", response_model=FilterResponse)
async def filter_signal(request: FilterRequest):
    """单通道滤波"""
    result = await filter_client.filter_signal(
        signal=request.signal,
        filter_type=request.filter_type,
        model=request.model,
        process_noise_var=request.process_noise_var,
        measurement_noise_var=request.measurement_noise_var,
        forgetting_factor=request.forgetting_factor,
        delta=request.delta,
        ridge_alpha=request.ridge_alpha,
        dt=request.dt,
    )
    
    return FilterResponse(
        original_signal=request.signal,
        filtered_signal=result["filtered_signal"],
        filter_type=request.filter_type.value,
        processing_time_ms=result["processing_time_ms"],
        sample_count=len(request.signal),
    )


@app.post("/filter/batch")
async def filter_batch(request: BatchFilterRequest):
    """批量多通道滤波"""
    result = await filter_client.filter_multichannel(
        signals=request.signals,
        filter_type=request.filter_type,
    )
    
    return {
        "filtered_signals": result["filtered_signals"],
        "filter_type": request.filter_type.value,
        "processing_time_ms": result["processing_time_ms"],
        "channel_count": len(request.signals),
    }


@app.get("/stats")
async def get_stats():
    """获取统计信息"""
    stats = filter_client.stats.copy()
    if stats["total_requests"] > 0:
        stats["avg_time_ms"] = stats["total_time_ms"] / stats["total_requests"]
        stats["avg_samples_per_request"] = stats["total_samples"] / stats["total_requests"]
    return stats


# ============== Kafka 流处理 ==============

class KafkaFilterProcessor:
    """Kafka 流处理器 - 从 Kafka 读取数据，调用滤波服务，写回 Kafka"""
    
    def __init__(self, filter_client: FilterClient):
        self.filter_client = filter_client
        self.running = False
        self.consumer = None
        self.producer = None
        self.filter_type = FilterType(os.getenv("FILTER_TYPE", "kalman"))
    
    def connect(self):
        brokers = KAFKA_BROKERS.split(",")
        
        self.consumer = KafkaConsumer(
            INPUT_TOPIC,
            bootstrap_servers=brokers,
            group_id=f"filter-gateway-{int(time.time())}",
            auto_offset_reset="latest",
            value_deserializer=lambda m: json.loads(m.decode("utf-8")),
        )
        
        self.producer = KafkaProducer(
            bootstrap_servers=brokers,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        )
        
        print(f"Kafka 连接成功: {INPUT_TOPIC} -> {OUTPUT_TOPIC}")
    
    async def process_message(self, data: Dict) -> Dict:
        """处理单条消息"""
        samples = data.get("samples", [])
        if not samples:
            return None
        
        result = await self.filter_client.filter_signal(
            signal=samples,
            filter_type=self.filter_type,
        )
        
        worker_id = os.getenv("WORKER_ID", f"filter-worker-{int(time.time()) % 3}")
        
        return {
            "type": "signal-data",
            "deviceId": data.get("deviceId", data.get("source", "unknown")),
            "timestamp": data.get("timestamp", int(time.time() * 1000)),
            "dispatch_time": data.get("timestamp", int(time.time() * 1000)),
            "processedAt": int(time.time() * 1000),
            "sampleRate": data.get("sampleRate", 50000),
            "originalSamples": samples[:500],  # 前端需要的字段
            "filteredSamples": result["filtered_signal"][:500],  # 前端需要的字段
            "original_samples": samples[:500],  # 兼容下划线命名
            "filtered_samples": result["filtered_signal"][:500],
            "sampleCount": len(samples),
            "sample_count": len(samples),
            "processingTimeMs": result["processing_time_ms"],
            "filterType": self.filter_type.value.upper(),
            "worker_id": worker_id,
            "sequence_id": data.get("index", 0),
        }
    
    async def run(self):
        """运行流处理"""
        self.connect()
        self.running = True
        
        print(f"开始处理 Kafka 流，滤波类型: {self.filter_type.value}")
        
        loop = asyncio.get_event_loop()
        
        while self.running:
            # 在线程池中执行阻塞的 poll
            messages = await loop.run_in_executor(
                None,
                lambda: self.consumer.poll(timeout_ms=100, max_records=10)
            )
            
            if not messages:
                await asyncio.sleep(0.01)
                continue
            
            for tp, records in messages.items():
                for msg in records:
                    try:
                        output = await self.process_message(msg.value)
                        if output:
                            self.producer.send(OUTPUT_TOPIC, value=output)
                    except Exception as e:
                        print(f"处理消息失败: {e}")
    
    def stop(self):
        self.running = False
        if self.producer:
            self.producer.flush()
            self.producer.close()
        if self.consumer:
            self.consumer.close()


# ============== 主入口 ==============

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["api", "kafka"], default="api")
    parser.add_argument("--port", type=int, default=8010)
    args = parser.parse_args()
    
    print(f"========================================")
    print(f"滤波网关服务启动")
    print(f"模式: {args.mode}")
    print(f"Kafka: {KAFKA_BROKERS}")
    print(f"输入Topic: {INPUT_TOPIC}")
    print(f"输出Topic: {OUTPUT_TOPIC}")
    print(f"========================================")
    
    if args.mode == "api":
        # API 模式
        uvicorn.run(app, host="0.0.0.0", port=args.port)
    else:
        # Kafka 流处理模式
        processor = KafkaFilterProcessor(filter_client)
        
        async def main():
            print("初始化滤波客户端...")
            await filter_client.init()
            print("开始 Kafka 流处理...")
            await processor.run()
        
        try:
            asyncio.run(main())
        except KeyboardInterrupt:
            print("收到停止信号")
            processor.stop()
        except Exception as e:
            print(f"启动失败: {e}")
            import traceback
            traceback.print_exc()
