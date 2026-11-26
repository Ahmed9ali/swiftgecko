# Swift Browser (Gecko edition)

Android browser on **GeckoView** (Firefox's engine) with a Brave-style UI, built-in blockers and an
automation hook. Written in Kotlin + Jetpack Compose. No local build needed: GitHub Actions builds the APK.

## Get an APK (no PC power needed)
1. Create a **new, empty** GitHub repo and upload everything in this folder (keep `.github/workflows/build.yml`).
2. Open the repo's **Actions** tab, pick **Build APK**, press **Run workflow** (it also runs on every push).
3. When it is green, open the run and download the artifact **swift-browser-debug-apk**, unzip, install the `.apk`.

Public repos have unlimited free Actions minutes. The build takes roughly 5-10 minutes.

## Compatibility
Runs on **Android 5.0+ (API 21)**, both **32-bit (armeabi-v7a)** and **64-bit (arm64-v8a)** phones.
GeckoView 145 and newer need Android 8, so the build is limited to GeckoView below 145 (range in `gradle.properties`).
Trade-off: that engine no longer gets Firefox security updates. Use a normal Firefox on devices that can run Android 8+.
On Android 5-9 downloads need the storage permission (asked on first download).

## If the build fails
This project could not be compiled where it was written, so a first-build error is possible. Open the failed
step in Actions, copy the **first** `e: ...` line (Kotlin) or the Gradle error and send it back.
Most likely places: `TabDelegates.kt`, `Prompts.kt`, `Extensions.kt` (GeckoView API signatures change between versions).
- "Could not resolve org.mozilla.geckoview:geckoview": check `https://maven.mozilla.org/maven2/org/mozilla/geckoview/`
  for the real artifact name / versions, then set `geckoArtifact=` / `geckoVersion=` in `gradle.properties`.
- After the first green build, pin `geckoVersion` to the exact version Gradle used (see the build log) so the
  engine does not change under you. Update it on purpose now and then: an old engine = unpatched security bugs.

## What is inside
| Area | Where |
|---|---|
| Engine + tracking protection (Gecko ETP) | `SwiftApp.kt` |
| Tabs, private tabs, tab switcher with thumbnails, restore tabs | `Browser.kt`, `Screens.kt` |
| Address bar (URL or search), suggestions, new tab page with block stats, find in page, desktop site | `BrowserUi.kt` |
| Shields panel (per-site on/off, blocked count, strict mode) | `BrowserUi.kt` |
| Bookmarks, history, downloads, settings | `Store.kt`, `Downloads.kt`, `Screens.kt` |
| Popups, JS dialogs, `<select>`, file upload, permissions, fullscreen video, external app links | `TabDelegates.kt`, `Prompts.kt`, `MainActivity.kt` |
| Built-in extensions (loaded without prompts, also in private tabs) | `Extensions.kt`, `scripts/fetch_extensions.py` |
| Automation hook | `app/src/main/assets/extensions/automation/*`, `AutomationEngine` in `Extensions.kt` |

### Built-in blockers (downloaded from addons.mozilla.org during the build)
uBlock Origin (ads/trackers/scriptlets/redirect stubs), ClearURLs (tracking parameters, redirect wrappers),
Skip Redirect (intermediate redirect / short-link pages), I still don't care about cookies.
Edit `EXTENSIONS` in `scripts/fetch_extensions.py` to add or remove. The blocked-count badge on the shield and the
"blocked" numbers come from uBlock Origin.
License note: uBlock Origin is GPLv3. Check every extension's license before you distribute the APK publicly.

### Automation
The app ships an extension (`automation/`) with a content script that listens on a native port. `AutomationEngine`
sends it JSON steps: `open, newTab, back, wait, click, type, waitFor, getText, getHtml, getAttr, scroll, eval, info`.
Open **Menu > Automation** to run a script. To plug in your own logic, call
`app.browser.automation.run(JSONArray(...)) { log -> ... }` from anywhere, or add commands in `content.js`.

## Not done / known limits
- Date, time and colour pickers, HTTP auth prompts, and print are not implemented.
- Per-site Shields only toggles Gecko's tracking protection. uBlock Origin per-site rules: use its dashboard
  (Menu > Extensions > Open settings).
- Widevine DRM (Netflix etc. at full quality) is not set up.
- No sync, no password manager UI (Android autofill is enabled), no reader mode, no translations.
- Downloads list is not saved across restarts. History/bookmarks are kept in SharedPreferences.
- The debug APK is signed with the debug key: fine for your own phone, not for Play Store.

## Diagnostics (added after the "pages do not open / random closing" report)
- `SwiftApp` now starts the Gecko engine **only in the main Android process**. GeckoView runs page/GPU work in
  separate processes (`:tab0`, `:gpu`, ...) that also load the Application class; starting a second engine there
  made the browser crash and pages never load.
- Settings > **Diagnostics (event log)** shows the last 300 events (navigation, page start/stop, load errors,
  extension loading, content-process crashes). Use Copy / Share and send it along with any bug report.
- If the app dies with a Java exception, the next start shows **"The app crashed last time"** with the stack trace.
- If the engine cannot start at all, a dialog shows the reason instead of closing the app.
- With a PC: `adb logcat -s Swift:D AndroidRuntime:E` (or `adb logcat | grep -iE "swift|gecko|fatal"`).
