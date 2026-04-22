# 修复Kafka路径太长的问题
$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  修复Kafka路径问题" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$currentPath = "$PSScriptRoot\kafka_3.6.0"
$newPath = "C:\kafka"

# 检查当前Kafka是否存在
if (Test-Path $currentPath) {
    Write-Host "发现Kafka在: $currentPath" -ForegroundColor Yellow
    Write-Host "准备移动到: $newPath" -ForegroundColor Yellow
    Write-Host ""
    
    # 检查目标路径是否已存在
    if (Test-Path $newPath) {
        Write-Host "[警告] C:\kafka 已存在" -ForegroundColor Red
        $response = Read-Host "是否删除并重新移动？(y/n)"
        if ($response -eq 'y') {
            Write-Host "删除旧的 C:\kafka..." -ForegroundColor Yellow
            Remove-Item -Path $newPath -Recurse -Force
        } else {
            Write-Host "取消操作" -ForegroundColor Yellow
            exit 0
        }
    }
    
    # 移动Kafka
    Write-Host "正在移动Kafka（可能需要1-2分钟）..." -ForegroundColor Yellow
    Move-Item -Path $currentPath -Destination $newPath -Force
    Write-Host "[OK] Kafka已移动到 C:\kafka" -ForegroundColor Green
    
} elseif (Test-Path $newPath) {
    Write-Host "[OK] Kafka已经在 C:\kafka" -ForegroundColor Green
} else {
    Write-Host "[错误] 找不到Kafka" -ForegroundColor Red
    Write-Host "请确保kafka_3.6.0在当前目录" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  修复完成！" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "现在可以正常启动了：" -ForegroundColor Cyan
Write-Host "  .\start.ps1" -ForegroundColor White
Write-Host ""
