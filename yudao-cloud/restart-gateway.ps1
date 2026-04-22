# Restart Gateway Service
$gatewayPath = "D:\CW_Cloud-main\yudao-cloud\yudao-gateway"
Set-Location $gatewayPath

# Stop existing service
Write-Host "`n=== Stopping Gateway Service ===" -ForegroundColor Yellow
$existing = netstat -ano | findstr ":48080" | findstr "LISTENING"
if ($existing) {
    $pid = ($existing -split '\s+')[-1]
    Write-Host "Found running Gateway process, PID: $pid" -ForegroundColor Yellow
    taskkill /F /PID $pid
    Start-Sleep -Seconds 2
    Write-Host "Gateway service stopped" -ForegroundColor Green
} else {
    Write-Host "No running Gateway service found" -ForegroundColor Gray
}

# Start service
Write-Host "`n=== Starting Gateway Service ===" -ForegroundColor Cyan
Start-Process -FilePath "java" -ArgumentList "-jar", "target\yudao-gateway.jar", "--spring.profiles.active=local", "--server.port=48080" -RedirectStandardOutput "logs\gateway.log" -RedirectStandardError "logs\gateway-error.log" -WindowStyle Hidden

Write-Host "Gateway service start command executed" -ForegroundColor Green
Write-Host "Waiting for service to start (30 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# Check status
Write-Host "`n=== Service Status Check ===" -ForegroundColor Cyan
$gateway = netstat -ano | findstr ":48080" | findstr "LISTENING"
if ($gateway) {
    Write-Host "Gateway (48080): Running" -ForegroundColor Green
    Write-Host "`nGateway service started successfully!" -ForegroundColor Green
} else {
    Write-Host "Gateway (48080): Not running" -ForegroundColor Red
    Write-Host "`nPlease check logs: $gatewayPath\logs\gateway-error.log" -ForegroundColor Yellow
}
