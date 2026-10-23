@echo off
setlocal EnableExtensions
chcp 65001 >nul
title D-oAmor - Reemplazo automatico y generar APK
cd /d "%~dp0"

echo ============================================================
echo   D-oAmor - REEMPLAZAR REPOSITORIO Y GENERAR APK
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
powershell -NoProfile -ExecutionPolicy Bypass -Command "$b='bmFtZTogR2VuZXJhciBBUEsgQW5kcm9pZCAtIEQtb0Ftb3IKCm9uOgogIHB1c2g6CiAgICBicmFuY2hlczogWyJtYWluIl0KICB3b3JrZmxvd19kaXNwYXRjaDoKCnBlcm1pc3Npb25zOgogIGNvbnRlbnRzOiByZWFkCgpjb25jdXJyZW5jeToKICBncm91cDogZC1vYW1vci1hcGstJHt7IGdpdGh1Yi5yZWYgfX0KICBjYW5jZWwtaW4tcHJvZ3Jlc3M6IHRydWUKCmpvYnM6CiAgYnVpbGQtYXBrOgogICAgbmFtZTogQ29tcGlsYXIgQVBLIGRlYnVnCiAgICBydW5zLW9uOiB1YnVudHUtbGF0ZXN0CiAgICB0aW1lb3V0LW1pbnV0ZXM6IDQ1CiAgICBlbnY6CiAgICAgIEFORFJPSURfSE9NRTogL3Vzci9sb2NhbC9saWIvYW5kcm9pZC9zZGsKICAgICAgQU5EUk9JRF9TREtfUk9PVDogL3Vzci9sb2NhbC9saWIvYW5kcm9pZC9zZGsKICAgIHN0ZXBzOgogICAgICAtIG5hbWU6IERlc2NhcmdhciBjw7NkaWdvCiAgICAgICAgdXNlczogYWN0aW9ucy9jaGVja291dEB2NAoKICAgICAgLSBuYW1lOiBDb25maWd1cmFyIEphdmEgMTcKICAgICAgICB1c2VzOiBhY3Rpb25zL3NldHVwLWphdmFAdjQKICAgICAgICB3aXRoOgogICAgICAgICAgZGlzdHJpYnV0aW9uOiB0ZW11cmluCiAgICAgICAgICBqYXZhLXZlcnNpb246ICcxNycKCiAgICAgIC0gbmFtZTogQWNlcHRhciBsaWNlbmNpYXMgQW5kcm9pZCBzaW4gaW50ZXJhY2Npw7NuCiAgICAgICAgc2hlbGw6IGJhc2gKICAgICAgICBydW46IHwKICAgICAgICAgIHNldCAtZXVvIHBpcGVmYWlsCiAgICAgICAgICBTREtNQU5BR0VSPSIkKGZpbmQgIiRBTkRST0lEX0hPTUUvY21kbGluZS10b29scyIgLXR5cGUgZiAtbmFtZSBzZGttYW5hZ2VyIDI+L2Rldi9udWxsIHwgc29ydCAtViB8IHRhaWwgLW4gMSkiCiAgICAgICAgICBpZiBbIC16ICIkU0RLTUFOQUdFUiIgXTsgdGhlbgogICAgICAgICAgICBlY2hvICI6OmVycm9yOjpObyBzZSBlbmNvbnRyw7Mgc2RrbWFuYWdlciBlbiAkQU5EUk9JRF9IT01FL2NtZGxpbmUtdG9vbHMiCiAgICAgICAgICAgIGV4aXQgMQogICAgICAgICAgZmkKICAgICAgICAgIHllcyB8ICIkU0RLTUFOQUdFUiIgLS1zZGtfcm9vdD0iJEFORFJPSURfSE9NRSIgLS1saWNlbnNlcyA+L2Rldi9udWxsIHx8IHRydWUKICAgICAgICAgICIkU0RLTUFOQUdFUiIgLS1zZGtfcm9vdD0iJEFORFJPSURfSE9NRSIgLS1pbnN0YWxsICJwbGF0Zm9ybS10b29scyIgInBsYXRmb3JtczthbmRyb2lkLTM2IiAiYnVpbGQtdG9vbHM7MzYuMC4wIgoKICAgICAgLSBuYW1lOiBDb25maWd1cmFyIEdyYWRsZQogICAgICAgIHVzZXM6IGdyYWRsZS9hY3Rpb25zL3NldHVwLWdyYWRsZUB2NAogICAgICAgIHdpdGg6CiAgICAgICAgICBncmFkbGUtdmVyc2lvbjogIjkuMy4xIgoKICAgICAgLSBuYW1lOiBQcmVwYXJhciBmaXJtYSBkZWJ1ZyBhdXRvbcOhdGljYQogICAgICAgIHNoZWxsOiBiYXNoCiAgICAgICAgcnVuOiB8CiAgICAgICAgICBzZXQgLWV1byBwaXBlZmFpbAogICAgICAgICAga2V5dG9vbCAtZ2Vua2V5cGFpciAtdiBcCiAgICAgICAgICAgIC1rZXlzdG9yZSBkZWJ1Zy5rZXlzdG9yZSBcCiAgICAgICAgICAgIC1zdG9yZXBhc3MgYW5kcm9pZCBcCiAgICAgICAgICAgIC1hbGlhcyBhbmRyb2lkZGVidWdrZXkgXAogICAgICAgICAgICAta2V5cGFzcyBhbmRyb2lkIFwKICAgICAgICAgICAgLWtleWFsZyBSU0EgLWtleXNpemUgMjA0OCAtdmFsaWRpdHkgMTAwMDAgXAogICAgICAgICAgICAtZG5hbWUgIkNOPUFuZHJvaWQgRGVidWcsTz1BbmRyb2lkLEM9VVMiCgogICAgICAtIG5hbWU6IFByZXBhcmFyIEZpcmViYXNlIHNpIGV4aXN0ZSBlbCBzZWNyZXRvCiAgICAgICAgZW52OgogICAgICAgICAgRklSRUJBU0VfR09PR0xFX1NFUlZJQ0VTX0pTT046ICR7eyBzZWNyZXRzLkZJUkVCQVNFX0dPT0dMRV9TRVJWSUNFU19KU09OIH19CiAgICAgICAgc2hlbGw6IGJhc2gKICAgICAgICBydW46IHwKICAgICAgICAgIGlmIFsgLW4gIiRGSVJFQkFTRV9HT09HTEVfU0VSVklDRVNfSlNPTiIgXTsgdGhlbgogICAgICAgICAgICBwcmludGYgJyVzJyAiJEZJUkVCQVNFX0dPT0dMRV9TRVJWSUNFU19KU09OIiA+IGFwcC9nb29nbGUtc2VydmljZXMuanNvbgogICAgICAgICAgZWxpZiBbIC1mIGFwcC9nb29nbGUtc2VydmljZXMuanNvbiBdOyB0aGVuCiAgICAgICAgICAgIGVjaG8gIlNlIGVuY29udHLDsyBhcHAvZ29vZ2xlLXNlcnZpY2VzLmpzb24uIgogICAgICAgICAgZWxzZQogICAgICAgICAgICBlY2hvICI6Ondhcm5pbmc6OkZhbHRhIEZpcmViYXNlOiBlbCBBUEsgZGUgcHJ1ZWJhIHB1ZWRlIGNvbXBpbGFyLCBwZXJvIEdvb2dsZSBTaWduLUluIHkgbGEgY29uZXhpw7NuIEZpcmViYXNlIG5vIGZ1bmNpb25hcsOhbiBoYXN0YSBhZ3JlZ2FyIGxhIGNvbmZpZ3VyYWNpw7NuIHJlYWwuIgogICAgICAgICAgZmkKCiAgICAgIC0gbmFtZTogQ29tcGlsYXIgQVBLCiAgICAgICAgcnVuOiBncmFkbGUgLS1uby1kYWVtb24gLS1zdGFja3RyYWNlIDphcHA6YXNzZW1ibGVEZWJ1ZwoKICAgICAgLSBuYW1lOiBQcmVwYXJhciBBUEsgY29uIG5vbWJyZSBmaW5hbAogICAgICAgIHJ1bjogfAogICAgICAgICAgc2V0IC1ldW8gcGlwZWZhaWwKICAgICAgICAgIHRlc3QgLXMgYXBwL2J1aWxkL291dHB1dHMvYXBrL2RlYnVnL2FwcC1kZWJ1Zy5hcGsKICAgICAgICAgIG1rZGlyIC1wIGRpc3QKICAgICAgICAgIGNwIGFwcC9idWlsZC9vdXRwdXRzL2Fway9kZWJ1Zy9hcHAtZGVidWcuYXBrIGRpc3QvRC1vQW1vci5hcGsKICAgICAgICAgIGxzIC1saCBkaXN0L0Qtb0Ftb3IuYXBrCgogICAgICAtIG5hbWU6IFB1YmxpY2FyIEFQSyBkZXNjYXJnYWJsZQogICAgICAgIHVzZXM6IGFjdGlvbnMvdXBsb2FkLWFydGlmYWN0QHY0CiAgICAgICAgd2l0aDoKICAgICAgICAgIG5hbWU6IEQtb0Ftb3ItQVBLCiAgICAgICAgICBwYXRoOiBkaXN0L0Qtb0Ftb3IuYXBrCiAgICAgICAgICBpZi1uby1maWxlcy1mb3VuZDogZXJyb3IKICAgICAgICAgIHJldGVudGlvbi1kYXlzOiAxNAo='; [IO.File]::WriteAllBytes('.github/workflows/android.yml',[Convert]::FromBase64String($b))"
if errorlevel 1 (
  echo ERROR: No se pudo escribir el workflow.
  pause
  exit /b 1
)
if not exist ".github\workflows\android.yml" (
  echo ERROR: Falta .github\workflows\android.yml.
  pause
  exit /b 1
)

rem Se reinicia solamente la metadata Git local; los archivos del proyecto se conservan.
if exist ".git" rmdir /s /q ".git"
git init -b main
if errorlevel 1 goto :git_error
git config user.name "D-oAmor Build Upload"
git config user.email "d-oamor-build@users.noreply.github.com"
git add -A
if errorlevel 1 goto :git_error
git add -f ".github/workflows/android.yml"
git commit -m "Reemplazar D-oAmor y generar APK con Actions"
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
echo Cuando termine en verde, descarga el artefacto D-oAmor-APK. El archivo dentro se llama D-oAmor.apk.
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
