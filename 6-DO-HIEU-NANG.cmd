@echo off
chcp 65001 >nul
title Do hieu nang launcher
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0launcher.ps1" do
echo.
pause
