@echo off
cd /d %~dp0

echo Starting Spark Processor with Java 17+ compatibility...

java ^
  --add-exports java.base/java.nio=ALL-UNNAMED ^
  --add-exports java.base/sun.nio.ch=ALL-UNNAMED ^
  --add-opens java.base/java.lang=ALL-UNNAMED ^
  --add-opens java.base/java.lang.invoke=ALL-UNNAMED ^
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED ^
  --add-opens java.base/java.io=ALL-UNNAMED ^
  --add-opens java.base/java.net=ALL-UNNAMED ^
  --add-opens java.base/java.nio=ALL-UNNAMED ^
  --add-opens java.base/java.util=ALL-UNNAMED ^
  --add-opens java.base/java.util.concurrent=ALL-UNNAMED ^
  --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED ^
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED ^
  --add-opens java.base/sun.nio.cs=ALL-UNNAMED ^
  --add-opens java.base/sun.security.action=ALL-UNNAMED ^
  --add-opens java.base/sun.util.calendar=ALL-UNNAMED ^
  --add-opens java.base/javax.security.auth=ALL-UNNAMED ^
  --add-opens java.base/javax.security.auth.login=ALL-UNNAMED ^
  --add-opens java.base/java.lang.ref=ALL-UNNAMED ^
  -Djava.security.manager=allow ^
  -Dio.netty.tryReflectionSetAccessible=true ^
  -Djdk.reflect.useDirectMethodHandle=false ^
  -DHADOOP_HOME=C:\hadoop ^
  -cp target\floatdata-streaming-1.0.0.jar ^
  com.floatdata.spark.StreamProcessor
