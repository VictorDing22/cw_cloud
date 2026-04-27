FROM python:3.10-slim

WORKDIR /app

RUN pip config set global.index-url https://mirrors.aliyun.com/pypi/simple/ && \
    pip config set global.trusted-host mirrors.aliyun.com && \
    pip install --no-cache-dir --timeout 120 \
    kafka-python \
    websockets

COPY websocket_bridge.py .

EXPOSE 8083

CMD ["python", "websocket_bridge.py"]
