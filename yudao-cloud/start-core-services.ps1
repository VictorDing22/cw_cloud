# Start four core services one by one
# Services: gateway, system, infra, monitor
# Each service will be built and started separately

Write-Host "Starting YuDao Cloud Core Services (One by One)" -ForegroundColor Green

# Get script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# 1. Start infrastructure services (MySQL, Redis, Nacos)
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Step 1: Starting infrastructure services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
docker compose -f docker-compose-simple.yml up mysql redis nacos -d

if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker services failed to start, please check if Docker is running" -ForegroundColor Red
    exit 1
}

Write-Host "Waiting for infrastructure services to start (30 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 30
Write-Host "Infrastructure services started successfully" -ForegroundColor Green

# 2. Create log directories
Write-Host "`nCreating log directories..." -ForegroundColor Yellow
$logDirs = @(
    "yudao-gateway\logs",
    "yudao-module-system\yudao-module-system-server\logs",
    "yudao-module-infra\yudao-module-infra-server\logs",
    "yudao-module-monitor\yudao-module-monitor-server\logs"
)

foreach ($dir in $logDirs) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}

# Function to build and start a service
function BuildAndStartService {
    param(
        [string]$ServiceName,
        [string]$ServicePath,
        [string]$JarName,
        [int]$Port,
        [string]$MainClass = $null,
        [bool]$UseClasspath = $false
    )
    
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "Building and Starting: $ServiceName" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    
    # Step 1: Build the service
    Write-Host "`n[1/2] Building $ServiceName..." -ForegroundColor Yellow
    Set-Location $ServicePath
    
    Write-Host "Running: mvn clean package -DskipTests" -ForegroundColor Gray
    mvn clean package -DskipTests
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed for $ServiceName" -ForegroundColor Red
        Set-Location $scriptDir
        return $false
    }
    
    Write-Host "Build completed successfully" -ForegroundColor Green
    
    # Step 2: Start the service
    Write-Host "`n[2/2] Starting $ServiceName on port $Port..." -ForegroundColor Yellow
    
    if ($UseClasspath) {
        # Monitor service uses classpath
        $libPath = (Get-ChildItem -Path "target\lib" -Filter "*.jar" | ForEach-Object { $_.FullName }) -join ";"
        $cp = "target\$JarName;$libPath"
        Start-Process -FilePath "java" -ArgumentList "-cp", $cp, $MainClass, "--spring.profiles.active=local", "--server.port=$Port" -RedirectStandardOutput "logs\$($ServiceName.ToLower()).log" -RedirectStandardError "logs\$($ServiceName.ToLower())-error.log" -WindowStyle Hidden
    } else {
        # Other services use fat jar
        Start-Process -FilePath "java" -ArgumentList "-jar", "target\$JarName", "--spring.profiles.active=local", "--server.port=$Port" -RedirectStandardOutput "logs\$($ServiceName.ToLower()).log" -RedirectStandardError "logs\$($ServiceName.ToLower())-error.log" -WindowStyle Hidden
    }
    
    Set-Location $scriptDir
    
    # Wait for service to start
    Write-Host "Waiting for $ServiceName to start (30 seconds)..." -ForegroundColor Yellow
    Start-Sleep -Seconds 30
    
    # Check if port is listening
    $portCheck = netstat -ano | findstr ":$Port" | findstr "LISTENING"
    if ($portCheck) {
        Write-Host "$ServiceName started successfully on port $Port" -ForegroundColor Green
        return $true
    } else {
        Write-Host "$ServiceName may not have started properly. Check logs for errors." -ForegroundColor Yellow
        return $false
    }
}

# 3. Build and Start Gateway service
$gatewayOk = BuildAndStartService -ServiceName "Gateway" -ServicePath "yudao-gateway" -JarName "yudao-gateway.jar" -Port 48080

if (-not $gatewayOk) {
    Write-Host "Gateway service failed to start. Continue anyway? (Y/N)" -ForegroundColor Yellow
    $continue = Read-Host
    if ($continue -ne "Y" -and $continue -ne "y") {
        exit 1
    }
}

# 4. Build and Start System service
$systemOk = BuildAndStartService -ServiceName "System" -ServicePath "yudao-module-system\yudao-module-system-server" -JarName "yudao-module-system-server.jar" -Port 48081

if (-not $systemOk) {
    Write-Host "System service failed to start. Continue anyway? (Y/N)" -ForegroundColor Yellow
    $continue = Read-Host
    if ($continue -ne "Y" -and $continue -ne "y") {
        exit 1
    }
}

# 5. Build and Start Infra service
$infraOk = BuildAndStartService -ServiceName "Infra" -ServicePath "yudao-module-infra\yudao-module-infra-server" -JarName "yudao-module-infra-server.jar" -Port 48082

if (-not $infraOk) {
    Write-Host "Infra service failed to start. Continue anyway? (Y/N)" -ForegroundColor Yellow
    $continue = Read-Host
    if ($continue -ne "Y" -and $continue -ne "y") {
        exit 1
    }
}

# 6. Build and Start Monitor service (uses classpath)
$monitorOk = BuildAndStartService -ServiceName "Monitor" -ServicePath "yudao-module-monitor\yudao-module-monitor-server" -JarName "yudao-module-monitor-server.jar" -Port 48090 -MainClass "cn.iocoder.yudao.module.monitor.MonitorServerApplication" -UseClasspath $true

if (-not $monitorOk) {
    Write-Host "Monitor service failed to start. Continue anyway? (Y/N)" -ForegroundColor Yellow
    $continue = Read-Host
    if ($continue -ne "Y" -and $continue -ne "y") {
        exit 1
    }
}

# 7. Display final status
Write-Host "`n========================================" -ForegroundColor Green
Write-Host "All Services Started" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "`nService Status:" -ForegroundColor Cyan
Write-Host "  Gateway (48080): $(if($gatewayOk){'Running'}else{'Failed'})" -ForegroundColor $(if($gatewayOk){'Green'}else{'Red'})
Write-Host "  System (48081):  $(if($systemOk){'Running'}else{'Failed'})" -ForegroundColor $(if($systemOk){'Green'}else{'Red'})
Write-Host "  Infra (48082):   $(if($infraOk){'Running'}else{'Failed'})" -ForegroundColor $(if($infraOk){'Green'}else{'Red'})
Write-Host "  Monitor (48090): $(if($monitorOk){'Running'}else{'Failed'})" -ForegroundColor $(if($monitorOk){'Green'}else{'Red'})

Write-Host "`nService Access Addresses:" -ForegroundColor Cyan
Write-Host "  - Gateway:  http://localhost:48080" -ForegroundColor White
Write-Host "  - System:   http://localhost:48081" -ForegroundColor White
Write-Host "  - Infra:    http://localhost:48082" -ForegroundColor White
Write-Host "  - Monitor:  http://localhost:48090" -ForegroundColor White

Write-Host "`nService Logs:" -ForegroundColor Cyan
Write-Host "  - Gateway:  yudao-gateway\logs\gateway.log" -ForegroundColor White
Write-Host "  - System:   yudao-module-system\yudao-module-system-server\logs\system.log" -ForegroundColor White
Write-Host "  - Infra:    yudao-module-infra\yudao-module-infra-server\logs\infra.log" -ForegroundColor White
Write-Host "  - Monitor: yudao-module-monitor\yudao-module-monitor-server\logs\monitor.log" -ForegroundColor White

Write-Host "`nNote: Frontend service needs to be started separately" -ForegroundColor Yellow
