<p align="center">
  <img src="docs/screenshots/launcher.png" width="120" alt="Maui icon">
</p>

<h1 align="center">Maui</h1>
<p align="center"><b>A real system layer for your Android phone</b></p>
<p align="center">
  Floating control panel · Live performance HUD · Game toolbar · Storage cleaner with hidden & duplicate file detection · Honest RAM & game booster
</p>

<p align="center">
  <a href="#english">English</a> ·
  <a href="README_AR.md">العربية</a> ·
  <a href="#license">MIT License</a>
</p>

---

<p align="center">
  <img src="docs/screenshots/dashboard.png" width="230" alt="Maui dashboard">
  <img src="docs/screenshots/games.png" width="230" alt="Maui game launcher">
  <img src="docs/screenshots/cleaner.png" width="230" alt="Maui cleaner">
  <img src="docs/screenshots/hud-game.png" width="230" alt="Maui in-game HUD">
</p>

> **Screenshots** are rendered by CI straight from the running app (Robolectric native
> graphics — the real layouts, the real views, the real scanner). They are committed by the
> release workflow, so they appear here after the first CI run.

> **Everything in Maui is measured, never faked.** RAM, CPU, temperature and FPS come
> straight from the OS (`/proc/stat`, `ActivityManager.MemoryInfo`, sysfs thermal zones,
> `SurfaceFlinger`). Free space is measured before and after deletion. If a feature needs
> root, Maui says so instead of pretending it worked.

---

## <a id="english"></a>🇬🇧 English

### What Maui is

Maui is a full-featured Android utility app that adds a **system layer on top of Android**,
in the spirit of Samsung One UI: a floating handle that opens a control panel over any app,
a live performance HUD, and a game toolbar that appears the moment a game is detected.

It also ships the tools people expect from a booster/cleaner app — but backed by real
system APIs instead of animated counters.

### ✨ Features

#### 🫧 Maui Layers — the system on top of Android
| | |
|---|---|
| **Floating bubble** | Draggable `M` handle that magnetically snaps to the screen edge, with a live pulse animation. Long-press to shut the layer down. |
| **Control panel** | Slide-out sheet with live RAM / CPU / temperature / battery / network tiles and one-tap actions: Clean, Boost, Files, Apps, Setup. |
| **Live HUD** | FPS, RAM, CPU%, temperature, battery and network speed pinned over *any* app. |
| **Game toolbar** | Automatically appears when a game is in the foreground — FPS, boost, clean and exit without leaving the match. |
| **Persistence** | Bubble position, size, opacity and edge survive reboots; the layer restores itself after a restart if you left it enabled. |

#### 🧹 Cleaner — finds the files you cannot find
- **App caches, thumbnails, temp files, logs, empty folders** — scanned and grouped by category.
- **Hidden leftovers** — dot-files and dot-folders anywhere in shared storage.
- **Large files** — anything over 100 MB, sorted by size.
- **Byte-identical duplicates** — files are grouped by size, then confirmed with an MD5 hash, so only true duplicates are flagged (the original is never touched).
- **Old `.apk` installers** left in Downloads.
- **Safe by default** — only genuinely safe categories are pre-selected. Hidden files, large files, duplicates and installers must be ticked by you.
- **Honest reporting** — freed space is measured, never estimated.

#### ⚡ Booster & Game Mode
- **Boost** kills background processes through `ActivityManager.killBackgroundProcesses` and trims the system cache with `pm trim-caches` (root), then reports the *actual* change in available memory.
- **Game Mode** drops window, transition and animator scales to `0` (the single biggest real win against jank), stops background apps, enables the game toolbar and silences notifications through Do Not Disturb.
- **Real FPS** — sampled from `SurfaceFlinger` when root is available, from the frame callback otherwise.

#### 🎮 Game launcher & game-space engine

A dedicated launcher for the games on the device, built the same way dedicated
game-space apps work — but with every step **measured and reported**, never
assumed.

