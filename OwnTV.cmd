@echo off
title OwnTV
if not exist "%~dp0tools\menu.ps1" (
  echo Hay giai nen file zip truoc, roi mo OwnTV.cmd trong thu muc da giai nen.
  echo Unzip the download first, then open OwnTV.cmd from the unzipped folder.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\menu.ps1"
if errorlevel 1 pause
