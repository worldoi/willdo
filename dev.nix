# dev.nix — Google Project IDX environment for the Will-do / CalendarAssistant Android project.
# The Android app lives in the CalendarAssistant/ subfolder of this repo.
# IDX reads this file from the REPO ROOT (Will-do/).
{ pkgs, ... }:

{
  # Tools available in the IDX terminal.
  # The Android SDK itself is installed by start.sh (kept out of onPostStart so the
  # workspace always opens even if the network to dl.google.com is slow/blocked).
  packages = [
    pkgs.jdk17        # JDK 17 — required by AGP 8.13 / Kotlin 2.3
    pkgs.unzip        # to unpack the Android commandline-tools
    pkgs.git
    pkgs.curl
  ];

  idx = {
    # VS Code extensions useful for Android / Kotlin work
    extensions = [
      "vscjava.vscode-gradle"
      "galudisu.android"
      "JetBrains.kotlin"
      "redhat.java"
    ];

    workspace = {
      # Runs once when the workspace is first created
      onCreate = {
        "prepare-gradlew" = "chmod +x CalendarAssistant/gradlew 2>/dev/null || true";
      };
    };

    # Android builds an APK, not a web service — there is no in-cloud preview.
    # Run the APK on a physical device via adb (see start.sh / IDX-SETUP.md).
    previews = {
      enable = false;
    };

    # Keep this lightweight so the workspace always starts.
    onPostStart = ''
      set -e
      export ANDROID_HOME="''${ANDROID_HOME:-''$HOME/Android/Sdk}"
      export ANDROID_SDK_ROOT="''$ANDROID_HOME"
      export JAVA_HOME="''$(dirname "''$(dirname "''$(readlink -f "''$(which java)")")")"
      chmod +x "''$HOME/CalendarAssistant/gradlew" 2>/dev/null || true
      echo "IDX environment ready. Run ./start.sh to install the Android SDK and build the APK."
    '';
  };
}
