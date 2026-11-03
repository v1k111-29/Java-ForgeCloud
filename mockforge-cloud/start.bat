@echo off
title MockForge Cloud - Start Demo
cd /d "%~dp0"

echo Starting MockForge Cloud...
echo The endpoint testing frontend is served by the backend on port 8080.
echo.

if not exist "logs" mkdir "logs"

start "MockForge - Backend and Frontend" cmd /k call "%~dp0tools\run-backend.bat"
start "MockForge - Slow Demo API" cmd /k call "%~dp0tools\run-upstream.bat"

python -u tools\start_demo.py
if errorlevel 1 pause
