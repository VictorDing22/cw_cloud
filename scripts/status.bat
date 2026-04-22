@echo off
chcp 65001 >nul
color 0E
title 系统状态检查

echo.
echo ========================================
echo   系统状态检查
echo ========================================
echo.

echo [基础设施]

netstat -ano 2>nul | findstr ":3306" | findstr "LISTENING" >nul
if %errorlevel%==0 (echo   [OK] MySQL 3306) else (echo   [X] MySQL 未运行)

netstat -ano 2>nul | findstr "127.0.0.1:6379" | findstr "LISTENING" >nul
if %errorlevel%==0 (echo   [OK] Redis 6379) else (echo   [X] Redis 未运行)

netstat -ano 2>nul | findstr ":8848" | findstr "LISTENING" >nul
if %errorlevel%==0 (echo   [OK] Nacos 8848) else (echo   [X] Nacos 未运行)

netstat -ano 2>nul | findstr "127.0.0.1:9092" | findstr "LISTENING" >nul
if %errorlevel%==0 (echo   [OK] Kafka 9092) else (echo   [-] Kafka 未运行)

netstat -ano 2>nul | findstr ":2181" | findstr "LISTENING" >nul
if %errorlevel%==0 (echo   [OK] Zookeeper 2181) else (echo   [-] Zookeeper 未运行)

echo.
echo [后端服务]

netstat -ano 2>nul | findstr ":48080" | findstr "LISTENING" >nul
if %errorlevel%==0 (echo   [OK] Gateway 48080) else (echo   [X] Gateway 未运行)

netstat -ano 2>nul | findstr ":48081" | findstr "LISTENING" >nul
if %errorlevel%==0 (echo   [OK] System 48081) else (echo   [X] System 未运行)

netstat -ano 2>nul | findstr ":48082" | findstr "LISTENING" >nul
if %errorlevel%==0 (echo   [OK] Infra 48082) else (echo   [X] Infra 未运行)

echo.
echo [数据处理]

netstat -ano 2>nul | findstr "0.0.0.0:8080" | findstr "LISTENING" >nul
if %errorlevel%==0 (echo   [OK] Backend滤波 8080) else (echo   [-] Backend滤波 未运行)

netstat -ano 2>nul | findstr ":3002" | findstr "LISTENING" >nul
if %errorlevel%==0 (echo   [OK] TDMS API 3002) else (echo   [-] TDMS API 未运行)

netstat -ano 2>nul | findstr ":8081" | findstr "LISTENING" >nul
if %errorlevel%==0 (echo   [OK] WebSocket 8081) else (echo   [-] WebSocket 未运行)

echo.
echo [前端服务]

netstat -ano 2>nul | findstr ":3000" | findstr "LISTENING" >nul
if %errorlevel%==0 (
    echo   [OK] 前端 3000
) else (
    netstat -ano 2>nul | findstr "0.0.0.0:80" | findstr "LISTENING" >nul
    if %errorlevel%==0 (echo   [OK] 前端 80) else (echo   [X] 前端 未运行)
)

echo.
echo ========================================
echo   访问地址
echo ========================================
echo.
echo   前端: http://localhost:3000
echo   Nacos: http://localhost:8848/nacos
echo   API: http://localhost:48080
echo.
pause
