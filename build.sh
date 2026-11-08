#!/bin/bash
# 构建 NetDiag APK（不依赖 Gradle，纯 aapt2 + javac + d8 + apksigner）
set -e

# 工程根目录（脚本自身所在目录），工作区根目录取其上级
SRC="$(cd "$(dirname "$0")" && pwd)"
BASE="${NETDIAG_HOME:-$(cd "$SRC/.." && pwd)}"
# 工具链不在当前工作区时，回退到原始开发目录
[ -d "$BASE/tools" ] || BASE="/Users/cmos/WorkBuddy/安卓开发"

JDK_HOME="$BASE/tools/jdk-17.0.21+9/Contents/Home"
if [ -d "$JDK_HOME" ]; then
    export JAVA_HOME="$JDK_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
# 未自带 JDK 时沿用系统 JAVA_HOME（需 JDK 17+）

BT="$BASE/tools/android-16"
AJAR="$BASE/tools/android-36/android.jar"
ADB="$BASE/platform-tools/adb"
OUT="$SRC/build"
KS="$SRC/debug.keystore"
PKG="com.cmos.netdiag"

AAPT2="$BT/aapt2"
D8_JAR="$BT/lib/d8.jar"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"
[ -x "$APKSIGNER" ] || APKSIGNER="$BT/lib/apksigner.jar"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/res"

echo "[1/6] aapt2 compile 资源"
"$AAPT2" compile --dir "$SRC/res" -o "$OUT/res/res.zip"

echo "[2/6] aapt2 link"
"$AAPT2" link \
    -o "$OUT/base.apk" \
    -I "$AJAR" \
    --manifest "$SRC/AndroidManifest.xml" \
    "$OUT/res/res.zip" \
    --java "$OUT/gen" \
    --min-sdk-version 30 \
    --target-sdk-version 36 \
    --version-code 1 \
    --version-name 1.0

echo "[3/6] javac"
find "$SRC/src" "$OUT/gen" -name "*.java" > "$OUT/sources.txt"
javac -source 11 -target 11 -nowarn -encoding UTF-8 -cp "$AJAR" -d "$OUT/classes" @"$OUT/sources.txt"

echo "[4/6] d8 -> classes.dex"
find "$OUT/classes" -name "*.class" > "$OUT/classes.txt"
java -cp "$D8_JAR" com.android.tools.r8.D8 \
    --min-api 30 --lib "$AJAR" \
    --output "$OUT" \
    @"$OUT/classes.txt"

echo "[5/6] 打包 dex 并对齐"
cd "$OUT"
zip -q -u base.apk classes.dex
"$ZIPALIGN" -f -p 4 base.apk aligned.apk

echo "[6/6] 签名"
if [ ! -f "$KS" ]; then
    keytool -genkeypair -v -keystore "$KS" -alias netdiag \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=NetDiag, OU=Dev, O=cmos, L=, S=, C=CN" >/dev/null 2>&1
    echo "  已生成调试密钥"
fi

if [ -f "$APKSIGNER" ]; then
    "$APKSIGNER" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
        --out "$OUT/netdiag.apk" "$OUT/aligned.apk"
else
    java -jar "$APKSIGNER" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
        --out "$OUT/netdiag.apk" "$OUT/aligned.apk"
fi

echo "构建完成: $(ls -lh "$OUT/netdiag.apk" | awk '{print $5}')  ->  $OUT/netdiag.apk"

if [ "$1" != "--no-install" ]; then
    DEV="${DEV:-${ANDROID_SERIAL:-$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1; exit}')}}"
    if [ -z "$DEV" ]; then
        echo "⚠️  没有在线设备，跳过安装（adb devices 为空）"
        exit 0
    fi
    echo "--- 安装到设备 $DEV ---"
    "$ADB" -s "$DEV" install -r "$OUT/netdiag.apk" 2>&1 | tail -3
    echo "--- 授予权限 ---"
    for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION READ_PHONE_STATE NEARBY_WIFI_DEVICES ACCESS_WIFI_STATE; do
        "$ADB" -s "$DEV" shell "pm grant $PKG android.permission.$p" >/dev/null 2>&1
        "$ADB" -s "$DEV" shell "su -c 'pm grant --user 0 $PKG android.permission.$p'" >/dev/null 2>&1
    done
    "$ADB" -s "$DEV" shell "appops set $PKG android:fine_location allow" >/dev/null 2>&1
    "$ADB" -s "$DEV" shell "appops set $PKG android:coarse_location allow" >/dev/null 2>&1
    echo "--- 启动 ---"
    "$ADB" -s "$DEV" shell "am start -n $PKG/.MainActivity" 2>&1 | tail -2
fi