| | |
|---|---|
| **Real game list** | Free Fire, PUBG, Genshin, Mobile Legends, Clash of Clans, Roblox, COD and 60+ other titles are recognised automatically, and *any* installed app can be added by hand with **Add game**. |
| **Per-game profiles** | `Performance` · `Balanced` · `Battery saver` — tap the chip under a game to switch. The choice is remembered per game. |
| **Launch = optimise** | Pressing **Play** applies the profile to the OS *before* the game starts, then launches it and opens the in-game HUD. |

What each profile actually does on the device:

- **Memory released** — every background process that is not the foreground app
  is killed, and the delta in available memory is measured and shown.
- **Animations off** — the animator, window and transition scales are set to `0`
  and read back to confirm it took.
- **Refresh rate** — the panel is pushed to the fastest mode the display
  actually reports (`peak_refresh_rate` / `min_refresh_rate`), and the new
  rate is read back.
- **Do Not Disturb** — notifications are silenced while you play.
- **Screen stays on** — the screen timeout is extended for the session and
  restored afterwards.
- **With root, additionally:** CPU governor → `performance`, GPU boost on the
  vendor's own control nodes, **Doze protection** for the game
  (`dumpsys deviceidle whitelist`), **standby bucket → active** so the game gets
  maximum RAM priority, and a system-wide cache trim.

After every launch Maui shows a report listing each step as applied or skipped
**with the reason** — no step is ever reported as successful when it was not.

#### 📈 Real FPS

The in-game HUD reads the frame rate from **SurfaceFlinger's frame timeline**
(`dumpsys SurfaceFlinger --latency <layer>`), which is the only accurate way to
measure another app's FPS on Android. The layer belonging to the running game is
picked automatically from the compositor's layer list.

Without root that timeline is not readable, and Maui says so (`FPS --`) instead
of showing a number it cannot measure.

#### 🔥 Free Fire support

Free Fire builds (`com.dts.freefireth`, `com.dts.freefiremax`,
`com.garena.game.kfire` and friends) are detected and flagged in the launcher,
and they get the full profile **plus** a dedicated pass that flushes background
processes and inspects the game's memory, on top of the Doze protection and
active standby bucket — the three things that make Free Fire stutter on
mid-range devices.

#### 📂 Files & 📦 Apps
- Built-in browser: internal storage, SD card, hidden-only, large-files-only and duplicates-only views, with delete (confirmed) and share via `FileProvider`.
- App manager with a per-app **app / cache / data** breakdown from `StorageStatsManager`, search, sort by size or name, force-stop, clear cache and uninstall.
- Root-assisted actions unlock automatically when `su` is detected: `pm trim-caches`, per-app cache clearing, `am force-stop`, and `du`-based cache discovery.

#### 🌍 Bilingual & themed
- Full English **and** Arabic (`values-ar`) with correct RTL support, launcher label `ماوي`.
- Material 3 theme, light / dark / follow-system, four navigation tabs plus settings.

### 📦 Install

The signed APK is `Maui-v1.0-release.apk`.

**Option A — GitHub Release (recommended).** The release page carries the APK as a
proper release asset:

    https://github.com/YOUSSEFJEDIDI89/Maui/releases

**Option B — the mirror inside this repository.** The exact APK is also stored here as
base64 chunks, so it can always be reconstructed byte-for-byte:

```bash
git clone https://github.com/YOUSSEFJEDIDI89/Maui.git
cd Maui/release
cat Maui-v1.0-release.apk.b64.* | base64 -d > Maui-v1.0-release.apk
sha256sum Maui-v1.0-release.apk
# 3089ef3794141768b4a4d262c32036c4c62c2f7df7c1849196c696aa18e3b216
```

Then, on the phone: open the APK, allow "install from unknown sources", install, and
launch **Maui**.

Via ADB:

```bash
adb install -r Maui-v1.0-release.apk
```

### 🔁 Publishing a new release

