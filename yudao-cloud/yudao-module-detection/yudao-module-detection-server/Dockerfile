# 使用官方维护的 JDK 17 镜像（推荐）
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制构建好的 JAR 文件
# 注意：这里使用具体文件名以避免复制多余的文件（如 original jar 或 sources jar）
COPY target/yudao-module-detection-server-2025.09-jdk8-SNAPSHOT.jar /app/app.jar

# 暴露端口
EXPOSE 9090
EXPOSE 48080

# 启动应用
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
