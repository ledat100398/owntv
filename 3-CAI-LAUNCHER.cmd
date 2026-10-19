@echo off
chcp 65001 >nul
title Cai launcher moi
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0launcher.ps1" cai
echo.
pause
