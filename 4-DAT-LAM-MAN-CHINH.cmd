@echo off
chcp 65001 >nul
title Dat launcher lam man hinh chinh
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0launcher.ps1" home
echo.
pause
