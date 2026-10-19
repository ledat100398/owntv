@echo off
chcp 65001 >nul
title Toi uu tivi Sony
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tv-sony.ps1" toiuu
echo.
pause
