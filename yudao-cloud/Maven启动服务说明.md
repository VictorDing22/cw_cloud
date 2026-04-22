# 使用 Maven 单独启动服务说明

本文档说明如何使用 Maven 单独启动 infra、system 和 detection 三个服务。

## 方法一：使用 PowerShell 脚本（推荐）

运行项目根目录下的 `start-services-with-maven.ps1` 脚本：

```powershell
.\start-services-with-maven.ps1
```

该脚本会在独立的 PowerShell 窗口中启动每个服务，方便查看日志。

## 方法二：手动使用 Maven 命令

### 1. 启动 Infra 服务

```powershell
cd yudao-module-infra\yudao-module-infra-server
mvn spring-boot:run -Dspring-boot.run.main-class=cn.iocoder.yudao.module.infra.InfraServerApplication -Dspring-boot.run.arguments="--spring.profiles.active=local --server.port=48082"
```

### 2. 启动 System 服务

```powershell
cd yudao-module-system\yudao-module-system-server
mvn spring-boot:run -Dspring-boot.run.main-class=cn.iocoder.yudao.module.system.SystemServerApplication -Dspring-boot.run.arguments="--spring.profiles.active=local --server.port=48081"
```

### 3. 启动 Detection 服务

```powershell
cd yudao-module-detection\yudao-module-detection-server
mvn spring-boot:run -Dspring-boot.run.main-class=cn.iocoder.yudao.module.detection.GrpcServerApplication -Dspring-boot.run.arguments="--spring.profiles.active=local --server.port=48083"
```

## 方法三：使用 Maven 命令（简化版）

如果已经配置了 Spring Boot Maven 插件，可以直接使用：

### Infra 服务
```powershell
cd yudao-module-infra\yudao-module-infra-server
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local --server.port=48082"
```

### System 服务
```powershell
cd yudao-module-system\yudao-module-system-server
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local --server.port=48081"
```

### Detection 服务
```powershell
cd yudao-module-detection\yudao-module-detection-server
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local --server.port=48083"
```

## 服务端口

- **System 服务**: 48081
- **Infra 服务**: 48082
- **Detection 服务**: 48083

## 注意事项

1. **首次运行**：Maven 需要下载依赖，可能需要一些时间
2. **多窗口运行**：如果要在同一终端运行多个服务，需要在不同的终端窗口中执行
3. **配置文件**：确保 `application-local.yml` 配置文件存在且配置正确
4. **依赖服务**：确保 Nacos、Redis、MySQL 等依赖服务已启动
5. **停止服务**：在运行 Maven 的终端中按 `Ctrl+C` 停止服务

## 验证服务是否启动成功

启动后，可以通过以下方式验证：

1. 查看控制台日志，确认没有错误
2. 访问服务健康检查端点（如果配置了）
3. 检查端口是否被占用：
   ```powershell
   netstat -ano | findstr "48081"
   netstat -ano | findstr "48082"
   netstat -ano | findstr "48083"
   ```

## 常见问题

### 问题1：端口被占用
**解决方案**：修改 `--server.port` 参数使用其他端口，或停止占用端口的进程

### 问题2：找不到主类
**解决方案**：确保在正确的服务目录下执行命令，或使用完整的主类路径

### 问题3：依赖下载失败
**解决方案**：检查网络连接，或配置 Maven 镜像源
