#!/usr/bin/env bash
# Builds dist/padlab-debug.apk without Gradle. Needs: JDK, aapt, dx, zipalign, apksigner and an
# android.jar (Debian/Ubuntu: apt install aapt dalvik-exchange zipalign apksigner libandroid-23-java).
set -euo pipefail
cd "$(dirname "$0")"

SDK=${ANDROID_SDK:-/usr/lib/android-sdk}
BT=$(ls -d "$SDK"/build-tools/* | head -1)
JAR=${ANDROID_JAR:-$SDK/platforms/android-23/android.jar}
OUT=build

rm -rf "$OUT" && mkdir -p "$OUT/classes" dist
javac -nowarn -source 8 -target 8 -bootclasspath "$JAR" -d "$OUT/classes" src/com/stecaklab/padlab/*.java 2>&1 | grep -v "^warning\|^Note\|^[0-9]* warning" || true
"$BT/dx" --dex --min-sdk-version=21 --output="$OUT/classes.dex" "$OUT/classes"
"$BT/aapt" package -f -M AndroidManifest.xml -S res -I "$JAR" -F "$OUT/unsigned.apk"
(cd "$OUT" && "$BT/aapt" add -k unsigned.apk classes.dex >/dev/null)
zipalign -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS=${KEYSTORE:-$PWD/debug.keystore}  # stable key, kept outside build/ so updates install over each other
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias padlab \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=PadLab Debug" >/dev/null 2>&1
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out dist/padlab-debug.apk "$OUT/aligned.apk"
apksigner verify dist/padlab-debug.apk && ls -l dist/padlab-debug.apk
