@echo off
chcp 65001 >nul
setlocal
echo ============================================
echo   Ev Kumanda - TV kurulum (ADB)
echo ============================================
echo Gereken: platform-tools (adb.exe) bu klasorde veya PATH'te,
echo EvKumanda-TV.apk bu dosyanin yaninda.
echo.
set /p TVIP=TV IP adresi (TV Ayarlar - Ag bilgisinde yazar): 
if "%TVIP%"=="" goto :eof

adb connect %TVIP%:5555
echo.
echo TV ekraninda "USB hata ayiklamaya izin ver" cikarsa "Her zaman izin ver" de.
pause

set PKG=com.evkumanda.app.tv
echo [1/4] APK kuruluyor...
adb -s %TVIP%:5555 install -r EvKumanda-TV.apk

echo [2/4] Varsayilan ana ekran yapiliyor...
adb -s %TVIP%:5555 shell cmd package set-home-activity %PKG%/com.evkumanda.app.tv.TvLauncherActivity

echo [3/4] Ekran koruyucu ayarlaniyor...
adb -s %TVIP%:5555 shell settings put secure screensaver_components %PKG%/com.evkumanda.app.tv.SaverDreamService
adb -s %TVIP%:5555 shell settings put secure screensaver_enabled 1

echo [4/4] TV bilgileri:
adb -s %TVIP%:5555 shell getprop ro.product.model
adb -s %TVIP%:5555 shell wm size
adb -s %TVIP%:5555 shell cat /sys/class/net/wlan0/address
adb -s %TVIP%:5555 shell cat /sys/class/net/eth0/address
echo.
echo Yukaridaki 17 karakterlik XX:XX:... degeri TV MAC adresidir (WoL icin).
echo Wm size 3840x2160 degilse TV Ayarlar - Cihaz tercihleri - Ekran - Cozunurluk'ten 4K sec.
echo Panel: tarayicida http://%TVIP%:8765
pause
