# 使用 Maven 启动 infra、system 和 detection 服务
# 使用方法：在 PowerShell 中运行此脚本，或在各个服务目录下单独执行 Maven 命令

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "使用 Maven 启动服务" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 获取脚本所在目录
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# 函数：在后台启动服务
function StartServiceWithMaven {
    param(
        [string]$ServiceName,
        [string]$ServicePath,
        [string]$MainClass,
        [int]$Port
    )
    
    Write-Host "`n========================================" -ForegroundColor Yellow
    Write-Host "启动服务: $ServiceName" -ForegroundColor Yellow
    Write-Host "路径: $ServicePath" -ForegroundColor Gray
    Write-Host "主类: $MainClass" -ForegroundColor Gray
    Write-Host "端口: $Port" -ForegroundColor Gray
    Write-Host "========================================" -ForegroundColor Yellow
    
    # 切换到服务目录
    $fullPath = Join-Path $scriptDir $ServicePath
    if (-not (Test-Path $fullPath)) {
        Write-Host "错误: 服务路径不存在: $fullPath" -ForegroundColor Red
        return $false
    }
    
    Set-Location $fullPath
    
    # 创建日志目录
    $logDir = Join-Path $fullPath "logs"
    if (-not (Test-Path $logDir)) {
        New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    }
    
    # 使用 Maven 启动服务
    Write-Host "`n正在启动 $ServiceName 服务..." -ForegroundColor Green
    Write-Host "执行命令: mvn spring-boot:run -Dspring-boot.run.main-class=$MainClass -Dspring-boot.run.arguments=--spring.profiles.active=local --server.port=$Port" -ForegroundColor Gray
    
    # 在新窗口中启动服务（每个服务一个窗口）
    $logFile = Join-Path $logDir "$($ServiceName.ToLower()).log"
    $errorLogFile = Join-Path $logDir "$($ServiceName.ToLower())-error.log"
    
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$fullPath'; mvn spring-boot:run -Dspring-boot.run.main-class=$MainClass -Dspring-boot.run.arguments='--spring.profiles.active=local --server.port=$Port' 2>&1 | Tee-Object -FilePath '$logFile'"
    
    Write-Host "$ServiceName 服务正在启动中..." -ForegroundColor Green
    Write-Host "日志文件: $logFile" -ForegroundColor Gray
    
    Set-Location $scriptDir
    return $true
}

# 启动三个服务
Write-Host "`n准备启动三个服务..." -ForegroundColor Cyan

# 1. 启动 Infra 服务
StartServiceWithMaven -ServiceName "Infra" `
    -ServicePath "yudao-module-infra\yudao-module-infra-server" `
    -MainClass "cn.iocoder.yudao.module.infra.InfraServerApplication" `
    -Port 48082

Start-Sleep -Seconds 5

# 2. 启动 System 服务
StartServiceWithMaven -ServiceName "System" `
    -ServicePath "yudao-module-system\yudao-module-system-server" `
    -MainClass "cn.iocoder.yudao.module.system.SystemServerApplication" `
    -Port 48081

Start-Sleep -Seconds 5

# 3. 启动 Detection 服务
StartServiceWithMaven -ServiceName "Detection" `
    -ServicePath "yudao-module-detection\yudao-module-detection-server" `
    -MainClass "cn.iocoder.yudao.module.detection.GrpcServerApplication" `
    -Port 48083

Write-Host "`n========================================" -ForegroundColor Green
Write-Host "所有服务已启动" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "`n服务访问地址:" -ForegroundColor Cyan
Write-Host "  - System:    http://localhost:48081" -ForegroundColor White
Write-Host "  - Infra:     http://localhost:48082" -ForegroundColor White
Write-Host "  - Detection: http://localhost:48083" -ForegroundColor White
Write-Host "`n提示: 每个服务都在独立的 PowerShell 窗口中运行" -ForegroundColor Yellow
Write-Host "      可以通过关闭对应的窗口来停止服务" -ForegroundColor Yellow
