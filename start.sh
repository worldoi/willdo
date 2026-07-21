#!/usr/bin/env bash
# =============================================================================
#  start.sh — Build the CalendarAssistant Android APK in a cloud env (IDX / CI)
#
#  Android has NO "database migration" step and NO "dev server":
#    - Room DB migrations are compiled into the app and run at first launch.
#    - The product is an APK; you run it on a real device (IDX has no emulator).
#
#  Usage:  ./start.sh
# =============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

# ---- 1. Environment ----
# 强制使用我们自己的干净 SDK 目录，不能沿用 devcontainers/android 基础镜像自带的
# /opt/android-sdk（里面带过旧的 build-tools 25.0.2，会让 AGP 枚举时抛 IllegalArgumentException）
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(which java)")")")}"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# ---- 2. Install the Android commandline-tools (only once) ----
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo ">> Android cmdline-tools not found — downloading (this happens once)..."
  mkdir -p /tmp/cmdline && cd /tmp/cmdline
  # If this URL 404s, grab the latest from:
  # https://developer.android.com/studio#command-line-tools-only
  curl -fsSL -o cmdline-tools.zip \
    https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q cmdline-tools.zip
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  mv cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
  cd "$REPO_ROOT"
fi

# ---- 3. Accept licenses + install SDK packages ----
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --licenses >/dev/null || true

# compileSdk=36，直接装 android-36（不再尝试 android-37，那是未来/beta API level，仓库里没有）
"$SDKMANAGER" "platform-tools" "build-tools;35.0.0" "platforms;android-36"

# 防御：清掉任何过旧的 build-tools（如 25.0.2），避免 AGP 枚举 SDK 时解析失败
rm -rf "$ANDROID_HOME/build-tools/25.0.2" 2>/dev/null || true
# 删除可能指向基础镜像旧 SDK 的 local.properties，强制 AGP 使用上面的 ANDROID_HOME
rm -f "$REPO_ROOT/CalendarAssistant/local.properties"

# ---- 4. Build the debug APK ----
cd "$REPO_ROOT/CalendarAssistant"
chmod +x ./gradlew
./gradlew assembleDebug --no-daemon

echo ""
echo "✅ Build complete."
echo "APK -> CalendarAssistant/app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "📱 Install on a PHYSICAL device (IDX cloud has no emulator):"
echo "   1) Phone: Settings > Developer options > Wireless debugging > Pair using code"
echo "   2) adb pair <ip>:<port>        # enter the 6-digit code"
echo "   3) adb connect <ip>:<port>"
echo "   4) adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk"
