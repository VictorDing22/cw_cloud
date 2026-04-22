# Fix Kafka path length issue by moving to C:\kafka
$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Fix Kafka Path Issue" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$currentPath = "$PSScriptRoot\kafka_3.6.0"
$newPath = "C:\kafka"

# Check if current Kafka exists
if (Test-Path $currentPath) {
    Write-Host "Found Kafka at: $currentPath" -ForegroundColor Yellow
    Write-Host "Moving to: $newPath" -ForegroundColor Yellow
    Write-Host ""
    
    # Check if target already exists
    if (Test-Path $newPath) {
        Write-Host "[WARNING] C:\kafka already exists" -ForegroundColor Red
        $response = Read-Host "Delete and move again? (y/n)"
        if ($response -eq 'y') {
            Write-Host "Deleting old C:\kafka..." -ForegroundColor Yellow
            Remove-Item -Path $newPath -Recurse -Force
        } else {
            Write-Host "Cancelled" -ForegroundColor Yellow
            exit 0
        }
    }
    
    # Move Kafka
    Write-Host "Moving Kafka (may take 1-2 minutes)..." -ForegroundColor Yellow
    Move-Item -Path $currentPath -Destination $newPath -Force
    Write-Host "[OK] Kafka moved to C:\kafka" -ForegroundColor Green
    
} elseif (Test-Path $newPath) {
    Write-Host "[OK] Kafka already at C:\kafka" -ForegroundColor Green
} else {
    Write-Host "[ERROR] Kafka not found" -ForegroundColor Red
    Write-Host "Please ensure kafka_3.6.0 exists in current directory" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Fix Complete!" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Now you can start the system:" -ForegroundColor Cyan
Write-Host "  .\start.ps1" -ForegroundColor White
Write-Host ""
