@echo off
chcp 65001 >nul
title Kiem tra Glass Home
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0kiem-tra.ps1"
echo.
pause
