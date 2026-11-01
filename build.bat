@echo off
where gradle >nul 2>nul || (echo Instala Gradle 8.10+ y JDK 21 primero. & exit /b 1)
call gradle build || exit /b 1
echo.
echo Listo: build\libs\luzviva-1.0.0.jar
