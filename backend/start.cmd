@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
echo ============================================
echo  WebGIS 内涝监测与应急管理系统 - 后端
echo ============================================
where java >nul 2>&1
if errorlevel 1 (
  echo 未找到 Java，请先安装 JDK 17 并配置 PATH。
  exit /b 1
)
java -version
echo.
echo 正在通过 Maven Wrapper 启动 Spring Boot...
call mvnw.cmd spring-boot:run
exit /b %errorlevel%
