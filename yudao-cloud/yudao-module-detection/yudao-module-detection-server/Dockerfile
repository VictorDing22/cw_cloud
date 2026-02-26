# 使用 OpenJDK 11 镜像（根据要求）
FROM openjdk:11-jre-slim

WORKDIR /app

# 复制构建好的 JAR 文件
COPY target/yudao-module-detection-server-*.jar /app/app.jar

# 暴露端口
EXPOSE 9090
EXPOSE 48080
EXPOSE 9999
EXPOSE 9998

# 启动应用
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
