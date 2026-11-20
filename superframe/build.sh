#!/bin/sh
# Builds build/superframe.apk with the Android SDK command line tools, no Gradle.
# ANDROID_HOME points to an SDK with platforms;android-34 and build-tools;35.0.0.
# KEYSTORE defaults to the standard Android debug keystore, created if missing.
set -e

: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
API=34
TOOLS=$ANDROID_HOME/build-tools/35.0.0
JAR=$ANDROID_HOME/platforms/android-$API/android.jar
KEYSTORE=${KEYSTORE:-$HOME/.android/debug.keystore}

cd "$(dirname "$0")"
rm -rf build
mkdir -p build/classes build/dex

if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

"$TOOLS/aapt2" link --manifest AndroidManifest.xml -I "$JAR" -o build/unaligned.apk
javac --release 17 -classpath "$JAR" -d build/classes $(find src -name '*.java')
"$TOOLS/d8" --min-api $API --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q ../unaligned.apk classes.dex)
"$TOOLS/zipalign" -f 4 build/unaligned.apk build/aligned.apk
"$TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --out build/superframe.apk build/aligned.apk
echo "build/superframe.apk"
