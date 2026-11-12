#!/bin/bash
mkdir -p shots
adb root || true
sleep 3
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell pm grant com.mychord.app android.permission.POST_NOTIFICATIONS || true
adb logcat -c
P=com.mychord.app
shot() { sleep "$2"; adb exec-out screencap -p > "shots/$1.png"; adb shell pidof $P >/dev/null && echo "OK $1" || echo "MATI $1"; }
adb shell am start -n $P/.MainActivity; shot 1-utama 20
adb shell am start -n $P/.MainActivity --ei tab 1; shot 2-cari 4
adb shell am start -n $P/.MainActivity --ei tab 2; shot 3-pustaka 4
adb shell am start -n $P/.MainActivity --ei tab 3; shot 4-tetapan 4
adb shell am start -n $P/.EffectsActivity; shot 5-kesan 4
adb shell am start -n $P/.AddonsActivity; shot 6-addon 6
adb shell am start -n $P/.SpotifyActivity; shot 7-spotify 4
adb shell am start -n $P/.ListActivity --es mode radio; shot 8-radio 10
adb shell am start -n $P/.ListActivity --es mode local; shot 9-lokal 4
adb shell am start -n $P/.LoginActivity --es service yt; shot 12-login-yt 10
adb shell am start -n $P/.LoginActivity --es service sp; shot 13-login-sp 10
adb shell am start -n $P/.ListActivity --es mode ytm_liked; shot 14-ytm-liked 6
adb shell am start -n $P/.ListActivity --es mode sp_lists; shot 15-sp-lists 6
adb shell am start -n $P/.AddonsActivity --es android.intent.extra.TEXT "https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"; shot 16-addon-tambah 4
adb shell input keyevent 4
adb shell am force-stop $P
adb shell am start -n $P/.MainActivity --es testQuery "siti nurhaliza"; shot 10-main 30
adb shell am start -n $P/.PlayerActivity; shot 11-pemain 8
adb shell dumpsys media_session | grep -iE "state=|description" | head -10
adb logcat -d > shots/logcat.txt
echo "=====CRASH====="
grep -A40 "FATAL EXCEPTION" shots/logcat.txt | head -120
echo "=====TEST====="
grep -E "MyChordTest|ExoPlayerImpl|PlaybackException|MediaCodec.*audio" shots/logcat.txt | head -40
grep -iE "Caused by" shots/logcat.txt | grep -v gms | head -20
echo "=====SWIPE====="
adb shell am start -n $P/.PlayerActivity; sleep 5
adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity" 
adb shell wm size; adb shell input swipe 160 150 160 600 300; sleep 3
adb logcat -d | grep MyChordSwipe | tail -10
adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity"
adb shell pidof $P >/dev/null && echo "OK selepas-leret" || echo "MATI selepas-leret"
adb logcat -d | grep -A30 "FATAL EXCEPTION" | head -60
