@echo off
chcp 65001 >nul
echo ========================================
echo   Database Visualization System
echo ========================================
echo.

echo Starting backend service...
start "Backend" cmd /k "cd /d %~dp0 && .\mvnw.cmd spring-boot:run"

echo Waiting 3 seconds...
timeout /t 3 /nobreak >nul

echo Starting frontend service...
start "Frontend" cmd /k "cd /d %~dp0\frontend && npm run dev"

echo.
echo ========================================
echo System startup complete!
echo.
echo Backend URL: http://localhost:8080
echo Frontend URL: http://localhost:3000
echo.
echo Please wait for services to fully start
echo then visit the frontend URL
echo ========================================
echo.
echo Press any key to open browser...
pause >nul
start http://localhost:3000 