<p align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="extras/brand/app-logos/logo_eggshell_light.png">
    <img src="extras/brand/app-logos/logo_eggshell.png" alt="YAiPlayer" width="360">
  </picture>
</p>

<p align="center">
  <b>Your own IPTV player for Android TV</b><br>
  <sub>Fast · modern · remote-first — bring your own M3U, Xtream or Stalker (MAC) sources</sub>
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%20TV-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Compose for TV" src="https://img.shields.io/badge/Jetpack%20Compose-for%20TV-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Player" src="https://img.shields.io/badge/engines-libmpv%20%2B%20ExoPlayer-FB8C00">
  <img alt="License" src="https://img.shields.io/badge/license-GPLv3-blue">
  <img alt="Built with the help of AI" src="https://img.shields.io/badge/built%20with-the%20help%20of%20AI-8A2BE2">
</p>

---
YAiPlayer is a native **Android TV** IPTV **player**, built with Kotlin and Jetpack Compose for TV. It
runs **two playback engines** — libmpv (FFmpeg) for the widest compatibility, ExoPlayer (Media3) for
near-instant Live TV — and it is a *player only*: you bring your own **Xtream** login, **M3U**
playlist (URL or a local file) or **Stalker/Ministra** portal.

> ⚠️ YAiPlayer ships **no** channels, playlists, subscriptions or streams. You are responsible for the
> sources you add.

Open source, GPLv3, original code, built with the help of AI. **Android TV only** — leanback
launcher, D-pad first.

> ### 📖 New here? **[User Guide →](extras/USER_GUIDE.md)**
> Every feature, where to find it, and the remote shortcuts — on one page.

---

## ✨ Features

### 🎬 Playback
- **Two engines** — mpv for compatibility, ExoPlayer for instant Live TV, with automatic fallback and a per-channel override
- **4K HDR** direct rendering, optional auto frame-rate matching, surround sound with a stereo safety net, 150% volume boost
- **Subtitles** — text, image and closed captions, plus OpenSubtitles search and local files
- Resume, next-episode auto-play, mini-player/PiP, picture-in-picture when you press Home, audio-only mode, live stream-info overlay
- Media-session transport keys and proper audio focus — it behaves like a TV app
- An **error log you can export** for any stream that misbehaves

### 🧭 Browse
- **Home** with Continue Watching and **Now Trending** (TMDB rankings matched to what your provider can actually play)
- Live TV · Movies · Series · Downloads · Guide, with per-profile row order, Favorites, History and search
  (voice search where the TV has a speech recogniser)
- **Multiple playlists** at once, with provider labels everywhere they could be confused
- Rename, hide, reorder and combine categories; bulk rename rules
- **TMDB** posters, plots, cast and trailers in 40 languages — scales to ~50k channels / ~168k movies
- **Two layouts for Movies & Series** — the classic three panels, or **Cinematic**: the focused title's artwork full-bleed behind the whole screen, with its details and cast above a wide poster grid
- Adjustable panel widths per section, including hiding the preview pane entirely

### 📥 Sources & EPG
- **Xtream**, **M3U** and **Stalker/Ministra** — add any of them from your phone over Wi-Fi (QR + PIN)
- **TV Guide** (XMLTV) with a live Now marker, multiple guide sources and automatic channel matching
- **Catch-up TV** up to 7 days, live rewind, and *Go back to…* even for channels with no guide
- Guide time offset, per-source guide logos, per-channel EPG fixes
- **Protected (DRM) channels** — Widevine and ClearKey, read from the playlist, nothing to configure
- Custom **DNS** (Google, Cloudflare, Quad9, DoH or your own)

### ⏺️ Recording & Multiview
- Record live TV from the guide, the channel list or while watching — including every showing of a programme
- **Multiview** — up to four channels at once, with one carrying the sound
- YAiPlayer measures how many streams your provider allows and warns before it refuses one

### 👥 Profiles & Downloads
- Multiple profiles with their own favourites, history and resume points, PIN locks and a Kids mode; lock single categories behind a PIN, and optionally Settings too
- Per-profile startup: Home, last channel, the last channel in the TV Guide or Live TV, Favorites, or one
  chosen channel
- Offline movies and episodes with pause, resume, retry and a storage bar

### 🎨 Look & robustness
- Your own accent colour and focus highlight, frosted **Glass** with adjustable opacity, and a Stage,
  Picture or Plain background
