@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
title 审稿工作台 - 编译并安装到手机

echo ============================================================
echo  审稿工作台 ：一键编译 + 安装到手机
echo  流程：Gradle 编译 -^> 生成 APK -^> adb 安装 -^> 手机上自动打开
echo ============================================================
echo.

rem ---------- 1/4 找 adb（顺带把自己所在目录塞进 PATH，后面直接用 adb） ----------
set "ADB="
set "SDKDIR="
if exist "local.properties" for /f "tokens=1,* delims==" %%a in ('findstr /b /i "sdk.dir" local.properties') do set "SDKDIR=%%b"
if defined SDKDIR set "SDKDIR=%SDKDIR:\\=\%"
if defined SDKDIR set "SDKDIR=%SDKDIR:C\:=C:%"
if defined SDKDIR if exist "%SDKDIR%\platform-tools\adb.exe" set "ADB=%SDKDIR%\platform-tools\adb.exe"
if not defined ADB if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB for /f "delims=" %%a in ('where adb 2^>nul') do if not defined ADB set "ADB=%%a"
if not defined ADB goto :err_adb
for %%a in ("%ADB%") do set "ADB_DIR=%%~dpa"
set "PATH=%ADB_DIR%;%PATH%"
echo  adb : %ADB%

echo.
echo [1/3] 编译 debug 包 ...（第一次慢，之后通常几十秒）
call gradlew.bat assembleDebug --console=plain
if errorlevel 1 goto :err_build

set "APK=app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" goto :err_apk
echo.
echo  APK : %APK%

echo.
echo [2/3] 找设备 ...
adb devices
set "SERIAL="
set "EMU="
for /f "skip=1 tokens=1,2" %%a in ('adb devices') do call :pick %%a %%b
if not defined SERIAL set "SERIAL=%EMU%"
if not defined SERIAL goto :err_nodev
echo  -^> 用设备 %SERIAL%

echo.
echo [3/3] 安装并启动 ...
adb -s %SERIAL% install -r "%APK%"
if errorlevel 1 goto :err_install
adb -s %SERIAL% shell am start -n com.newsreview.workbench/com.newsreview.workbench.MainActivity
echo.
echo [完成] 手机上应该已经打开"审稿工作台"了。
echo.
pause
exit /b 0

rem ---------- 设备挑选：真机优先，其次模拟器 ----------
:pick
if /i not "%~2"=="device" exit /b 0
echo %~1 | findstr /b /i "emulator-" >nul
if not errorlevel 1 goto :pick_emu
if not defined SERIAL set "SERIAL=%~1"
exit /b 0

:pick_emu
if not defined EMU set "EMU=%~1"
exit /b 0

rem ---------- 出错提示 ----------
:err_adb
echo.
echo [x] 没找到 adb.exe（Android SDK 里的 platform-tools）。
echo     如果装 Android Studio 时把 SDK 放到了自定义目录，请设置环境变量 ANDROID_HOME 指向它，再运行本脚本。
pause
exit /b 1

:err_build
echo.
echo [x] 编译失败。请看上面的报错信息；
echo     常见原因整理在 README-构建与演示.md 的"报错了怎么办"一节。
pause
exit /b 1

:err_apk
echo.
echo [x] 编译结束了但没找到 %APK%，请把上面 Gradle 的输出发出来。
pause
exit /b 1

:err_nodev
echo.
echo [x] 没找到已授权的安卓设备。检查这三点：
echo     1) 手机：设置 → 关于手机 → 连点 7 次"版本号" → 开发者选项 → 打开"USB 调试"
echo     2) 数据线连电脑，手机上弹窗点"允许 USB 调试"
echo     3) 或者先开一个模拟器（Tools → Device Manager），再运行本脚本
pause
exit /b 1

:err_install
echo.
echo [x] 安装失败。常见原因：手机存储不足 / 手机上拒绝了安装 / 数据线掉了，重插后再试。
pause
exit /b 1
