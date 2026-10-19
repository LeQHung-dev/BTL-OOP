@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -cp "lib/*" -d out *.java
if errorlevel 1 (
  echo Loi bien dich, xem thong bao phia tren
  pause
  exit /b 1
)
java -cp "out;lib/*" PharmacyGUI
