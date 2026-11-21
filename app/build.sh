#!/usr/bin/env bash
# ADB 网关 —— 手机端构建脚本
# 依赖：JDK17、Android build-tools(aapt2/d8/apksigner/zipalign)、android.jar、debug keystore
set -euo pipefail
cd "$(dirname "$0")"
APP_DIR="$(pwd)"
WORK="${WORK:-$HOME/apkbuild/gw-build}"

if [ -x "$HOME/jdk17/bin/java" ]; then
  export JAVA_HOME="$HOME/jdk17"
  export PATH="$HOME/jdk17/bin:$PATH"
fi

TOOLS="${TOOLS:-$HOME/apkbuild/tools}"
BTDIR="$(find "$TOOLS" -maxdepth 5 -type f -name aapt2 2>/dev/null | head -1 | xargs -r dirname || true)"
ANDROID_JAR="$(find "$TOOLS" -maxdepth 5 -type f -name android.jar 2>/dev/null | head -1 || true)"
KEYSTORE="${KEYSTORE:-$HOME/apkbuild/debug.keystore}"

[ -n "$BTDIR" ] || { echo "找不到 aapt2（可用 TOOLS 指定 build-tools 根目录）"; exit 1; }
[ -n "$ANDROID_JAR" ] || { echo "找不到 android.jar"; exit 1; }
[ -f "$KEYSTORE" ] || { echo "找不到 keystore: $KEYSTORE"; exit 1; }

echo "java: $(java -version 2>&1 | head -1)"
echo "build-tools: $BTDIR"

rm -rf "$WORK"
mkdir -p "$WORK/classes" "$WORK/dex" "$WORK/res"
cp -r "$APP_DIR/res/." "$WORK/res/"
cp -r "$APP_DIR/icon_res/." "$WORK/res/"

echo "== aapt2 =="
"$BTDIR/aapt2" compile --dir "$WORK/res" -o "$WORK/res.zip"
"$BTDIR/aapt2" link -o "$WORK/base.apk" \
  --manifest "$APP_DIR/AndroidManifest.xml" \
  -I "$ANDROID_JAR" "$WORK/res.zip" \
  --min-sdk-version 29 --target-sdk-version 34 \
  --java "$WORK/gen"

echo "== javac =="
javac -nowarn -source 8 -target 8 -bootclasspath "$ANDROID_JAR" \
  -d "$WORK/classes" "$APP_DIR"/src/*.java

echo "== d8 =="
"$BTDIR/d8" --lib "$ANDROID_JAR" --min-api 29 --output "$WORK/dex" \
  $(find "$WORK/classes" -name '*.class')
(cd "$WORK/dex" && zip -q -u "$WORK/base.apk" classes.dex)

echo "== sign =="
"$BTDIR/zipalign" -f -p 4 "$WORK/base.apk" "$WORK/aligned.apk"
"$BTDIR/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$APP_DIR/adbgw.apk" "$WORK/aligned.apk"

echo
echo "APK: $APP_DIR/adbgw.apk"
ls -l "$APP_DIR/adbgw.apk"