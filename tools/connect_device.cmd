@echo off
REM ==============================================================================
REM Lynx Universal - Smart ADB Wireless Device Connector Launcher (CMD Wrapper)
REM ==============================================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0connect_device.ps1" %*
