@echo off
chcp 65001 >nul
title Khoi phuc tivi Sony
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tv-sony.ps1" khoiphuc
echo.
pause
