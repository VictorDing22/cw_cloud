# 分布式滤波服务 Docker镜像
FROM python:3.11-slim

WORKDIR /app

# 升级pip并安装依赖（包含lz4压缩支持）
RUN pip install --upgrade pip && \
    pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple \
    numpy \
    scipy \
    kafka-python \
    websockets \
    lz4

# 复制服务文件
COPY dispatcher.py .
COPY filter_worker.py .
COPY aggregator.py .

# 环境变量
ENV KAFKA_BROKERS=kafka:9092
ENV PYTHONUNBUFFERED=1

# 默认运行filter_worker
CMD ["python", "filter_worker.py"]
