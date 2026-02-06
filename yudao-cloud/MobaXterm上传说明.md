# MobaXterm 上传 MySQL、Redis、Nacos 文件说明

## 方法一：使用 MobaXterm 图形界面（推荐）

### 步骤：
1. 打开 MobaXterm，点击左侧的 **Sessions** 按钮
2. 点击 **New Session**，选择 **SFTP**
3. 输入服务器信息：
   - Remote host: 你的服务器IP地址
   - Username: 你的用户名（通常是 root）
   - Port: 22（默认SSH端口）
4. 连接后，在左侧本地文件浏览器中找到以下文件/目录：
   - `docker-compose.yml`
   - `sql/mysql/` 目录（包含所有 .sql 文件）
5. 将这些文件/目录拖拽到右侧服务器目录（建议放在 `/opt/yudao-cloud` 或你指定的目录）

## 方法二：使用命令行（在 MobaXterm 终端中执行）

### 在 MobaXterm 终端中执行以下命令：

```bash
# 设置变量（请根据实际情况修改）
SERVER_IP="your-server-ip"
USERNAME="root"
TARGET_DIR="/opt/yudao-cloud"

# 创建目标目录
ssh $USERNAME@$SERVER_IP "mkdir -p $TARGET_DIR/sql/mysql"

# 上传 docker-compose.yml
scp docker-compose.yml $USERNAME@$SERVER_IP:$TARGET_DIR/

# 上传 MySQL SQL 脚本
scp sql/mysql/*.sql $USERNAME@$SERVER_IP:$TARGET_DIR/sql/mysql/
```

### 或者使用一行命令（Windows PowerShell）：

```powershell
# 设置变量
$SERVER_IP = "your-server-ip"
$USERNAME = "root"
$TARGET_DIR = "/opt/yudao-cloud"

# 创建目录并上传文件
ssh "${USERNAME}@${SERVER_IP}" "mkdir -p ${TARGET_DIR}/sql/mysql"
scp docker-compose.yml "${USERNAME}@${SERVER_IP}:${TARGET_DIR}/"
Get-ChildItem sql\mysql\*.sql | ForEach-Object { scp $_.FullName "${USERNAME}@${SERVER_IP}:${TARGET_DIR}/sql/mysql/" }
```

## 需要上传的文件清单

### 必需文件：
1. **docker-compose.yml** - Docker Compose 配置文件（包含 MySQL、Redis、Nacos 配置）
2. **sql/mysql/** 目录下的所有 SQL 文件：
   - `ruoyi-vue-pro.sql` - 主数据库脚本
   - `quartz.sql` - Quartz 调度器脚本
   - `iot_alert_tables.sql` - IoT 告警表脚本
   - `monitor_realtime_menu.sql` - 监控菜单脚本
   - `新增菜单SQL脚本.sql` - 菜单脚本

## 上传后操作

在服务器上执行：

```bash
cd /opt/yudao-cloud  # 或你指定的目录
docker-compose up -d mysql redis nacos
```

## 注意事项

1. **确保服务器已安装 Docker 和 Docker Compose**
2. **确保服务器防火墙开放了以下端口**：
   - 3306 (MySQL)
   - 6379 (Redis)
   - 8848, 9848 (Nacos)
3. **首次连接可能需要输入密码或配置 SSH 密钥**
4. **如果使用中文文件名，确保服务器支持 UTF-8 编码**
