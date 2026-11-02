@echo off
setlocal EnableExtensions
chcp 65001 >nul
title KOMBO ARENA - Reemplazo automatico y generar APK
cd /d "%~dp0"

echo ============================================================
echo   KOMBO ARENA - REEMPLAZAR REPOSITORIO Y GENERAR APK
echo ============================================================
echo Repositorio fijo: https://github.com/francoferrari9595-cod/D-oAmor
echo Este proceso reemplaza por completo la rama main mediante force push.
echo No pedira URL ni confirmacion.
echo.

if not exist "settings.gradle.kts" (
  echo ERROR: Ejecuta este BAT desde la carpeta raiz del proyecto, junto a settings.gradle.kts.
  pause
  exit /b 1
)
if not exist "debug.keystore" (
  echo ERROR: Falta debug.keystore. Sin la misma firma en todas las versiones, Android no deja actualizar encima.
  pause
  exit /b 1
)
if not exist "app\build.gradle.kts" (
  echo ERROR: No se encuentra app\build.gradle.kts.
  pause
  exit /b 1
)
where git >nul 2>&1
if errorlevel 1 (
  echo ERROR: Git no esta instalado. Instala Git for Windows: https://git-scm.com/download/win
  pause
  exit /b 1
)

if not exist ".github\workflows" mkdir ".github\workflows"
rem El workflow .github\workflows\android.yml ya viene en el proyecto: NO se reescribe (contiene versionado, publicacion y actualizaciones).
if not exist ".github\workflows\android.yml" (
  echo ERROR: Falta .github\workflows\android.yml.
  pause
  exit /b 1
)

rem Se reinicia solamente la metadata Git local; los archivos del proyecto se conservan.
if exist ".git" rmdir /s /q ".git"
git init -b main
if errorlevel 1 goto :git_error
git config user.name "KOMBO ARENA Build Upload"
git config user.email "kombo-arena-build@users.noreply.github.com"
git add -A
if errorlevel 1 goto :git_error
git add -f "debug.keystore"
if errorlevel 1 goto :git_error
git add -f ".github/workflows/android.yml"
git commit -m "Reemplazar KOMBO ARENA y generar APK con Actions"
if errorlevel 1 goto :git_error
git remote add origin "https://github.com/francoferrari9595-cod/D-oAmor.git"
if errorlevel 1 goto :git_error

echo.
echo Subiendo y reemplazando la rama main...
git push -u --force origin main
if errorlevel 1 goto :push_error

echo.
echo SUBIDA COMPLETADA. GitHub Actions debe iniciar con este push.
echo Abriendo Actions del repositorio...
start "" "https://github.com/francoferrari9595-cod/D-oAmor/actions"
echo Cuando termine en verde, descarga el artefacto KOMBO-ARENA-APK. El archivo dentro se llama KOMBO-ARENA.apk.
echo.
pause
exit /b 0

:git_error
echo.
echo ERROR al preparar el repositorio local. Mira el mensaje anterior.
pause
exit /b 1

:push_error
echo.
echo ERROR al subir el proyecto.
echo Verifica que GitHub autentique tu cuenta y que tengas permiso de escritura.
echo Si la rama main esta protegida, GitHub puede rechazar el force push.
pause
exit /b 1
