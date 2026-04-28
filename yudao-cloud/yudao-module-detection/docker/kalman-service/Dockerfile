FROM python:3.10-slim
WORKDIR /app
RUN pip config set global.index-url https://mirrors.aliyun.com/pypi/simple/ && \
    pip config set global.trusted-host mirrors.aliyun.com && \
    pip install --no-cache-dir --timeout 120 \
    fastapi uvicorn numpy orjson
COPY kalman_service.py .
EXPOSE 8000
CMD ["uvicorn", "kalman_service:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "8"]
