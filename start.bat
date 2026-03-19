@echo off
chcp 65001 >nul

title 服务启动器

:: 后端：切换到 target 目录并运行 jar
start "后端服务" /D "F:\GitRepo\CheckByAi\Check_Ai\target" java -jar Check_Ai-0.0.1-SNAPSHOT.jar

:: 前端：切换到前端目录并运行 npm
start "前端服务" /D "F:\GitRepo\CheckByAi\check_ai_web" npm run dev

echo.
echo ✅ 后端和前端服务已启动！
echo    - 后端: http://localhost:8080 (默认)
echo    - 前端: 查看 npm 输出的地址（http://localhost:5173）
echo.
pause