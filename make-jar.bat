@echo off
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
javac --release 11 -encoding UTF-8 -d out *.java || exit /b 1
set /p VERSION=<VERSION
(echo Main-Class: SpreadsheetApp
echo Implementation-Version: %VERSION%) > manifest.txt
jar cfm SpreadsheetPro.jar manifest.txt -C out . -C resources . || exit /b 1
echo Built SpreadsheetPro.jar
