# Monitor 服务启动脚本
# 使用 Spring Boot 直接启动

$JarName = "yudao-module-monitor-server.jar"
$Port = 48090
$MainClass = "cn.iocoder.yudao.module.monitor.MonitorServerApplication"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ServerDir = Join-Path $ProjectRoot "yudao-module-monitor-server"
$TargetDir = Join-Path $ServerDir "target"

Write-Host "=== Monitor 服务启动脚本 ===" -ForegroundColor Cyan
Write-Host ""

# 检查 JAR 文件是否存在
$JarPath = Join-Path $TargetDir $JarName
if (-not (Test-Path $JarPath)) {
    Write-Host "❌ JAR 文件不存在: $JarPath" -ForegroundColor Red
    Write-Host "请先运行: mvn clean package -DskipTests" -ForegroundColor Yellow
    exit 1
}

# 检查 lib 目录
$LibDir = Join-Path $TargetDir "lib"
if (-not (Test-Path $LibDir)) {
    Write-Host "❌ lib 目录不存在: $LibDir" -ForegroundColor Red
    Write-Host "请先运行: mvn clean package -DskipTests" -ForegroundColor Yellow
    exit 1
}

# 检查端口是否被占用
$PortInUse = netstat -ano | findstr ":$Port" | findstr "LISTENING"
if ($PortInUse) {
    Write-Host "⚠️  端口 $Port 已被占用，正在停止旧进程..." -ForegroundColor Yellow
    $pids = $PortInUse | ForEach-Object { ($_ -split '\s+')[-1] }
    foreach ($pid in $pids) {
        if ($pid -and $pid -ne "0") {
            taskkill /F /PID $pid 2>$null
            Write-Host "   已停止进程 $pid" -ForegroundColor Gray
        }
    }
    Start-Sleep -Seconds 2
}

# 创建日志目录
$LogDir = Join-Path $ServerDir "logs"
if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
}

# 构建 classpath
$LibJars = Get-ChildItem -Path $LibDir -Filter "*.jar" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
$ClassPath = "$JarPath;" + ($LibJars -join ";")

Write-Host "📦 JAR 文件: $JarPath" -ForegroundColor Green
Write-Host "📚 依赖库: $($LibJars.Count) 个" -ForegroundColor Green
Write-Host "🔌 端口: $Port" -ForegroundColor Green
Write-Host "📝 日志目录: $LogDir" -ForegroundColor Green
Write-Host ""

# 启动服务
Write-Host "🚀 正在启动 Monitor 服务..." -ForegroundColor Cyan
Write-Host ""

$LogFile = Join-Path $LogDir "monitor.log"
$ErrorLogFile = Join-Path $LogDir "monitor-error.log"

# 切换到 server 目录
Push-Location $ServerDir

try {
    # 启动 Java 进程
    $Process = Start-Process -FilePath "java" `
        -ArgumentList "-cp", $ClassPath, $MainClass, "--spring.profiles.active=local", "--server.port=$Port" `
        -RedirectStandardOutput $LogFile `
        -RedirectStandardError $ErrorLogFile `
        -WindowStyle Hidden `
        -PassThru
    
    Write-Host "✅ 服务已启动，进程 ID: $($Process.Id)" -ForegroundColor Green
    Write-Host ""
    Write-Host "等待服务初始化（约 30 秒）..." -ForegroundColor Yellow
    
    # 等待服务启动
    $MaxWait = 60
    $WaitCount = 0
    $Started = $false
    
    while ($WaitCount -lt $MaxWait) {
        Start-Sleep -Seconds 2
        $WaitCount += 2
        
        # 检查日志中是否有启动成功标志
        if (Test-Path $LogFile) {
            $LogContent = Get-Content $LogFile -Tail 10 -ErrorAction SilentlyContinue
            if ($LogContent -match "Started MonitorServerApplication|Tomcat started on port") {
                $Started = $true
                break
            }
        }
        
        # 检查进程是否还在运行
        if (-not (Get-Process -Id $Process.Id -ErrorAction SilentlyContinue)) {
            Write-Host ""
            Write-Host "❌ 服务进程已退出，请检查错误日志: $ErrorLogFile" -ForegroundColor Red
            if (Test-Path $ErrorLogFile) {
                Write-Host ""
                Write-Host "最后 20 行错误日志:" -ForegroundColor Yellow
                Get-Content $ErrorLogFile -Tail 20
            }
            exit 1
        }
        
        Write-Host "." -NoNewline -ForegroundColor Gray
    }
    
    Write-Host ""
    Write-Host ""
    
    if ($Started) {
        Write-Host "✅ Monitor 服务启动成功！" -ForegroundColor Green
        Write-Host ""
        Write-Host "服务信息:" -ForegroundColor Cyan
        Write-Host "  - 端口: http://localhost:$Port" -ForegroundColor White
        Write-Host "  - 进程 ID: $($Process.Id)" -ForegroundColor White
        Write-Host "  - 日志文件: $LogFile" -ForegroundColor White
        Write-Host ""
        Write-Host "查看日志: Get-Content `"$LogFile`" -Tail 50 -Wait" -ForegroundColor Gray
        Write-Host "停止服务: taskkill /F /PID $($Process.Id)" -ForegroundColor Gray
    } else {
        Write-Host "⚠️  服务可能还在启动中，请检查日志: $LogFile" -ForegroundColor Yellow
    }
    
} finally {
    Pop-Location
}
