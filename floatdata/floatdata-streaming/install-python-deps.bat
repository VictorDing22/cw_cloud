@echo off
REM Install Python dependencies for TDMS reader

echo.
echo ========================================
echo Installing Python Dependencies
echo ========================================
echo.

echo [1/1] Installing nptdms library...
pip install nptdms

echo.
echo ========================================
echo Installation Complete
echo ========================================
echo.
echo Next: Run the TDMS reader with:
echo   python tdms-reader.py
echo.
pause
