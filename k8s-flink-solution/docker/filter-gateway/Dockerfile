FROM python:3.10-slim

WORKDIR /app

# 使用阿里云镜像源加速
RUN pip config set global.index-url https://mirrors.aliyun.com/pypi/simple/ && \
    pip config set global.trusted-host mirrors.aliyun.com && \
    pip install --no-cache-dir --timeout 120 \
    fastapi \
    uvicorn \
    aiohttp \
    kafka-python \
    pydantic

COPY filter_gateway.py .

EXPOSE 8010

# 默认 API 模式
CMD ["python", "filter_gateway.py", "--mode", "api", "--port", "8010"]
