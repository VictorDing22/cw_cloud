@echo off
chcp 65001 >nul
color 0C
title 工业健康监测系统 - 停止服务

echo.
echo ========================================
echo   停止所有服务
echo ========================================
echo.

:: 停止前端 (Node)
echo [1/5] 停止前端服务...
taskkill /F /IM node.exe >nul 2>&1
echo   [OK] 前端已停止

:: 停止后端 (Java微服务)
echo [2/5] 停止后端微服务...
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":48080.*LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":48081.*LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":48082.*LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":8080.*LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)
echo   [OK] 后端已停止

:: 停止Kafka
echo [3/5] 停止Kafka...
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":9092.*LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":2181.*LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)
echo   [OK] Kafka已停止

:: 停止Nacos
echo [4/5] 停止Nacos...
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":8848.*LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)
echo   [OK] Nacos已停止

:: 保留基础服务
echo [5/5] 保留基础服务...
echo   [INFO] MySQL和Redis保持运行

echo.
echo ========================================
echo   验证停止结果
echo ========================================
echo.

netstat -ano 2>nul | findstr ":48080.*LISTENING" >nul && (echo   [WARN] Gateway仍在运行) || (echo   [OK] Gateway已停止)
netstat -ano 2>nul | findstr ":48081.*LISTENING" >nul && (echo   [WARN] System仍在运行) || (echo   [OK] System已停止)
netstat -ano 2>nul | findstr ":48082.*LISTENING" >nul && (echo   [WARN] Infra仍在运行) || (echo   [OK] Infra已停止)
netstat -ano 2>nul | findstr ":9092.*LISTENING" >nul && (echo   [WARN] Kafka仍在运行) || (echo   [OK] Kafka已停止)
netstat -ano 2>nul | findstr ":8848.*LISTENING" >nul && (echo   [WARN] Nacos仍在运行) || (echo   [OK] Nacos已停止)

echo.
echo [保持运行]
echo   MySQL (3306) - 系统服务
echo   Redis (6379) - 系统服务
echo.
pause
