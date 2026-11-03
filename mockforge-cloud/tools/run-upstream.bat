@echo off
title MockForge - Upstream API Logs
cd /d "%~dp0.."
python -u tools\run_demo_component.py upstream
echo.
echo Upstream window finished. Check the messages above for any errors.
