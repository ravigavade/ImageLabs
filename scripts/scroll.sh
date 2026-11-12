#!/bin/bash
PKG=com.csaim.imagelabs

if [ "$1" = "cold" ]; then
  adb shell pm clear $PKG
fi
# fresh start: kill app, relaunch, let it settle
adb shell am force-stop $PKG
adb shell am start -n $PKG/.MainActivity > /dev/null
sleep 4

adb logcat -c
adb shell dumpsys gfxinfo $PKG reset > /dev/null

for i in $(seq 1 10); do
  adb shell input swipe 640 2200 640 800 150
  sleep 1.5
done
for i in $(seq 1 10); do
  adb shell input swipe 640 800 640 2200 150
  sleep 1.5
done

echo "=== FRAME STATS ==="
adb shell dumpsys gfxinfo $PKG | grep -E "Total frames|Janky frames:|50th percentile|90th percentile|99th percentile|Slow issue|High input"
echo "=== NON-EXPLICIT GC COUNT ==="
adb logcat -d | grep "csaim.imagelabs" | grep "GC freed" | grep -vc "Explicit"
echo "=== MEMORY (end of run) ==="
adb shell dumpsys meminfo $PKG | grep -E "TOTAL PSS|Java Heap:|Native Heap:|Graphics:"

echo "=== CRASHED? ==="
adb logcat -d | grep -c "FATAL EXCEPTION"
adb shell pidof $PKG > /dev/null && echo "app alive" || echo "app DEAD"

echo "=== CACHE (last line) ==="
adb logcat -d -s BitmapCache | tail -1

echo "=== DISK CACHE (last line) ==="
adb logcat -d -s DiskCache | tail -1