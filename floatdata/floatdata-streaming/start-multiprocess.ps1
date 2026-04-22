# Start Multiple Sender Processes
# Launches multiple independent HighRateDataSender processes for maximum throughput

param(
    [int]$NumProcesses = 4,
    [long]$TotalTargetRate = 2000000,
    [int]$ChunkSize = 2000,
    [string]$DataFile = "tdms-export.bin"
)

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Multi-Process Launcher" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Calculate rate per process
$ratePerProcess = [long]($TotalTargetRate / $NumProcesses)

Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Total Processes:  $NumProcesses"
Write-Host "  Total Target:     $TotalTargetRate samples/sec"
Write-Host "  Per Process:      $ratePerProcess samples/sec"
Write-Host "  Chunk Size:       $ChunkSize"
Write-Host "  Data File:        $DataFile"
Write-Host ""

# Check prerequisites
if (-not (Test-Path $DataFile)) {
    Write-Host "[ERROR] Data file not found: $DataFile" -ForegroundColor Red
    Write-Host "Run: python export-tdms-binary.py" -ForegroundColor Yellow
    exit 1
}

$JarPath = "target\floatdata-streaming-1.0.0.jar"
if (-not (Test-Path $JarPath)) {
    Write-Host "[ERROR] JAR not found" -ForegroundColor Red
    Write-Host "Run: mvn clean package -DskipTests" -ForegroundColor Yellow
    exit 1
}

# Check Netty server
Write-Host "[INFO] Checking Netty server..." -ForegroundColor Yellow
$test = Test-NetConnection -ComputerName localhost -Port 9090 -WarningAction SilentlyContinue
if (-not $test.TcpTestSucceeded) {
    Write-Host "[ERROR] Netty server not available on port 9090" -ForegroundColor Red
    Write-Host "Start system first: .\start.ps1" -ForegroundColor Yellow
    exit 1
}
Write-Host "[OK] Netty server ready" -ForegroundColor Green
Write-Host ""

# Launch processes
Write-Host "[INFO] Launching $NumProcesses sender processes..." -ForegroundColor Yellow

$processes = @()
for ($i = 0; $i -lt $NumProcesses; $i++) {
    $sensorId = $i + 1
    $logFile = "sender_$i.log"
    
    $arguments = @(
        "-cp", $JarPath,
        "com.floatdata.client.HighRateDataSender",
        "--dataFile=$DataFile",
        "--sensorId=$sensorId",
        "--chunkSize=$ChunkSize",
        "--targetRate=$ratePerProcess",
        "--reportInterval=2"
    )
    
    $process = Start-Process -FilePath "java" `
        -ArgumentList $arguments `
        -NoNewWindow `
        -PassThru `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError "sender_$i.err"
    
    $processes += $process
    Write-Host "  [OK] Process $i started (PID: $($process.Id), SensorId: $sensorId)" -ForegroundColor Green
    Start-Sleep -Milliseconds 500
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  All $NumProcesses processes running" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

Write-Host "Process IDs:" -ForegroundColor Cyan
foreach ($proc in $processes) {
    Write-Host "  PID $($proc.Id)"
}

Write-Host ""
Write-Host "To stop all senders:" -ForegroundColor Yellow
Write-Host '  Get-Process java | Where-Object {$_.MainWindowTitle -eq ""} | Stop-Process -Force' -ForegroundColor Cyan
Write-Host ""
Write-Host "To view combined throughput:" -ForegroundColor Yellow
Write-Host '  Get-Content sender_*.log | Select-String "samples/sec"' -ForegroundColor Cyan
Write-Host ""