- **Dark and Light themes** (or follow the TV) across the whole app, the player included
- Font sizing for the interface and for popups, independently
- **Remote Shortcuts** — map spare colour, number, channel and media keys to 26 actions; **Player buttons**
  choose what OK and a held OK do in the player, swap channel up/down, and turn number keys off
- **Menu labels** — the section names beside the menu icons: off, when focused, or always
- **26 interface languages**, RTL-aware, chosen before anything else on a fresh install
- **Backup & Restore** to a single `.own` file, optionally encrypted, locally or over Wi-Fi
- **Set up from another device** — a new box copies everything from the YAiPlayer device you already
  have over your own Wi-Fi, offered on the first setup screen rather than buried in the menus
- In-app updates; memory-safe lists, auto-reconnect and offline detection throughout

---

## 📸 Screenshots

<table>
  <tr>
    <td align="center"><img src="extras/screenshots/Home.png" alt="Home screen"><br><sub>Home — Continue Watching</sub></td>
    <td align="center"><img src="extras/screenshots/LiveTV_with_PreviewON.png" alt="Live TV with preview"><br><sub>Live TV — preview playing</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="extras/screenshots/EPG_loaded.png" alt="TV Guide"><br><sub>TV Guide (EPG)</sub></td>
    <td align="center"><img src="extras/screenshots/Settings_Main.png" alt="Settings"><br><sub>Settings</sub></td>
  </tr>
</table>

More in **[extras/screenshots/](extras/screenshots/)**.

---

## 🧱 Tech stack

| Area | Choice |
|------|--------|
| Language | Kotlin 2.4.20 (no `kotlin-android` plugin) |
| Build | AGP 9.4.0 / Gradle 9.8.2, KSP2 2.3.11, JDK 25 |
| UI | Jetpack Compose for TV (`androidx.tv:tv-material` 1.1.0), Compose BOM 2026.08.00 |
| Media | **libmpv** (FFmpeg) · **ExoPlayer/Media3** 1.11.1 |
| Database | Room 2.8.5 + Paging 3.5.1 + FTS4 |
| DI · Network · Images | Koin 4.2.2 · OkHttp 5 · Coil 3.6.0 |

`minSdk 26`, `targetSdk 36`, `compileSdk 37`, `applicationId com.yai.iptv.player.tv`.

> **Build note:** there is no `kotlin-android` plugin. AGP 9 ships its own Kotlin, but the Compose
> compiler plugin pulls the Kotlin Gradle plugin up to the `kotlin` version in
> `gradle/libs.versions.toml` — that is what compiles the app, so keep the Compose compiler plugin on
> exactly that version. KSP versions independently and does not have to match it.

## ⚙️ How it works

- **Parsing & sync** — M3U is line-streamed and Xtream JSON is read with `JsonReader`, so a huge
  provider payload is never fully buffered. Refresh is chunked, cancellable and reports progress.
- **Storage** — a 27-entity Room schema with FTS4 search; Paging 3 with a bounded size keeps memory
  flat across 50k-item lists.
- **EPG** — XMLTV is stream-parsed (gzip-aware) into a rolling window and pruned.
- **Player** — two engines behind one `PlaybackEngine` interface, with a four-rung fallback ladder.
  Live promotes the running preview straight to full screen with no reload.
- **DI** — Koin modules.

### Project layout

This repository is the **Android TV app**. Everything underneath it — database, sync, parsers, EPG,
backup, settings storage, the playback engines and every translated string — is a separate core
library (`clients/owntv-core`), shared with the **YAiPlayer Mobile** app for phones
and tablets.

```
clients/android-tv/  (this app)
├── player/      the TV player HUD, surfaces and mini-player
├── ui/          theme + reusable components
├── features/    setup, shell, live, movies, series, search, downloads, epg, profiles, settings
└── di/          Koin modules

clients/owntv-core/  (shared core library)
├── core/        database, network, parsers, Stalker, repository, sync, strings
└── player-core/ libmpv + ExoPlayer engines, fallback ladder, watchdogs, diagnostics

clients/android-mobile/  (the phone and tablet app on the same core)
```

**Related modules**

- 📱 **YAiPlayer Mobile** (`clients/android-mobile`) — the phone and tablet app, built on the same core.
- 🧩 **YAiPlayer Core** (`clients/owntv-core`) — the shared engine both apps run on.

