#!/usr/bin/env bash
set -euo pipefail
export ANDROID_AVD_HOME="$HOME/.android/avd"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
mkdir -p "$ANDROID_AVD_HOME"
echo "$ANDROID_HOME/platform-tools" >> "$GITHUB_PATH"
echo "$ANDROID_HOME/emulator" >> "$GITHUB_PATH"
# Never wait indefinitely for an emulator process that has already exited.
trap 'for f in /tmp/emulator*.log; do tail -60 "$f" 2>/dev/null || true; done' EXIT
test -e /dev/kvm
sudo chmod a+rw /dev/kvm
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'platform-tools' 'emulator' 'system-images;android-35;google_apis;x86_64'
for n in A B; do
  echo no | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -n "qa$n" -k 'system-images;android-35;google_apis;x86_64' --force --path "$ANDROID_AVD_HOME/qa$n.avd"
done
adb start-server
for pair in A:5554 B:5556; do
  n=${pair%:*}; port=${pair#*:}
  nohup "$ANDROID_HOME/emulator/emulator" -avd "qa$n" -port "$port" -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader -feature -Vulkan -cores 2 -memory 1536 -camera-back emulated -camera-front none -no-metrics > "/tmp/emulator$n.log" 2>&1 &
  pid=$!
  sleep 3
  kill -0 "$pid" || { cat "/tmp/emulator$n.log"; exit 1; }
  timeout 120 adb -s "emulator-$port" wait-for-device
  deadline=$((SECONDS+180))
  until [ "$(timeout 8 adb -s "emulator-$port" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
    kill -0 "$pid" || exit 1
    [ "$SECONDS" -lt "$deadline" ] || { echo "Boot timeout $n"; exit 1; }
    sleep 2
  done
  adb -s "emulator-$port" shell input keyevent 82
  adb -s "emulator-$port" shell settings put global window_animation_scale 0
  adb -s "emulator-$port" shell settings put global transition_animation_scale 0
  adb -s "emulator-$port" shell settings put global animator_duration_scale 0
  echo "ANDROID_BOOT_OK $n"
done
