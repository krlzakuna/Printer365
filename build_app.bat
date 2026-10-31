@echo off
set "JAVA_HOME=C:\Users\Msi\Printer365\tools\jdk"
set "PATH=C:\Users\Msi\Printer365\tools\jdk\bin;%PATH%"
set "ANDROID_HOME=C:\Users\Msi\AppData\Local\Android\Sdk"

cd /d "C:\Users\Msi\Printer365\Printer365App"

call "C:\Users\Msi\Printer365\Printer365App\gradlew.bat" assembleDebug --no-daemon



