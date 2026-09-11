# Likee Bridge

Android Accessibility Service that reads live Likee chat (the "barrage" comment
stream) and POSTs each message to a server endpoint you configure.

Package: `video.like` targeting confirmed against Likee APK v5.64.1.

## Getting an installable APK with zero local setup (GitHub Actions)
1. Create a new GitHub repository (private is fine).
2. Upload every file/folder from this project to the repo root, or `git push`.
   The workflow at `.github/workflows/build.yml` runs automatically on push.
3. Open the repo's **Actions** tab → wait for **Build APK** to finish (~3–5 min).
4. Open the finished run → **Artifacts** → download **likee-bridge-debug-apk**
   (a .zip containing `app-debug.apk`).
5. Copy the APK to your phone, allow "install unknown apps", tap to install.

## Or build with Claude Code
See `BUILD_WITH_CLAUDE_CODE.md` — paste the prompt inside it into Claude Code
run from this folder.

## Or build locally in Android Studio
Open this folder → let Gradle sync → **Build ▸ Build APK(s)**.
Output: `app/build/outputs/apk/debug/app-debug.apk`.

> Cannot be published on Google Play (accessibility-scraping of another app
> breaks Play policy). Sideload only.

## Use
1. Open **Likee Bridge**, enter your endpoint URL (e.g.
   `https://your-server.com/api/chat`), tap **Save Endpoint**.
2. Tap **Enable Service** → turn on *Likee Bridge* in Android Accessibility settings.
3. Open a Likee live stream with active chat. Status flips to **CAPTURING**.

Server receives JSON:
`{"username","message","source":"likee","captured_at"}`

## If no messages capture
The barrage IDs are pinned to Likee v5.64.1. If Likee updated, set
`DEBUG_DUMP = true` in `LikeeAccessibilityService.kt`, rebuild, open a live stream,
and read Logcat (`adb logcat -s LikeeBridge`) to find the current
`tv_barrage_sender_nickname` / `tv_barrage_sender_msg` IDs, then update the
`ID_NICK` / `ID_MSG` constants and set `DEBUG_DUMP = false`.

Note: some live-video surfaces render chat on a SurfaceView/Canvas or with
FLAG_SECURE, which accessibility cannot read regardless of IDs. The DEBUG_DUMP
output tells you whether the chat text is actually exposed to the a11y tree.
