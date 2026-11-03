@echo off
title MockForge - Backend and Frontend Logs
cd /d "%~dp0.."
python -u tools\run_demo_component.py backend
echo.
echo Backend window finished. Check the messages above for any errors.
