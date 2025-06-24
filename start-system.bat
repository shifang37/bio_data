@echo off
echo ========================================
echo   数据库可视化系统启动脚本
echo ========================================
echo.

echo 正在检查Java环境...
java -version
if %errorlevel% neq 0 (
    echo 错误: 未找到Java环境，请安装Java 17+
    pause
    exit /b 1
)

echo.
echo 正在检查Node.js环境...
node --version
if %errorlevel% neq 0 (
    echo 错误: 未找到Node.js环境，请安装Node.js 16+
    pause
    exit /b 1
)

echo.
echo 正在启动后端服务 (Spring Boot)...
echo 后端将在 http://localhost:8080 启动
start cmd /k "title Spring Boot Backend && .\mvnw.cmd spring-boot:run"

echo.
echo 等待后端启动...
timeout /t 10 /nobreak >nul

echo.
echo 正在启动前端服务 (Vue 3)...
echo 前端将在 http://localhost:3000 启动
start cmd /k "title Vue Frontend && cd frontend && npm run dev"

echo.
echo ========================================
echo 系统启动完成！
echo 后端地址: http://localhost:8080
echo 前端地址: http://localhost:3000
echo ========================================
echo.
echo 请确保MySQL数据库已启动并配置正确
echo 配置文件: src/main/resources/application.properties
echo.
pause 