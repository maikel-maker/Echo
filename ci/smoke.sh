#!/bin/bash
# Lance Écho sur un téléphone Android virtuel, fait des captures et vérifie qu'il n'y a pas de plantage.
set -x
PKG=be.maikel.echo
mkdir -p out
adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk || { echo "INSTALL_FAILED" > out/result.txt; exit 1; }
adb shell pm grant $PKG android.permission.RECORD_AUDIO
adb logcat -c

shot() { sleep "$2"; adb exec-out screencap -p > "out/$1.png"; }
go() { adb shell am start -S -W -n $PKG/.MainActivity "$@"; }
down() { adb shell input swipe 500 1900 500 500 300; }

go;                         shot 01-accueil 4
down;                       shot 02-accueil-2 2
go --es screen game;        shot 03-jeu-ecoute 2
sleep 3;                    shot 04-jeu-a-toi 0
sleep 6;                    shot 05-jeu-perdu 0
go --es screen over;        shot 06-fin 4
go --es screen duel;        shot 07-duel 4

adb logcat -d -b crash > out/crash.txt
if grep -q "FATAL EXCEPTION" out/crash.txt; then echo "CRASH" > out/result.txt; cat out/crash.txt; exit 1; fi
echo "OK" > out/result.txt
