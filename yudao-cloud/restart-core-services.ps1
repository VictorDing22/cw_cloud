# Restart four core services
# Services: gateway, system, infra, monitor

Write-Host "========================================" -ForegroundColor Green
Write-Host "Restarting YuDao Cloud Core Services" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

# Get script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# Function to stop service by port
function StopServiceByPort {
    param([int]$Port, [string]$ServiceName)
    
    Write-Host "`nStopping $ServiceName (port $Port)..." -ForegroundColor Yellow
    
    # Find process using the port
    $processes = netstat -ano | findstr ":$Port" | findstr "LISTENING"
    if ($processes) {
        $processIds = $processes | ForEach-Object {
            ($_ -split '\s+')[-1]
        } | Select-Object -Unique
        
        foreach ($processId in $processIds) {
            if ($processId -and $processId -ne "0") {
                Write-Host "  Found process PID: $processId" -ForegroundColor Gray
                try {
                    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
                    Write-Host "  Process $processId stopped" -ForegroundColor Green
                } catch {
                    Write-Host "  Failed to stop process $processId : $($_.Exception.Message)" -ForegroundColor Red
                }
            }
        }
        
        # Wait a bit for process to fully stop
        Start-Sleep -Seconds 2
        
        # Verify port is free
        $stillRunning = netstat -ano | findstr ":$Port" | findstr "LISTENING"
        if ($stillRunning) {
            Write-Host "  Warning: Port $Port is still in use" -ForegroundColor Yellow
        } else {
            Write-Host "  $ServiceName stopped successfully" -ForegroundColor Green
        }
    } else {
        Write-Host "  $ServiceName is not running" -ForegroundColor Gray
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

# Step 1: Stop all services
Write-Host "`n========================================" -ForegroundColor Yellow
Write-Host "Step 1: Stopping existing services" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

StopServiceByPort -Port 48080 -ServiceName "Gateway"
StopServiceByPort -Port 48081 -ServiceName "System"
StopServiceByPort -Port 48082 -ServiceName "Infra"
StopServiceByPort -Port 48090 -ServiceName "Monitor"

Write-Host "`nWaiting for services to fully stop (5 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

# Step 2: Create log directories
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

# Step 3: Restart services
Write-Host "`n========================================" -ForegroundColor Yellow
Write-Host "Step 2: Restarting services" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow

# 1. Gateway
$gatewayOk = BuildAndStartService -ServiceName "Gateway" -ServicePath "yudao-gateway" -JarName "yudao-gateway.jar" -Port 48080

if (-not $gatewayOk) {
    Write-Host "Gateway service failed to start. Continue anyway? (Y/N)" -ForegroundColor Yellow
    $continue = Read-Host
    if ($continue -ne "Y" -and $continue -ne "y") {
        exit 1
    }
}

# 2. System
$systemOk = BuildAndStartService -ServiceName "System" -ServicePath "yudao-module-system\yudao-module-system-server" -JarName "yudao-module-system-server.jar" -Port 48081

if (-not $systemOk) {
    Write-Host "System service failed to start. Continue anyway? (Y/N)" -ForegroundColor Yellow
    $continue = Read-Host
    if ($continue -ne "Y" -and $continue -ne "y") {
        exit 1
    }
}

# 3. Infra
$infraOk = BuildAndStartService -ServiceName "Infra" -ServicePath "yudao-module-infra\yudao-module-infra-server" -JarName "yudao-module-infra-server.jar" -Port 48082

if (-not $infraOk) {
    Write-Host "Infra service failed to start. Continue anyway? (Y/N)" -ForegroundColor Yellow
    $continue = Read-Host
    if ($continue -ne "Y" -and $continue -ne "y") {
        exit 1
    }
}

# 4. Monitor
$monitorOk = BuildAndStartService -ServiceName "Monitor" -ServicePath "yudao-module-monitor\yudao-module-monitor-server" -JarName "yudao-module-monitor-server.jar" -Port 48090 -MainClass "cn.iocoder.yudao.module.monitor.MonitorServerApplication" -UseClasspath $true

if (-not $monitorOk) {
    Write-Host "Monitor service failed to start. Continue anyway? (Y/N)" -ForegroundColor Yellow
    $continue = Read-Host
    if ($continue -ne "Y" -and $continue -ne "y") {
        exit 1
    }
}

# Step 4: Display final status
Write-Host "`n========================================" -ForegroundColor Green
Write-Host "All Services Restarted" -ForegroundColor Green
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

Write-Host "`n✅ All core services have been restarted!" -ForegroundColor Green
