#!/bin/bash
mkdir -p out
P=com.mychord.app
adb root || true; sleep 3
adb shell settings put global hide_error_dialogs 1
adb shell settings put secure anr_show_background 0
sleep 20
adb install -r app/build/outputs/apk/release/app-release.apk
for p in POST_NOTIFICATIONS RECORD_AUDIO; do adb shell pm grant $P android.permission.$p || true; done
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 92 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
cap() { sleep "$2"; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null; sleep 1; adb exec-out screencap -p > "out/$1.png"; echo "shot $1"; }
start() { adb shell am force-stop $P; sleep 1; adb shell am start -n $P/.MainActivity --es testLang en --es testTheme "$1" $2 >/dev/null; }
# warm-up (bahasa)
start ember; sleep 8
start ember; cap 01-home-ember 30
start kaca; cap 02-home-glass 30
adb shell am start -n $P/.MainActivity --ei tab 3 >/dev/null; cap 03-settings-glass 5
adb shell input swipe 540 1500 540 900 300; cap 04-settings-glass-2 3
start kacacerah; cap 05-home-glass-light 30
start malam "--es testQuery 'hindia evaluasi'"; sleep 25
adb shell am start -n $P/.PlayerActivity >/dev/null; cap 06-player 10
start kaca; sleep 6
adb shell am start -n $P/.RecognizeActivity >/dev/null; cap 07-recognize 8
start kaca; sleep 4
adb shell am start -n $P/.ListActivity --es mode podcasts >/dev/null; cap 08-podcasts 14
start lagun; sleep 4
adb shell am start -n $P/.AddonsActivity >/dev/null; cap 09-addons 8
start malam; sleep 4
adb shell am start -n $P/.EffectsActivity >/dev/null; cap 10-effects 5
start amoled; sleep 4
adb shell am start -n $P/.ListActivity --es mode radio >/dev/null; cap 11-radio 14
start kaca "--ez testTogether true"; cap 12-together 10
start ember; sleep 4
adb shell am start -n $P/.SpotifyActivity >/dev/null; cap 13-spotify 5
start kopi; sleep 4
adb shell am start -n $P/.MainActivity --ei tab 2 >/dev/null; cap 14-library 5
ls -la out
