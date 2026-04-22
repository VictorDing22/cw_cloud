# Restart Monitor Service
$monitorPath = "D:\CW_Cloud-main\yudao-cloud\yudao-module-monitor\yudao-module-monitor-server"
Set-Location $monitorPath

# Stop existing service
Write-Host "`n=== Stopping Monitor Service ===" -ForegroundColor Yellow
$existing = netstat -ano | findstr ":48090" | findstr "LISTENING"
if ($existing) {
    $pid = ($existing -split '\s+')[-1]
    Write-Host "Found running Monitor process, PID: $pid" -ForegroundColor Yellow
    taskkill /F /PID $pid
    Start-Sleep -Seconds 2
    Write-Host "Monitor service stopped" -ForegroundColor Green
} else {
    Write-Host "No running Monitor service found" -ForegroundColor Gray
}

# Start service
Write-Host "`n=== Starting Monitor Service ===" -ForegroundColor Cyan
$libFiles = Get-ChildItem "target\lib\*.jar" | ForEach-Object { $_.FullName }
$libPath = $libFiles -join ";"
$cp = "target\yudao-module-monitor-server.jar;$libPath"

$javaArgs = @(
    "-cp",
    $cp,
    "cn.iocoder.yudao.module.monitor.MonitorServerApplication",
    "--spring.profiles.active=local",
    "--server.port=48090"
)

Start-Process -FilePath "java" -ArgumentList $javaArgs -RedirectStandardOutput "logs\monitor.log" -RedirectStandardError "logs\monitor-error.log" -WindowStyle Hidden

Write-Host "Monitor service start command executed" -ForegroundColor Green
Write-Host "Waiting for service to start (30 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# Check status
Write-Host "`n=== Service Status Check ===" -ForegroundColor Cyan
$monitor = netstat -ano | findstr ":48090" | findstr "LISTENING"
if ($monitor) {
    Write-Host "Monitor (48090): Running" -ForegroundColor Green
    Write-Host "`nMonitor service started successfully!" -ForegroundColor Green
} else {
    Write-Host "Monitor (48090): Not running" -ForegroundColor Red
    Write-Host "`nPlease check logs: $monitorPath\logs\monitor-error.log" -ForegroundColor Yellow
}
