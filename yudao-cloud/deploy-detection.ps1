# Yudao Detection Flink 平台一键部署脚本
Write-Host "========== 开始部署 Yudao Detection 平台 ==========" -ForegroundColor Cyan

# 1. 编译项目
Write-Host "[1/4] 正在编译 Maven 项目..." -ForegroundColor Yellow
mvn clean package -DskipTests -pl yudao-module-detection/yudao-module-detection-server -am

if ($LASTEXITCODE -ne 0) {
    Write-Host "编译失败，请检查代码！" -ForegroundColor Red
    exit $LASTEXITCODE
}

# 2. 构建 Docker 镜像
Write-Host "[2/4] 正在构建 Docker 镜像..." -ForegroundColor Yellow
docker build -t yudao-module-detection-server:latest ./yudao-module-detection/yudao-module-detection-server/

# 3. 初始化 TDengine (假设 K3s 已运行并暴露服务)
Write-Host "[3/4] 请确保 TDengine 已启动并执行了 sql/init_tdengine.sql" -ForegroundColor Magenta

# 4. 部署到 Kubernetes
Write-Host "[4/4] 正在部署到 Kubernetes (K3s)..." -ForegroundColor Yellow
kubectl apply -f ./yudao-module-detection/yudao-module-detection-server/k8s/deployment.yaml

Write-Host "========== 部署完成 ==========" -ForegroundColor Green
Write-Host "提示："
Write-Host "1. 使用 'kubectl logs -f deployment/yudao-detection-flink-job' 查看 Flink 日志"
Write-Host "2. 使用 'kubectl logs -f deployment/yudao-module-detection-server' 查看 Netty 日志"
Write-Host "3. 运行 DataSimulator 进行数据模拟测试"
