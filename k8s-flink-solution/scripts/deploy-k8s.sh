#!/bin/bash
# K8s 部署脚本

set -e

echo "=========================================="
echo "  K8s + Flink 滤波解决方案部署"
echo "=========================================="

# 检查 kubectl
if ! command -v kubectl &> /dev/null; then
    echo "[ERROR] kubectl 未安装"
    exit 1
fi

# 检查集群连接
if ! kubectl cluster-info &> /dev/null; then
    echo "[ERROR] 无法连接到 K8s 集群"
    exit 1
fi

echo "[1/5] 创建命名空间..."
kubectl apply -f ../k8s/namespace.yaml

echo "[2/5] 构建 Docker 镜像..."
cd ../docker/filter-gateway
docker build -t filter-gateway:latest .
cd ../websocket-bridge
docker build -t websocket-bridge:latest .
cd ../../scripts

echo "[3/5] 部署 Flink 集群..."
kubectl apply -f ../k8s/flink-cluster.yaml

echo "[4/5] 部署滤波服务..."
kubectl apply -f ../k8s/filter-gateway-deployment.yaml
kubectl apply -f ../k8s/kafka-processor-deployment.yaml

echo "[5/5] 等待 Pod 就绪..."
kubectl wait --for=condition=ready pod -l app=filter-gateway -n signal-processing --timeout=120s
kubectl wait --for=condition=ready pod -l app=flink -n signal-processing --timeout=120s

echo ""
echo "=========================================="
echo "  部署完成！"
echo "=========================================="
echo ""
echo "查看 Pod 状态:"
echo "  kubectl get pods -n signal-processing"
echo ""
echo "查看 Flink Dashboard:"
echo "  kubectl port-forward svc/flink-jobmanager 8081:8081 -n signal-processing"
echo "  然后访问: http://localhost:8081"
echo ""
echo "查看滤波网关:"
echo "  kubectl port-forward svc/filter-gateway-service 8010:8010 -n signal-processing"
echo "  然后访问: http://localhost:8010/docs"
echo ""
