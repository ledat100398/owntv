@echo off
chcp 65001 >nul
title OwnTV
cd /d "%~dp0"
set "PS=powershell -NoProfile -ExecutionPolicy Bypass -File"

:menu
cls
echo.
echo   OwnTV - Your TV. Your apps. Nothing else.
echo   ------------------------------------------------------------
echo   1. Cài OwnTV và đặt làm màn hình chính   (install + set as home)
echo   2. Trả lại màn hình gốc                   (back to the stock home)
echo.
echo   3. Tắt app chạy ngầm - tivi Sony           (debloat, optional)
echo   4. Bật lại các app đã tắt ở mục 3          (undo 3)
echo   5. Đo độ mượt                              (measure performance)
echo.
echo   0. Thoát                                   (exit)
echo   ------------------------------------------------------------
choice /c 123450 /n /m "  Chọn số (choose): "
if errorlevel 6 exit /b
if errorlevel 5 (%PS% "%~dp0tools\launcher.ps1" do & goto xong)
if errorlevel 4 (%PS% "%~dp0tools\tv-sony.ps1" khoiphuc & goto xong)
if errorlevel 3 (%PS% "%~dp0tools\tv-sony.ps1" toiuu & goto xong)
if errorlevel 2 (%PS% "%~dp0tools\launcher.ps1" goc & goto xong)
%PS% "%~dp0tools\launcher.ps1" cai
if errorlevel 1 goto xong
set HOME_DONGY=1
%PS% "%~dp0tools\launcher.ps1" home
set HOME_DONGY=

:xong
echo.
pause
goto menu
