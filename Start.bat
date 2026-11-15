@echo off
cd /d "%~dp0"
where javac >nul 2>&1
if errorlevel 1 (
    echo JDK not found. Install JDK and add it to PATH.
    pause
    exit /b 1
)
javac -encoding UTF-8 Main.java Window.java
if errorlevel 1 (
    echo Compilation failed.
    pause
    exit /b 1
)
java Window
if errorlevel 1 pause
