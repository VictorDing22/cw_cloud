@echo off
REM Stop all FloatData Streaming System components

echo.
echo ========================================
echo Stopping FloatData Streaming System
echo ========================================
echo.

echo Killing all Java processes...
taskkill /F /IM java.exe >nul 2>&1

echo Waiting for processes to terminate...
timeout /t 2 /nobreak

echo.
echo ========================================
echo [OK] All processes stopped
echo ========================================
echo.