The release is produced by CI (`ci/release.yml`), which builds the signed APK, runs the
test suite, regenerates the screenshots and attaches the APK to a GitHub Release.

```bash
bash scripts/enable_release.sh          # installs the workflow (one-time)
gh workflow run release.yml -f version=v1.0
```

`scripts/enable_release.sh` copies the workflow into `.github/workflows/` and pushes it.
Once it is there, the release can also be triggered straight from the Actions tab.

### 🔐 Permissions — and exactly why each one is needed

Maui works **without root**, using public Android APIs. It also detects `su` at runtime and
unlocks extra power when it is present.

| Permission | Why | Without it |
|---|---|---|
| Display over other apps | Draws the Maui bubble | The overlay cannot appear |
| All files access | Scans and deletes junk in shared storage | Only app-private caches are cleaned |
| Usage access | Detects the foreground app, per-app storage, games | No game toolbar, no size column |
| Notifications | Shows the "Maui Layers is active" notification | The service runs, but silently |
| Battery optimisation exemption | Keeps the layer alive | Android may kill it in the background |
| Modify system settings | Sets animation scale to 0 in Game Mode | Game Mode works, minus the animation tweak |
| Do Not Disturb access | Silences notifications while gaming | DND is skipped, everything else works |
| `QUERY_ALL_PACKAGES` | Lets Maui see every installed app | Apps list is incomplete |

All of them are listed with live status in **Settings → Permissions**, each with a **Grant**
button that opens the exact system screen.

### 🛠 Build from source

```bash
git clone https://github.com/YOUSSEFJEDIDI89/Maui.git
cd Maui
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleRelease      # -> app/build/outputs/apk/release/app-release.apk
./gradlew :app:testReleaseUnitTest  # JVM + Robolectric test suite
```

Requirements: **JDK 17**, **Android SDK 35** (platform 35 + build-tools 35.0.0), Gradle 8.9+.

> The repository ships a **demo signing key** (`keystore/maui-release.jks`, password `maui2026`,
> alias `maui`) so that CI produces an installable release APK for anyone who forks the project.
> Replace it with your own key before publishing to any store.

### 🧪 Tests

```bash
./gradlew :app:testReleaseUnitTest
```

* `CoreLogicTest` — size/percent formatting, recursive directory sizing, real shell execution.
* `MauiSmokeTest` — **Robolectric** runs every screen on a real Android runtime: the main activity and its five navigation tabs, the dashboard stat tiles, the settings permission rows, the apps and file-explorer activities, all four overlay views, and a scanner test that plants junk files on disk and asserts they are found.

### 📁 Project layout

```
Maui/
├── app/src/main/java/com/maui/system/
│   ├── core/       DeviceProfiler · PerformanceSampler · AppHelper · Cleaner · RootShell · Prefs
│   ├── scanner/    StorageScanner — junk, hidden, large files, duplicates
│   ├── overlay/    MauiOverlayService · BubbleView · MauiPanelView · GameToolbarView · HudView
│   └── ui/         MainActivity · Dashboard/Cleaner/Files/Games · Settings · Apps · FileExplorer
├── app/src/main/res/     layouts, values (EN), values-ar (AR), Material 3 theme
├── app/src/test/         JVM + Robolectric tests
├── docs/screenshots/     Generated by CI from the real app
└── tools/make_icons.py   Launcher icon generator
```

### ⚖️ Honest limitations

- Clearing **other apps'** caches needs root, or you clear them from the system app settings. Maui tells you this rather than faking it.
- Per-app storage sizes need Usage Access; without it the app list still works, but the size column is empty.
- The overlay needs "display over other apps"; some launchers restart the foreground activity after granting it, which briefly hides the bubble until Maui redraws it.

---

<a id="license"></a>

## 📄 License

Released under the **MIT License** — use it, fork it, modify it, ship it. See [LICENSE](LICENSE).

```
MIT License

Copyright (c) 2026 Maui Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
