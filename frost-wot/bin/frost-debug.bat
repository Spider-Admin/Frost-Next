@echo off
cd /d %~dp0
java -Xmx384M -ea -jar frost.jar %1 %2 %3 %4 %5 %6 %7
pause
