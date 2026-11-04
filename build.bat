@echo off
chcp 65001 >nul
echo === Сборка Time Rewind ===
java -version 2>&1 | findstr /C:"\"25" >nul
if errorlevel 1 (
  echo Java 25 не найдена. Ставлю через winget...
  winget install -e --id EclipseAdoptium.Temurin.25.JDK --accept-package-agreements --accept-source-agreements
  echo.
  echo Java установлена. ЗАКРОЙ это окно и запусти build.bat снова.
  pause
  exit /b
)
call gradlew.bat build
if errorlevel 1 (
  echo.
  echo ОШИБКА СБОРКИ. Скопируй текст выше и пришли мне.
  pause
  exit /b 1
)
echo.
echo ГОТОВО! Файл: build\libs\timerewind-1.0.0.jar
explorer build\libs
pause
