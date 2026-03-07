# Start three services: system, infra, detection
# Services: system (48081), infra (48082), detection (48083)

Write-Host "Starting YuDao Cloud Services: System, Infra, Detection" -ForegroundColor Green

# Get script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# Create log directories
Write-Host "`nCreating log directories..." -ForegroundColor Yellow
$logDirs = @(
    "yudao-module-system\yudao-module-system-server\logs",
    "yudao-module-infra\yudao-module-infra-server\logs",
    "yudao-module-detection\yudao-module-detection-server\logs"
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
        # Service uses classpath
        $libPath = (Get-ChildItem -Path "target\lib" -Filter "*.jar" | ForEach-Object { $_.FullName }) -join ";"
        $cp = "target\$JarName;$libPath"
        Start-Process -FilePath "java" -ArgumentList "-cp", $cp, $MainClass, "--spring.profiles.active=local", "--server.port=$Port" -RedirectStandardOutput "logs\$($ServiceName.ToLower()).log" -RedirectStandardError "logs\$($ServiceName.ToLower())-error.log" -WindowStyle Hidden
    } else {
        # Service uses fat jar
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

# 1. Build and Start System service
$systemOk = BuildAndStartService -ServiceName "System" -ServicePath "yudao-module-system\yudao-module-system-server" -JarName "yudao-module-system-server.jar" -Port 48081

if (-not $systemOk) {
    Write-Host "System service failed to start. Continue anyway? (Y/N)" -ForegroundColor Yellow
    $continue = Read-Host
    if ($continue -ne "Y" -and $continue -ne "y") {
        exit 1
    }
}

# 2. Build and Start Infra service
$infraOk = BuildAndStartService -ServiceName "Infra" -ServicePath "yudao-module-infra\yudao-module-infra-server" -JarName "yudao-module-infra-server.jar" -Port 48082

if (-not $infraOk) {
    Write-Host "Infra service failed to start. Continue anyway? (Y/N)" -ForegroundColor Yellow
    $continue = Read-Host
    if ($continue -ne "Y" -and $continue -ne "y") {
        exit 1
    }
}

# 3. Build and Start Detection service
$detectionOk = BuildAndStartService -ServiceName "Detection" -ServicePath "yudao-module-detection\yudao-module-detection-server" -JarName "yudao-module-detection-server.jar" -Port 48083

if (-not $detectionOk) {
    Write-Host "Detection service failed to start. Continue anyway? (Y/N)" -ForegroundColor Yellow
    $continue = Read-Host
    if ($continue -ne "Y" -and $continue -ne "y") {
        exit 1
    }
}

# 4. Display final status
Write-Host "`n========================================" -ForegroundColor Green
Write-Host "All Services Started" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "`nService Status:" -ForegroundColor Cyan
Write-Host "  System (48081):    $(if($systemOk){'Running'}else{'Failed'})" -ForegroundColor $(if($systemOk){'Green'}else{'Red'})
Write-Host "  Infra (48082):     $(if($infraOk){'Running'}else{'Failed'})" -ForegroundColor $(if($infraOk){'Green'}else{'Red'})
Write-Host "  Detection (48083): $(if($detectionOk){'Running'}else{'Failed'})" -ForegroundColor $(if($detectionOk){'Green'}else{'Red'})

Write-Host "`nService Access Addresses:" -ForegroundColor Cyan
Write-Host "  - System:    http://localhost:48081" -ForegroundColor White
Write-Host "  - Infra:     http://localhost:48082" -ForegroundColor White
Write-Host "  - Detection: http://localhost:48083" -ForegroundColor White

Write-Host "`nService Logs:" -ForegroundColor Cyan
Write-Host "  - System:    yudao-module-system\yudao-module-system-server\logs\system.log" -ForegroundColor White
Write-Host "  - Infra:     yudao-module-infra\yudao-module-infra-server\logs\infra.log" -ForegroundColor White
Write-Host "  - Detection: yudao-module-detection\yudao-module-detection-server\logs\detection.log" -ForegroundColor White
