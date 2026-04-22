# MySQL、Redis、Nacos 服务文件上传脚本 (PowerShell版本)
# 使用方法: .\upload-services-to-server.ps1 -ServerIP "your-server-ip" -Username "root" -TargetDir "/opt/yudao-cloud"

param(
    [string]$ServerIP = "your-server-ip",
    [string]$Username = "root",
    [string]$TargetDir = "/opt/yudao-cloud"
)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "开始上传 MySQL、Redis、Nacos 相关文件到服务器" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "服务器: ${Username}@${ServerIP}" -ForegroundColor Yellow
Write-Host "目标目录: ${TargetDir}" -ForegroundColor Yellow
Write-Host ""

# 检查 scp 命令是否可用（需要安装 OpenSSH 客户端）
if (-not (Get-Command scp -ErrorAction SilentlyContinue)) {
    Write-Host "错误: 未找到 scp 命令。请安装 OpenSSH 客户端。" -ForegroundColor Red
    Write-Host "安装命令: Add-WindowsCapability -Online -Name OpenSSH.Client~~~~0.0.1.0" -ForegroundColor Yellow
    exit 1
}

# 创建目标目录
Write-Host "1. 在服务器上创建目标目录..." -ForegroundColor Green
ssh "${Username}@${ServerIP}" "mkdir -p ${TargetDir}/sql/mysql"

# 上传 docker-compose.yml 文件
Write-Host "2. 上传 docker-compose.yml 文件..." -ForegroundColor Green
scp docker-compose.yml "${Username}@${ServerIP}:${TargetDir}/"

# 上传 MySQL SQL 脚本目录
Write-Host "3. 上传 MySQL SQL 初始化脚本..." -ForegroundColor Green
Get-ChildItem -Path "sql\mysql\*" | ForEach-Object {
    scp $_.FullName "${Username}@${ServerIP}:${TargetDir}/sql/mysql/"
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "文件上传完成！" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "接下来可以在服务器上执行：" -ForegroundColor Yellow
Write-Host "  cd ${TargetDir}" -ForegroundColor White
Write-Host "  docker-compose up -d mysql redis nacos" -ForegroundColor White
Write-Host ""
