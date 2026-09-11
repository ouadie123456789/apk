# Prompt for Claude Code — build this into an installable APK

Copy everything in the box below and paste it as your first message to Claude Code,
run from inside this project folder (the directory containing `settings.gradle`).

------------------------------------------------------------------------------
You are in the root of an Android Gradle project (Kotlin, AGP 8.5.2, Gradle 8.9,
JDK 17). Your one goal: produce a debug APK I can install on my phone, at
`app/build/outputs/apk/debug/app-debug.apk`.

Do this end to end without asking me to intervene:

1. Check the toolchain. Ensure JDK 17 is available (`java -version`). Ensure the
   Android SDK is present; if `ANDROID_HOME`/`ANDROID_SDK_ROOT` is unset or the
   SDK is missing, install the command-line tools, set the env var, and use
   `sdkmanager` to install `platform-tools`, `platforms;android-34`, and
   `build-tools;34.0.0`, accepting licenses (`sdkmanager --licenses`).
2. Ensure Gradle 8.9 is usable. If there is no `./gradlew`, either generate the
   wrapper (`gradle wrapper --gradle-version 8.9`) or install Gradle 8.9 and call
   `gradle` directly. Create `local.properties` with `sdk.dir=<sdk path>` if needed.
3. Build: `./gradlew assembleDebug --stacktrace` (or `gradle assembleDebug`).
4. If the build fails, read the actual error and fix it in place — version bumps,
   missing SDK components, a stale wrapper, etc. — then rebuild. Repeat until green.
   Do NOT change app behaviour or the accessibility logic; only fix build issues.
5. When it succeeds, print the absolute path to `app-debug.apk` and its size, and
   give me the exact `adb install -r <path>` command.

Notes:
- This app is a private sideload tool (an Accessibility Service). It is not going
  to Google Play, so a debug signature is fine — do not set up release signing.
- minSdk 24, compileSdk/targetSdk 34, namespace `com.example.likeebridge`.
- If a dependency version is unavailable, pick the nearest working stable version
  and tell me what you changed.
------------------------------------------------------------------------------

## After the build
1. `adb install -r app/build/outputs/apk/debug/app-debug.apk` (or copy the APK to
   the phone and tap it; enable "install unknown apps").
2. Open **Likee Bridge**, paste your server URL, tap **Save Endpoint**.
3. Tap **Enable Service** → turn on **Likee Chat Bridge** in Accessibility settings.
4. Open a Likee live stream. Status should flip to **CAPTURING** when chat arrives.

## If no messages capture
The barrage IDs are pinned to Likee v5.64.1. If Likee updated, set
`DEBUG_DUMP = true` in `LikeeAccessibilityService.kt`, rebuild, open a live stream,
and read Logcat (`adb logcat -s LikeeBridge`) to find the current
`tv_barrage_sender_nickname` / `tv_barrage_sender_msg` IDs, then update the
constants and set `DEBUG_DUMP = false`.
