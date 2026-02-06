# 1. Build the project (Build parent module to ensure API is built)
Write-Host "Building Maven project..."
Push-Location ..
mvn clean package -DskipTests
Pop-Location

# 2. Build Docker image
Write-Host "Building Docker image..."
docker build -t yudao-module-detection-server:latest .

Write-Host "Build complete! You can now deploy to Kubernetes using:"
Write-Host "kubectl apply -f k8s/deployment.yaml"
