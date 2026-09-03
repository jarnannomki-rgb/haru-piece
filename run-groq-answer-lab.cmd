@echo off
chcp 65001 >nul
cd /d "%~dp0"
python tools\groq_answer_lab.py %*
pause
