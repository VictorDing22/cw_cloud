param(
    [int]$DurationSeconds = 70,
    [string]$LogPath = "highrate.log",
    [string]$ErrPath = "highrate.err"
)

$ErrorActionPreference = "Stop"
$projectRoot = "e:\Code\floatdata\floatdata-streaming"

if (Test-Path $LogPath) {
    Remove-Item $LogPath -Force
}
if (Test-Path $ErrPath) {
    Remove-Item $ErrPath -Force
}

$maxWaitSeconds = 30
Write-Host "[INFO] Waiting for Netty server on port 9090 (up to $maxWaitSeconds seconds)..."
$portReady = $false
for ($i = 0; $i -lt $maxWaitSeconds; $i++) {
    if (Test-NetConnection -ComputerName "localhost" -Port 9090 -WarningAction SilentlyContinue -InformationLevel Quiet) {
        $portReady = $true
        break
    }
    Start-Sleep -Seconds 1
}
if (-not $portReady) {
    Write-Host "[ERROR] Netty server on port 9090 is not reachable after $maxWaitSeconds seconds."
    exit 1
}

$javaArgs = "-cp target\floatdata-streaming-1.0.0.jar com.floatdata.client.HighRateDataSender --dataFile=tdms-export.bin --chunkSize=2000 --targetRate=2000000 --maxSamples=6000000 --reportInterval=2"

Write-Host "[INFO] Starting HighRateDataSender for $DurationSeconds seconds..."
$proc = Start-Process -FilePath "java" -ArgumentList $javaArgs -WorkingDirectory $projectRoot `
        -RedirectStandardOutput $LogPath -RedirectStandardError $ErrPath -PassThru

Start-Sleep -Seconds $DurationSeconds

if (-not $proc.HasExited) {
    Stop-Process -Id $proc.Id -Force
    Write-Host "[INFO] Sender stopped after duration."
} else {
    Write-Host "[INFO] Sender exited before duration."
}

Start-Sleep -Seconds 2

if (-not (Test-Path $LogPath)) {
    Write-Host "[ERROR] Log file not found."
    if (Test-Path $ErrPath) {
        Write-Host "[INFO] Stderr output:"
        Get-Content $ErrPath
    }
    exit 1
}

Write-Host "[INFO] Log stored at $LogPath"
if (Test-Path $ErrPath) {
    Write-Host "[INFO] Error log stored at $ErrPath"
}

$pattern = 'Sent (?<total>\d+) samples total \| (?<rate>[0-9Ee+\-.]+) samples/sec'
$rates = @()
foreach ($line in Get-Content $LogPath) {
    if ($line -match $pattern) {
        $rates += [double]$matches['rate']
    }
}

if ($rates.Count -eq 0) {
    Write-Host "[WARN] No throughput entries found in log."
    exit 0
}

$average = ($rates | Measure-Object -Average).Average
Write-Host ("[RESULT] Average throughput over {0} samples: {1:N2} samples/sec" -f $rates.Count, $average)
