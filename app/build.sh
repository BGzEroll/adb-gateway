#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
APP_DIR="$(pwd)"

if [ -x "$HOME/jdk17/bin/java" ]; then
  export JAVA_HOME="$HOME/jdk17"
  export PATH="$HOME/jdk17/bin:$PATH"
fi

TOOLS="${TOOLS:-$HOME/apkbuild/tools}"
AAPT2="$(find "$TOOLS" -maxdepth 5 -type f -name aapt2 -print -quit 2>/dev/null || true)"
ANDROID_JAR="${ANDROID_JAR:-$(find "$TOOLS" -maxdepth 5 -type f -name android.jar -print -quit 2>/dev/null || true)}"
KEYSTORE="${KEYSTORE:-$HOME/apkbuild/debug.keystore}"

[ -n "$AAPT2" ] || { echo "找不到 aapt2（可用 TOOLS 指定 build-tools 根目录）"; exit 1; }
[ -n "$ANDROID_JAR" ] || { echo "找不到 android.jar"; exit 1; }
[ -f "$KEYSTORE" ] || { echo "找不到 keystore: $KEYSTORE"; exit 1; }
BTDIR="$(dirname "$AAPT2")"

echo "java: $(java -version 2>&1 | head -1)"
echo "build-tools: $BTDIR"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/adbgw-build.XXXXXX")"
trap 'find "$WORK" -depth -delete' EXIT
mkdir -p "$WORK/classes" "$WORK/dex" "$WORK/res"
cp -r "$APP_DIR/res/." "$WORK/res/"
cp -r "$APP_DIR"/icon_res/mipmap-* "$WORK/res/"

echo "== aapt2 =="
"$AAPT2" compile --dir "$WORK/res" -o "$WORK/res.zip"
"$AAPT2" link -o "$WORK/base.apk" \
  --manifest "$APP_DIR/AndroidManifest.xml" \
  -I "$ANDROID_JAR" "$WORK/res.zip"

echo "== javac =="
javac -nowarn -source 8 -target 8 -bootclasspath "$ANDROID_JAR" \
  -d "$WORK/classes" "$APP_DIR"/src/*.java

echo "== d8 =="
mapfile -d '' -t classes < <(find "$WORK/classes" -name '*.class' -print0)
"$BTDIR/d8" --lib "$ANDROID_JAR" --min-api 30 --output "$WORK/dex" "${classes[@]}"
(cd "$WORK/dex" && zip -q -u "$WORK/base.apk" classes.dex)

echo "== sign =="
"$BTDIR/zipalign" -f -p 4 "$WORK/base.apk" "$WORK/aligned.apk"
"$BTDIR/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$APP_DIR/adbgw.apk" "$WORK/aligned.apk"

echo
echo "APK: $APP_DIR/adbgw.apk"
ls -l "$APP_DIR/adbgw.apk"