## 📚 Docs (`extras/`)

- 📖 **[User Guide](extras/USER_GUIDE.md)** — every feature and where to find it.
- 📄 **[Master Product Brief](extras/OwnTV_Master_Product_Brief.md)** — the full as-built feature and
  architecture reference.

## 📥 Installing (Fire TV / Android TV)

Grab the signed APK (`YAiPlayer-TV.apk`) from the [**latest release**](https://github.com/eai-apps/YAiPlayer/releases/latest).

- **Android TV / Google TV / Fire TV** — sideload with *Send files to TV*, a USB drive, Downloader, or `adb install YAiPlayer-TV.apk`.

## 🛠️ Building & running

> Only if you want to build from source — otherwise just
> **[install the APK](#-installing-fire-tv--android-tv)**.

1. **Get the code** — `git clone https://github.com/eai-apps/YAiPlayer.git`
2. **Open it** in [Android Studio](https://developer.android.com/studio) and let Gradle sync.
3. **Pick the build variant** — `standard` for real devices and arm emulators, `x86_64` for x86_64
   emulators. This matters: the native player only loads on a matching ABI.
4. **Run** ▶. Minimum **Android 8.0 / API 26**.

Command line: `./gradlew assembleDebug` (`gradlew.bat` on Windows). The APK lands in
`app/build/outputs/apk/`.

**Tested on** a real TCL Google TV and the Android Studio emulator (Android TV and Google TV images).

## 🤝 Contributing

Contributions, bug reports and ideas are welcome — open an issue or a pull request. Please keep the
project's player-only, bring-your-own-source positioning and match the existing code style.

## 🙏 Credits

<img src="extras/partner-logos/tmdb_logo.svg" alt="TMDB" width="200">

Movie & series metadata and trailers are provided by [TMDB](https://www.themoviedb.org/).
**This product uses the TMDB API but is not endorsed or certified by TMDB.**

<img src="extras/partner-logos/opensubtitles_logo.svg" alt="OpenSubtitles" width="200">

Subtitle search & download is powered by [OpenSubtitles.com](https://www.opensubtitles.com/).
**This product uses the OpenSubtitles API but is not endorsed or certified by OpenSubtitles.** You sign
in with your own OpenSubtitles account and are subject to their terms and download quotas.

### 🎨 Brand

The lowercase **owntv** wordmark was designed for the original project by @m3th0d93.

### ▶️ Playback engines

- **[mpv](https://mpv.io/)** (via [libmpv](https://github.com/mpv-android/mpv-android), built on
  [FFmpeg](https://ffmpeg.org/)) — the default engine, for the widest IPTV/codec, audio and HDR support.
- **[Media3 / ExoPlayer](https://github.com/androidx/media)** — the fast-start engine for Live TV
  preview and HLS.

Which of the two starts a stream is yours to set, separately for Live TV and for Movies & Series
(Settings → Player): either order, or one engine only with the automatic handover turned off.

### 🧩 Built with

[Jetpack Compose for TV](https://developer.android.com/jetpack/compose) ·
[Room](https://developer.android.com/jetpack/androidx/releases/room) ·
[Koin](https://insert-koin.io/) ·
[OkHttp](https://square.github.io/okhttp/) ·
[Coil](https://coil-kt.github.io/coil/) ·
[ZXing](https://github.com/zxing/zxing) — and the wider Kotlin / AndroidX open-source ecosystem.
Thank you to all their maintainers. See each project for its own license.

## ⚖️ Legal

YAiPlayer is a media **player** only. It ships with no channels, playlists, subscriptions, or content, and
does not endorse or facilitate access to unauthorized streams. Users are solely responsible for the
sources they add and for complying with the laws and rights that apply to them.

## 📄 License

Released under the **GNU General Public License v3.0 (GPLv3)** — see [LICENSE](LICENSE).

YAiPlayer основан на открытом проекте OwnTV (https://github.com/ahXN00/OwnTV), лицензия GPL-3.0.

In short: you're free to use, study, modify, and redistribute YAiPlayer, including commercially — but any
redistributed version (including forks and commercial products built on it) must also be licensed under
GPLv3 and its source made available.

---

<sub>YAiPlayer is an open-source, player-only project, built with the help of AI.</sub>
