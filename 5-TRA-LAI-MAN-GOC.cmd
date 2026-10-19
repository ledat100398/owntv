@echo off
chcp 65001 >nul
title Tra lai man hinh goc
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0launcher.ps1" goc
echo.
pause
