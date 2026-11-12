#!/usr/bin/env bash
# Release gate (owner 12.11, after v3.0.50–52 crashed on every Android 10 box): install the release APK on an
# Android 10 (API 29) emulator, make ART verify EVERY class (that is where R8's broken method was rejected),
# then launch the app and check it is still alive. Any verifier rejection or crash fails the release.
set -u
apk="$1"; pkg="$2"
adb wait-for-device
adb install -r -g "$apk" || { echo "install failed"; exit 1; }
adb logcat -c
# Ahead-of-time verification of all classes; a hard verifier failure is logged by dex2oat.
adb shell cmd package compile -m verify -f "$pkg" || true
sleep 5
adb logcat -d > verify.log
adb logcat -c
adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 30
adb logcat -d > launch.log
fail=0
if grep -E "Verification error in|Verifier rejected class|java.lang.VerifyError" verify.log launch.log; then
  echo "::error::ART verifier rejected a class on Android 10"; fail=1
fi
if grep -qE "FATAL EXCEPTION|Process: $pkg, PID" launch.log; then
  grep -A30 "FATAL EXCEPTION" launch.log | head -60
  echo "::error::the app crashed on start on Android 10"; fail=1
fi
if ! adb shell pidof "$pkg" >/dev/null; then
  echo "::error::the app is not running 30 s after start on Android 10"; fail=1
fi
[ "$fail" = 0 ] && echo "Android 10 smoke test passed"
exit $fail
