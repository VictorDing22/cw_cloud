# Multi-Process Performance Test Script
# Tests system throughput with multiple parallel connections

param(
    [int]$NumConnections = 4,
    [int]$DurationSeconds = 60,
    [long]$TargetRate = 2000000,
    [int]$ChunkSize = 2000,
    [string]$DataFile = "tdms-export.bin",
    [string]$LogPath = "multiprocess.log",
    [string]$ErrPath = "multiprocess.err"
)

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Multi-Process Performance Test" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Connections:    $NumConnections"
Write-Host "  Duration:       $DurationSeconds seconds"
Write-Host "  Target Rate:    $TargetRate samples/sec (total)"
Write-Host "  Per Connection: $($TargetRate / $NumConnections) samples/sec"
Write-Host "  Chunk Size:     $ChunkSize samples"
Write-Host "  Data File:      $DataFile"
Write-Host ""

# Check if data file exists
if (-not (Test-Path $DataFile)) {
    Write-Host "[ERROR] Data file not found: $DataFile" -ForegroundColor Red
    Write-Host "Please run: python export-tdms-binary.py" -ForegroundColor Yellow
    exit 1
}

# Check if JAR exists
$JarPath = "target\floatdata-streaming-1.0.0.jar"
if (-not (Test-Path $JarPath)) {
    Write-Host "[ERROR] JAR not found: $JarPath" -ForegroundColor Red
    Write-Host "Please run: mvn clean package -DskipTests" -ForegroundColor Yellow
    exit 1
}

# Clean up old logs
if (Test-Path $LogPath) { Remove-Item $LogPath -Force }
if (Test-Path $ErrPath) { Remove-Item $ErrPath -Force }

# Wait for Netty server to be ready
Write-Host "[INFO] Checking Netty server availability..." -ForegroundColor Yellow
$maxWait = 30
$waited = 0
$connected = $false

while ($waited -lt $maxWait) {
    $test = Test-NetConnection -ComputerName localhost -Port 9090 -WarningAction SilentlyContinue
    if ($test.TcpTestSucceeded) {
        $connected = $true
        break
    }
    Start-Sleep -Seconds 1
    $waited++
}

if (-not $connected) {
    Write-Host "[ERROR] Netty server not available on port 9090" -ForegroundColor Red
    Write-Host "Please start the system first: .\start.ps1" -ForegroundColor Yellow
    exit 1
}

Write-Host "[OK] Netty server is ready" -ForegroundColor Green
Write-Host ""

# Calculate max samples for the test duration
$maxSamples = $TargetRate * $DurationSeconds

# Start multi-process sender
Write-Host "[INFO] Starting multi-process sender..." -ForegroundColor Yellow
Write-Host "  Target: $maxSamples samples over $DurationSeconds seconds" -ForegroundColor Cyan

$arguments = @(
    "-cp", $JarPath,
    "com.floatdata.client.MultiProcessDataSender",
    "--dataFile=$DataFile",
    "--numConnections=$NumConnections",
    "--chunkSize=$ChunkSize",
    "--targetRate=$TargetRate",
    "--maxSamples=$maxSamples",
    "--reportInterval=2"
)

$process = Start-Process -FilePath "java" `
    -ArgumentList $arguments `
    -NoNewWindow `
    -PassThru `
    -RedirectStandardOutput $LogPath `
    -RedirectStandardError $ErrPath

Write-Host "[OK] Sender started (PID: $($process.Id))" -ForegroundColor Green
Write-Host ""

# Wait for duration
Write-Host "[INFO] Test running for $DurationSeconds seconds..." -ForegroundColor Yellow
Start-Sleep -Seconds $DurationSeconds

# Stop the sender
Write-Host ""
Write-Host "[INFO] Stopping sender..." -ForegroundColor Yellow
Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

Write-Host "[OK] Sender stopped" -ForegroundColor Green
Write-Host ""

# Analyze results
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Performance Analysis" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if (Test-Path $LogPath) {
    $content = Get-Content $LogPath
    
    # Extract throughput lines
    $throughputLines = $content | Select-String "samples/sec" | Where-Object { $_ -match "Sent \d+ samples total" }
    
    if ($throughputLines.Count -gt 0) {
        Write-Host "[INFO] Throughput measurements:" -ForegroundColor Yellow
        
        $rates = @()
        foreach ($line in $throughputLines) {
            if ($line -match "Sent (\d+) samples total \| ([\d.]+) samples/sec") {
                $samples = $matches[1]
                $rate = $matches[2]
                $rates += [double]$rate
                Write-Host "  $samples samples: $rate samples/sec"
            }
        }
        
        if ($rates.Count -gt 0) {
            $average = ($rates | Measure-Object -Average).Average
            $min = ($rates | Measure-Object -Minimum).Minimum
            $max = ($rates | Measure-Object -Maximum).Maximum
            
            Write-Host ""
            Write-Host "========================================" -ForegroundColor Green
            Write-Host "  Test Results" -ForegroundColor White
            Write-Host "========================================" -ForegroundColor Green
            Write-Host "  Measurements:     $($rates.Count)" -ForegroundColor Cyan
            Write-Host "  Average Rate:     $("{0:N2}" -f $average) samples/sec" -ForegroundColor Cyan
            Write-Host "  Min Rate:         $("{0:N2}" -f $min) samples/sec" -ForegroundColor Cyan
            Write-Host "  Max Rate:         $("{0:N2}" -f $max) samples/sec" -ForegroundColor Cyan
            Write-Host "  Target Rate:      $("{0:N0}" -f $TargetRate) samples/sec" -ForegroundColor Cyan
            
            $achievedPercent = ($average / $TargetRate) * 100
            Write-Host "  Achieved:         $("{0:N1}" -f $achievedPercent)% of target" -ForegroundColor Cyan
            Write-Host "========================================" -ForegroundColor Green
            Write-Host ""
            
            # Performance improvement
            $baselineRate = 65000
            if ($average -gt $baselineRate) {
                $improvement = (($average - $baselineRate) / $baselineRate) * 100
                Write-Host "[SUCCESS] Performance improved by $("{0:N1}" -f $improvement)% over baseline ($baselineRate samples/sec)" -ForegroundColor Green
            }
        }
    } else {
        Write-Host "[WARN] No throughput data found in log" -ForegroundColor Yellow
    }
    
    # Check for errors
    Write-Host ""
    Write-Host "Log file: $LogPath" -ForegroundColor Cyan
    
    if (Test-Path $ErrPath) {
        $errContent = Get-Content $ErrPath
        if ($errContent) {
            Write-Host "Error log: $ErrPath (has content)" -ForegroundColor Yellow
        } else {
            Write-Host "Error log: $ErrPath (empty)" -ForegroundColor Green
        }
    }
} else {
    Write-Host "[ERROR] Log file not found: $LogPath" -ForegroundColor Red
}

Write-Host ""
Write-Host "Test completed." -ForegroundColor Green
Write-Host ""
